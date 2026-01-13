package org.fastpinpon.simulation.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.fastpinpon.simulation.api.ApiClient;
import org.fastpinpon.simulation.api.ApiClient.UnitInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core simulation engine that tracks vehicles in transit and updates their positions.
 * 
 * Responsibilities:
 * - Track units with status "under_way" OR "available" (return trips)
 * - Move them along their stored routes
 * - Update GPS position via API
 * - Transition to "on_site" (if under_way) or "available_hidden" (if available) on arrival
 * - Complete interventions 30s after all responding units arrive
 */
public final class SimulationEngine {
    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    
    // Status Constants
    private static final String STATUS_UNDER_WAY = "under_way";
    private static final String STATUS_ON_SITE = "on_site";
    private static final String STATUS_AVAILABLE = "available";
    private static final String STATUS_AVAILABLE_HIDDEN = "available_hidden";

    private final ApiClient api;
    private final boolean updatingEnabled;
    private final double movementSpeedMultiplier;
    private final Map<String, VehicleState> vehicleStates = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    // Track units that are moving while "available" (Returning to station)
    // Used to distinguish arrival behavior (Hidden vs On Site)
    private final Set<String> movingAvailableUnits = ConcurrentHashMap.newKeySet();

    // Track units for which a repair request has been triggered to avoid duplicates
    private final Set<String> repairRequests = ConcurrentHashMap.newKeySet();


    private long lastTickTime = System.currentTimeMillis();
    private long lastSyncTime = 0;

    public SimulationEngine(ApiClient api, boolean updatingEnabled, double movementSpeedMultiplier) {
        this.api = api;
        this.updatingEnabled = updatingEnabled;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        log.info("Simulation engine initialized (updatingEnabled={}, speedMultiplier={})", updatingEnabled, movementSpeedMultiplier);
    }

    public boolean isUpdatingEnabled() {
        return updatingEnabled;
    }

    /**
     * Main simulation tick - called periodically.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        double deltaSeconds = (now - lastTickTime) / 1000.0;
        lastTickTime = now;

        tick(deltaSeconds);
    }

    /**
     * Simulation tick with explicit delta time.
     * @param deltaSeconds elapsed time since last tick in seconds
     */
    public void tick(double deltaSeconds) {
        try {
            // 1. Sync tracked vehicles with current API state (throttled to 10s)
            // This prevents race conditions where the Simulator reads 'stale' status from API 
            // before the Bridge Emitter/Receiver loop has propagated the new status.
            long now = System.currentTimeMillis();
            if (now - lastTickTime > 10000) { // Using lastTickTime as sync timer base here is confusing, better use separate timer
               // Actually lastTickTime is used for delta calculation.
            }
            // Let's use a dedicated timer
            if (now - lastSyncTime > 10000) {
                 syncVehicles();
                 lastSyncTime = now;
            }

            // 2. Update progress for all tracked vehicles
            updateVehicleProgress(deltaSeconds);

            // 3. Check for intervention completions (probabilistic)
            checkInterventionCompletions(deltaSeconds);

        } catch (Exception e) {
            log.warn("Simulation tick error: {}", e.getMessage());
        }
    }

    /**
     * Sync tracked vehicles with API.
     * Tracks both 'under_way' (intervention) and 'available' (return to station) units.
     */
    private void syncVehicles() {
        List<ApiClient.UnitInfo> units = api.loadUnits();
        
        Map<String, String> latestStatusMap = new HashMap<>();
        Set<String> currentTrackedIds = new HashSet<>();
        long underWayCount = 0;
        long availableCount = 0;

        for (UnitInfo unit : units) {
            latestStatusMap.put(unit.id, unit.status);
            boolean isUnderWay = STATUS_UNDER_WAY.equals(unit.status);
            boolean isAvailable = STATUS_AVAILABLE.equals(unit.status);

            if (isUnderWay || isAvailable) {
                currentTrackedIds.add(unit.id);
                if (isUnderWay) underWayCount++;
                if (isAvailable) {
                    availableCount++;
                    movingAvailableUnits.add(unit.id);
                } else {
                    movingAvailableUnits.remove(unit.id);
                }
                
                checkAndReloadStaleState(unit, isAvailable, isUnderWay);

                if (!vehicleStates.containsKey(unit.id)) {
                    tryAddVehicle(unit);
                }
            }
        }

        log.debug("Vehicle sync: {} under_way, {} available (moving), {} total tracked",
                underWayCount, availableCount, vehicleStates.size());

        cleanupInactiveVehicles(currentTrackedIds, latestStatusMap);
    }

    private void checkAndReloadStaleState(UnitInfo unit, boolean isAvailable, boolean isUnderWay) {
        VehicleState existing = vehicleStates.get(unit.id);
        if (existing != null && isStateStale(existing, isAvailable, isUnderWay)) {
            log.info("State stale for unit {} (status: {}, arrived: {}), reloading route", 
                unit.id, unit.status, existing.hasArrived());
            vehicleStates.remove(unit.id);
        }
    }

    private boolean isStateStale(VehicleState state, boolean isAvailable, boolean isUnderWay) {
        return state.hasArrived() || 
               (isAvailable && state.getInterventionId() != null) || 
               (isUnderWay && state.getInterventionId() == null);
    }

    private void cleanupInactiveVehicles(Set<String> currentTrackedIds, Map<String, String> latestStatusMap) {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, VehicleState> entry : vehicleStates.entrySet()) {
            String unitId = entry.getKey();
            if (!currentTrackedIds.contains(unitId)) {
                VehicleState state = entry.getValue();
                String currentStatus = latestStatusMap.get(unitId);
                
                if (state.hasArrived() && STATUS_ON_SITE.equals(currentStatus) && state.getInterventionId() != null) {
                    continue;
                }
                
                toRemove.add(unitId);
                log.info("No longer tracking vehicle {} (status: {}, arrived: {})", 
                    unitId, currentStatus, state.hasArrived());
            }
        }
        
        toRemove.forEach(id -> {
            vehicleStates.remove(id);
            movingAvailableUnits.remove(id);
            repairRequests.remove(id);
        });
    }

    /**
     * Try to add a new vehicle to tracking by fetching its route.
     */
    private void tryAddVehicle(UnitInfo unit) {
        try {
            ApiClient.UnitRouteInfo route = api.getUnitRoute(unit.id);
            if (route == null) {
                log.debug("No route found for unit {}", unit.id);
                maybeTriggerRepair(unit);
                return;
            }

            // Clear any pending repair flag once a route is present again
            repairRequests.remove(unit.id);

            double initialLat = 0;
            if (route.currentLat != null) {
                initialLat = route.currentLat;
            } else if (unit.latitude != null) {
                initialLat = unit.latitude;
            }

            double initialLon = 0;
            if (route.currentLon != null) {
                initialLon = route.currentLon;
            } else if (unit.longitude != null) {
                initialLon = unit.longitude;
            }

            VehicleState state = new VehicleState(new VehicleState.VehicleConfig(
                    unit.id,
                    route.interventionId,
                    null,
                    route.estimatedDurationSeconds,
                    route.routeLengthMeters,
                    route.severity,
                    new VehicleState.InitialPosition(initialLat, initialLon, route.progressPercent),
                    route.autoSimulated != null ? route.autoSimulated : Boolean.TRUE
            ));

            vehicleStates.put(unit.id, state);
            
            String moveType = movingAvailableUnits.contains(unit.id) ? "Return to Station" : "Intervention";
            log.info("Started tracking vehicle {} ({}) (duration={}s)",
                    unit.id, moveType, route.estimatedDurationSeconds);

        } catch (Exception e) {
            log.warn("Failed to add vehicle {}: {}", unit.id, e.getMessage());
        }
    }

    /**
     * Update progress for all tracked vehicles based on elapsed time.
     */
    private void updateVehicleProgress(double deltaSeconds) {
        for (VehicleState state : vehicleStates.values()) {
            if (state.hasArrived()) {
                continue; // Already at destination
            }

            try {
                // Calculate new progress based on elapsed time and estimated duration
                double increment = state.calculateProgressIncrement(deltaSeconds * movementSpeedMultiplier);
                double newProgress = Math.min(100.0, state.getProgressPercent() + increment);

                // Update progress in API and get interpolated position
                ApiClient.ProgressUpdateResult result = api.updateRouteProgress(state.getUnitId(), newProgress);
                
                if (result != null) {
                    state.setProgressPercent(result.progressPercent);
                    state.setCurrentLat(result.currentLat);
                    state.setCurrentLon(result.currentLon);

                    // Update unit location in API only if updating is enabled
                    if (updatingEnabled) {
                        api.updateUnitLocation(state.getUnitId(), result.currentLat, result.currentLon, Instant.now());
                    }

                    // Check if arrived (100% progress)
                    if (result.progressPercent >= 100.0) {
                        handleArrival(state);
                    }
                }

            } catch (Exception e) {
                log.warn("Failed to update vehicle {}: {}", 
                        state.getUnitId(), e.getMessage());
            }
        }
    }

    /**
     * Handle vehicle arrival at destination.
     * Behavior depends on whether unit is Returning (Available) or Responding (Under Way).
     */
    private void handleArrival(VehicleState state) {
        state.setArrivedAt(Instant.now());

        boolean isReturnTrip = movingAvailableUnits.contains(state.getUnitId());
        
        if (isReturnTrip) {
            handleReturnTripArrival(state);
        } else {
            handleInterventionArrival(state);
        }
    }

    /**
     * Handle arrival for "available" units (Return to Station).
     * Sets status to available_hidden.
     */
    private void handleReturnTripArrival(VehicleState state) {
        log.info("Vehicle {} arrived at station (return trip)", state.getUnitId());
        
        if (updatingEnabled) {
            api.updateUnitStatus(state.getUnitId(), STATUS_AVAILABLE_HIDDEN);
        }
        
        // Clean up immediately as there are no completion timers for return trips
        movingAvailableUnits.remove(state.getUnitId());
        vehicleStates.remove(state.getUnitId());
    }

    /**
     * Handle arrival for "under_way" units (Intervention).
     * Sets status to on_site and manages completion timers.
     */
    private void handleInterventionArrival(VehicleState state) {
        log.info("Vehicle {} arrived at intervention site", state.getUnitId());

        if (updatingEnabled) {
            api.updateUnitStatus(state.getUnitId(), STATUS_ON_SITE);
        }

        if (state.getInterventionId() != null && updatingEnabled) {
            api.updateAssignmentStatusByUnit(state.getInterventionId(), state.getUnitId(), "arrived");
        }
    }

    /**
     * Check if any interventions should complete based on random chance, severity, and unit count.
     */
    private void checkInterventionCompletions(double deltaSeconds) {
        Map<String, List<VehicleState>> byIntervention = groupArrivedVehiclesByIntervention();

        for (Map.Entry<String, List<VehicleState>> entry : byIntervention.entrySet()) {
            if (shouldCompleteIntervention(entry.getKey(), entry.getValue(), deltaSeconds)) {
                log.info("Probabilistic completion for intervention {}", entry.getKey());
                completeIntervention(entry.getKey());
            }
        }
    }

    private Map<String, List<VehicleState>> groupArrivedVehiclesByIntervention() {
        Map<String, List<VehicleState>> byIntervention = new HashMap<>();
        for (VehicleState state : vehicleStates.values()) {
            // Only consider auto-simulated interventions for automatic completion.
            // Manual interventions (autoSimulated = false) must be completed manually.
            if (state.hasArrived() && state.getInterventionId() != null && 
                    !movingAvailableUnits.contains(state.getUnitId()) && state.isAutoSimulated()) {
                byIntervention.computeIfAbsent(state.getInterventionId(), k -> new ArrayList<>())
                        .add(state);
            }
        }
        return byIntervention;
    }

    private boolean shouldCompleteIntervention(String interventionId, List<VehicleState> arrivedUnits, double deltaSeconds) {
        if (arrivedUnits.isEmpty()) return false;

        int severity = arrivedUnits.stream()
            .map(VehicleState::getSeverity)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(3);
        
        int unitCount = arrivedUnits.size();
        double pTick = calculateCompletionProbability(unitCount, severity, deltaSeconds);
        
        double roll = random.nextDouble();
        log.debug("Intervention {} (severity {}, units {}): roll {:.4f} vs threshold {:.4f}", 
                interventionId, severity, unitCount, roll, pTick);

        return roll < pTick;
    }

    private double calculateCompletionProbability(int unitCount, int severity, double deltaSeconds) {
        double baseProbPerc = 2.3;
        double prob1s = baseProbPerc + (unitCount - 1) * 2.2 - (severity - 3) * 2.2;
        
        prob1s = Math.max(0.1, Math.min(99.0, prob1s));
        double p1s = prob1s / 100.0;
        return 1.0 - Math.pow(1.0 - p1s, deltaSeconds);
    }

    private void completeIntervention(String interventionId) {
        try {
            log.info("Completing intervention {}", interventionId);
            api.updateInterventionStatus(interventionId, "completed");

            vehicleStates.entrySet().removeIf(e -> interventionId.equals(e.getValue().getInterventionId()));
            
        } catch (Exception e) {
            log.warn("Failed to complete intervention {}: {}", 
                    interventionId, e.getMessage());
        }
    }

    /**
     * Trigger a single repair request for a unit that is missing a route.
     */
    private void maybeTriggerRepair(UnitInfo unit) {
        boolean shouldRepair = STATUS_UNDER_WAY.equals(unit.status) || STATUS_AVAILABLE.equals(unit.status);
        if (!shouldRepair) {
            return;
        }

        if (repairRequests.add(unit.id)) {
            log.info("Triggering route repair for unit {}", unit.id);
            api.triggerRouteRepair(unit.id);
        }
    }

    /**
     * Get a snapshot of all tracked vehicles for the HTTP endpoint.
     */
    public List<VehicleSnapshot> snapshotVehicles() {
        List<VehicleSnapshot> snapshots = new ArrayList<>();
        for (VehicleState state : vehicleStates.values()) {
            String snapshotStatus;
            
            boolean isReturnTrip = movingAvailableUnits.contains(state.getUnitId());
            
            if (state.hasArrived()) {
                snapshotStatus = isReturnTrip ? STATUS_AVAILABLE_HIDDEN : STATUS_ON_SITE;
            } else {
                snapshotStatus = isReturnTrip ? STATUS_AVAILABLE : STATUS_UNDER_WAY;
            }

            snapshots.add(new VehicleSnapshot(
                    state.getUnitId(),
                    state.getCurrentLat(),
                    state.getCurrentLon(),
                    state.getProgressPercent(),
                    snapshotStatus,
                    state.getInterventionId(),
                    0 // Heading placeholder
            ));
        }
        return snapshots;
    }

    /**
     * Snapshot of a vehicle's current state for API responses.
     */
    public static final class VehicleSnapshot {
        @JsonProperty("unit_id")
        public final String unitId;
        
        @JsonProperty("latitude")
        public final double latitude;
        
        @JsonProperty("longitude")
        public final double longitude;
        
        @JsonProperty("progress_percent")
        public final double progressPercent;
        
        @JsonProperty("status")
        public final String status;
        
        @JsonProperty("intervention_id")
        public final String interventionId;
        
        @JsonProperty("heading")
        public final int heading;

        public VehicleSnapshot(String unitId, double latitude, double longitude, 
                               double progressPercent, String status, String interventionId, int heading) {
            this.unitId = unitId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.progressPercent = progressPercent;
            this.status = status;
            this.interventionId = interventionId;
            this.heading = heading;
        }
    }
}
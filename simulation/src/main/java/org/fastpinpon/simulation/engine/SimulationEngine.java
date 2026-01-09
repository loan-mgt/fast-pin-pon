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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger LOG = Logger.getLogger(SimulationEngine.class.getName());
    
    // Status Constants
    private static final String STATUS_UNDER_WAY = "under_way";
    private static final String STATUS_ON_SITE = "on_site";
    private static final String STATUS_AVAILABLE = "available";
    private static final String STATUS_AVAILABLE_HIDDEN = "available_hidden";
    
    private static final long COMPLETION_DELAY_MS = 30_000; // 30 seconds

    private final ApiClient api;
    private final String apiBaseUrl;
    private final boolean updatingEnabled;
    private final Map<String, VehicleState> vehicleStates = new ConcurrentHashMap<>();
    
    // Track units that are moving while "available" (Returning to station)
    // Used to distinguish arrival behavior (Hidden vs On Site)
    private final Set<String> movingAvailableUnits = ConcurrentHashMap.newKeySet();

    // Track interventions waiting for completion (interventionId -> first arrived timestamp)
    private final Map<String, Instant> interventionCompletionTimers = new ConcurrentHashMap<>();
    
    private List<ApiClient.UnitInfo> cachedUnits = new ArrayList<>();

    private long lastTickTime = System.currentTimeMillis();

    public SimulationEngine(ApiClient api, String apiBaseUrl, boolean updatingEnabled) {
        this.api = api;
        this.apiBaseUrl = apiBaseUrl;
        this.updatingEnabled = updatingEnabled;
        LOG.info("[ENGINE] SimulationEngine initialized (updatingEnabled=" + updatingEnabled + ")");
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
            // 1. Sync tracked vehicles with current API state
            syncVehicles();

            // 2. Update progress for all tracked vehicles
            updateVehicleProgress(deltaSeconds);

            // 3. Check for intervention completions (30s delay)
            checkInterventionCompletions();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ENGINE] Tick error: {0}", e.getMessage());
        }
    }

    /**
     * Sync tracked vehicles with API.
     * Tracks both 'under_way' (intervention) and 'available' (return to station) units.
     */
    private void syncVehicles() {
        List<ApiClient.UnitInfo> units = api.loadUnits();
        this.cachedUnits = units;
        
        Set<String> currentTrackedIds = new HashSet<>();
        long underWayCount = 0;
        long availableCount = 0;

        for (UnitInfo unit : units) {
            boolean isUnderWay = STATUS_UNDER_WAY.equals(unit.status);
            boolean isAvailable = STATUS_AVAILABLE.equals(unit.status);

            if (isUnderWay || isAvailable) {
                currentTrackedIds.add(unit.id);
                if (isUnderWay) underWayCount++;
                if (isAvailable) availableCount++;

                // Manage the type of movement
                if (isAvailable) {
                    movingAvailableUnits.add(unit.id);
                } else {
                    // If it changed from available to under_way mid-flight, remove from available set
                    movingAvailableUnits.remove(unit.id);
                }

                // Add new vehicle if not already tracked
                if (!vehicleStates.containsKey(unit.id)) {
                    tryAddVehicle(unit);
                }
            }
        }

        LOG.log(Level.FINE, "[ENGINE] Sync: {0} under_way, {1} available (moving), {2} total tracked",
                new Object[]{underWayCount, availableCount, vehicleStates.size()});

        // Remove vehicles that are no longer moving (stopped externally or finished)
        Set<String> toRemove = new HashSet<>();
        for (String unitId : vehicleStates.keySet()) {
            if (!currentTrackedIds.contains(unitId)) {
                VehicleState state = vehicleStates.get(unitId);
                
                // Only remove arrived vehicles if their intervention is NOT waiting for completion
                if (state.hasArrived()) {
                    // Keep tracking if intervention completion timer is active (only for under_way / intervention units)
                    if (state.getInterventionId() != null && 
                        interventionCompletionTimers.containsKey(state.getInterventionId())) {
                        continue;
                    }
                    toRemove.add(unitId);
                    LOG.log(Level.INFO, "[ENGINE] Removing arrived vehicle {0}", unitId);
                } else {
                    // Unit changed status externally (e.g. cancelled) - stop tracking
                    toRemove.add(unitId);
                    LOG.log(Level.INFO, "[ENGINE] Vehicle {0} no longer moving, removing", unitId);
                }
            }
        }
        
        toRemove.forEach(id -> {
            vehicleStates.remove(id);
            movingAvailableUnits.remove(id);
        });
    }

    /**
     * Try to add a new vehicle to tracking by fetching its route.
     */
    private void tryAddVehicle(UnitInfo unit) {
        try {
            ApiClient.UnitRouteInfo route = api.getUnitRoute(unit.id);
            if (route == null) {
                LOG.log(Level.FINE, "[ENGINE] No route found for unit {0}", unit.id);
                return;
            }

            VehicleState state = new VehicleState(
                    unit.id,
                    route.interventionId,
                    null, // assignmentId will be fetched if needed
                    route.estimatedDurationSeconds,
                    route.routeLengthMeters,
                    route.progressPercent,
                    route.currentLat != null ? route.currentLat : (unit.latitude != null ? unit.latitude : 0),
                    route.currentLon != null ? route.currentLon : (unit.longitude != null ? unit.longitude : 0)
            );

            vehicleStates.put(unit.id, state);
            
            String moveType = movingAvailableUnits.contains(unit.id) ? "Return to Station" : "Intervention";
            LOG.log(Level.INFO, "[ENGINE] Now tracking vehicle {0} ({1}) (duration={2}s)",
                    new Object[]{unit.id, moveType, route.estimatedDurationSeconds});

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ENGINE] Failed to add vehicle {0}: {1}", new Object[]{unit.id, e.getMessage()});
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
                double increment = state.calculateProgressIncrement(deltaSeconds);
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
                LOG.log(Level.WARNING, "[ENGINE] Failed to update vehicle {0}: {1}", 
                        new Object[]{state.getUnitId(), e.getMessage()});
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
        LOG.log(Level.INFO, "[ENGINE] Vehicle {0} arrived at station (Return Trip)", state.getUnitId());
        
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
        LOG.log(Level.INFO, "[ENGINE] Vehicle {0} arrived at intervention site", state.getUnitId());

        if (updatingEnabled) {
            api.updateUnitStatus(state.getUnitId(), STATUS_ON_SITE);
        }

        // Update assignment status to arrived (if we have intervention ID)
        if (state.getInterventionId() != null) {
            if (updatingEnabled) {
                api.updateAssignmentStatusByUnit(state.getInterventionId(), state.getUnitId(), "arrived");
            }
            
            // Start or update completion timer for this intervention
            interventionCompletionTimers.putIfAbsent(state.getInterventionId(), Instant.now());
        }
    }

    /**
     * Check if any interventions are ready for completion (30s after all units arrived).
     */
    private void checkInterventionCompletions() {
        if (interventionCompletionTimers.isEmpty()) {
            return;
        }

        // Group arrived vehicles by intervention (exclude return trips)
        Map<String, List<VehicleState>> byIntervention = new HashMap<>();
        for (VehicleState state : vehicleStates.values()) {
            if (state.hasArrived() && state.getInterventionId() != null && !movingAvailableUnits.contains(state.getUnitId())) {
                byIntervention.computeIfAbsent(state.getInterventionId(), k -> new ArrayList<>())
                        .add(state);
            }
        }

        Set<String> toComplete = new HashSet<>();
        Instant now = Instant.now();

        for (Map.Entry<String, Instant> entry : interventionCompletionTimers.entrySet()) {
            String interventionId = entry.getKey();
            Instant timerStart = entry.getValue();

            List<VehicleState> arrivedForIntervention = byIntervention.getOrDefault(interventionId, List.of());
            
            // Check if there are still vehicles in transit for this intervention
            // (Exclude vehicles that are marked as 'available' return trips)
            boolean hasVehiclesInTransit = vehicleStates.values().stream()
                    .filter(s -> !movingAvailableUnits.contains(s.getUnitId())) // Only count under_way units
                    .anyMatch(s -> interventionId.equals(s.getInterventionId()) && !s.hasArrived());

            if (hasVehiclesInTransit) {
                interventionCompletionTimers.put(interventionId, now); // Reset timer
                continue;
            }

            long elapsedMs = now.toEpochMilli() - timerStart.toEpochMilli();
            if (elapsedMs >= COMPLETION_DELAY_MS && !arrivedForIntervention.isEmpty()) {
                toComplete.add(interventionId);
            }
        }

        // Complete interventions
        for (String interventionId : toComplete) {
            completeIntervention(interventionId);
        }
    }

    private void completeIntervention(String interventionId) {
        try {
            LOG.log(Level.INFO, "[ENGINE] Completing intervention {0} after 30s delay", interventionId);
            if (updatingEnabled) {
                api.updateInterventionStatus(interventionId, "completed");
            }
            interventionCompletionTimers.remove(interventionId);
            
            // Remove all vehicles for this intervention from tracking
            vehicleStates.entrySet().removeIf(e -> interventionId.equals(e.getValue().getInterventionId()));
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ENGINE] Failed to complete intervention {0}: {1}", 
                    new Object[]{interventionId, e.getMessage()});
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
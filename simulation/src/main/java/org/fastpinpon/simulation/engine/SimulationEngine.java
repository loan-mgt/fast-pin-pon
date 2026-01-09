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
    
    // private static final long COMPLETION_DELAY_MS = 30_000; // Removed in favor of probabilistic completion

    private final ApiClient api;
    private final String apiBaseUrl;
    private final boolean updatingEnabled;
    private final double movementSpeedMultiplier;
    private final Map<String, VehicleState> vehicleStates = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    // Track units that are moving while "available" (Returning to station)
    // Used to distinguish arrival behavior (Hidden vs On Site)
    private final Set<String> movingAvailableUnits = ConcurrentHashMap.newKeySet();

    // Track units for which a repair request has been triggered to avoid duplicates
    private final Set<String> repairRequests = ConcurrentHashMap.newKeySet();

    private List<ApiClient.UnitInfo> cachedUnits = new ArrayList<>();

    private long lastTickTime = System.currentTimeMillis();
    private long lastSyncTime = 0;

    public SimulationEngine(ApiClient api, String apiBaseUrl, boolean updatingEnabled, double movementSpeedMultiplier) {
        this.api = api;
        this.apiBaseUrl = apiBaseUrl;
        this.updatingEnabled = updatingEnabled;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        LOG.info("[ENGINE] SimulationEngine initialized (updatingEnabled=" + updatingEnabled + ", speedMultiplier=" + movementSpeedMultiplier + ")");
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
                if (isAvailable) availableCount++;

                // Manage the type of movement
                if (isAvailable) {
                    movingAvailableUnits.add(unit.id);
                } else {
                    // If it changed from available to under_way mid-flight, remove from available set
                    movingAvailableUnits.remove(unit.id);
                }
                
                // Check if existing state is stale (e.g. unit was settled but is now moving again,
                // or switched context from intervention to return trip)
                if (vehicleStates.containsKey(unit.id)) {
                    VehicleState existing = vehicleStates.get(unit.id);
                    boolean isStale = false;
                    
                    // If state counts as "Arrived" but unit is reported as moving status (under_way/available),
                    // it means a new route is needed.
                    if (existing.hasArrived()) {
                        isStale = true;
                    }
                    // If returning (available) but tracking an intervention route
                    else if (isAvailable && existing.getInterventionId() != null) {
                        isStale = true;
                    }
                    // If responding (under_way) but tracking a return route (no intervention id)
                    else if (isUnderWay && existing.getInterventionId() == null) {
                        isStale = true;
                    }
                    
                    if (isStale) {
                        LOG.log(Level.INFO, "[ENGINE] State stale for {0} (Status: {1}, Arrived: {2}), reloading route.", 
                            new Object[]{unit.id, unit.status, existing.hasArrived()});
                        vehicleStates.remove(unit.id);
                    }
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
                String currentStatus = latestStatusMap.get(unitId);
                
                // Only keep tracking if it is ON_SITE (intervention active) and has arrived
                // This allows us to run completion logic
                if (state.hasArrived() && STATUS_ON_SITE.equals(currentStatus) && state.getInterventionId() != null) {
                    continue;
                }
                
                // Otherwise remove (arrived return trip, or cancelled externally)
                toRemove.add(unitId);
                if (state.hasArrived()) {
                    LOG.log(Level.INFO, "[ENGINE] Removing arrived vehicle {0} (Status: {1})", 
                        new Object[]{unitId, currentStatus});
                } else {
                    LOG.log(Level.INFO, "[ENGINE] Vehicle {0} no longer moving (Status: {1}), removing", 
                        new Object[]{unitId, currentStatus});
                }
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
                LOG.log(Level.FINE, "[ENGINE] No route found for unit {0}", unit.id);
                maybeTriggerRepair(unit);
                return;
            }

            // Clear any pending repair flag once a route is present again
            repairRequests.remove(unit.id);

            VehicleState state = new VehicleState(
                    unit.id,
                    route.interventionId,
                    null, // assignmentId will be fetched if needed
                    route.estimatedDurationSeconds,
                    route.routeLengthMeters,
                    route.progressPercent,
                    route.currentLat != null ? route.currentLat : (unit.latitude != null ? unit.latitude : 0),
                    route.currentLon != null ? route.currentLon : (unit.longitude != null ? unit.longitude : 0),
                    route.severity
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
        }
    }

    /**
     * Check if any interventions should complete based on random chance, severity, and unit count.
     */
    private void checkInterventionCompletions(double deltaSeconds) {
        // Group arrived vehicles by intervention (exclude return trips)
        Map<String, List<VehicleState>> byIntervention = new HashMap<>();
        for (VehicleState state : vehicleStates.values()) {
            if (state.hasArrived() && state.getInterventionId() != null && !movingAvailableUnits.contains(state.getUnitId())) {
                byIntervention.computeIfAbsent(state.getInterventionId(), k -> new ArrayList<>())
                        .add(state);
            }
        }

        Set<String> toComplete = new HashSet<>();

        for (Map.Entry<String, List<VehicleState>> entry : byIntervention.entrySet()) {
            String interventionId = entry.getKey();
            List<VehicleState> arrivedUnits = entry.getValue();
            
            if (arrivedUnits.isEmpty()) {
                continue;
            }

            // Logic for completion
            // Use severity from one of the units (they should all have same intervention -> same severity)
            // If severity is missing, default to 3 (medium)
            int severity = arrivedUnits.stream()
                .map(VehicleState::getSeverity)
                .filter(s -> s != null)
                .findFirst()
                .orElse(3);
            
            int unitCount = arrivedUnits.size();

            // Metric: High metric = Harder to solve (Low Probability)
            // Wait, logic derived was: High Metric = Higher Chance?
            // Re-derivation:
            // Sev 3, Unit 1 => P_1s = 0.023.
            // Target P_1s increases with Units, Decreases with Severity.
            
            // Let's use direct Probability Score:
            // Base Score = 2.3 (Percent)
            // + (Units - 1) * 3.0  => More units increase chance drastically
            // - (Severity - 3) * 1.5 => Higher severity reduces chance
            
            // Re-calib: Sev 3, Unit 1 -> 2.3%
            // Sev 3, Unit 4 -> 2.3 + 9 = 11.3% -> Median ~6s.
            // Sev 5, Unit 1 -> 2.3 - 3 = -0.7% -> Impossible (good).
            
            double baseProbPerc = 2.3;
            double prob1s = baseProbPerc 
                          + (unitCount - 1) * 2.2 
                          - (severity - 3) * 2.2;
            
            // Clamp prob1s
            if (prob1s < 0.1) prob1s = 0.1; // Minimum chance to avoid infinite loops, or 0? 
            if (prob1s > 99.0) prob1s = 99.0;
            
            // Convert to probability per tick
            double p1s = prob1s / 100.0;
            double pTick = 1.0 - Math.pow(1.0 - p1s, deltaSeconds);
            
            // Threshold for random roll [0, 100]
            // We want Event if Roll < pTick * 100
            // OR Roll > Threshold where Threshold = 100 * (1 - pTick)
            
            double threshold = 100.0 * (1.0 - pTick);
            double roll = random.nextDouble() * 100.0;
            
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("[ENGINE] Intv %s (Sev %d, Units %d): Roll %.2f vs Threshold %.2f (P_tick %.4f)", 
                    interventionId, severity, unitCount, roll, threshold, pTick));
            }

            if (roll > threshold) {
                LOG.info(String.format("[ENGINE] Probabilistic completion for %s: Roll %.2f > Threshold %.2f", interventionId, roll, threshold));
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
            LOG.log(Level.INFO, "[ENGINE] Completing intervention {0}", interventionId);
            api.updateInterventionStatus(interventionId, "completed");

            // Remove all vehicles for this intervention from tracking
            vehicleStates.entrySet().removeIf(e -> interventionId.equals(e.getValue().getInterventionId()));
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ENGINE] Failed to complete intervention {0}: {1}", 
                    new Object[]{interventionId, e.getMessage()});
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
            LOG.log(Level.INFO, "[ENGINE] Triggering route repair for unit {0}", unit.id);
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
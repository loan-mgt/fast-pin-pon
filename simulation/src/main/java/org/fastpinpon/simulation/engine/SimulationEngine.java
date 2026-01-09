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
 * - Track units with status "under_way"
 * - Move them along their stored routes using estimated_duration_seconds
 * - Update GPS position via API
 * - Transition to "on_site" on arrival
 * - Complete interventions 30s after all units arrive
 */
public final class SimulationEngine {
    private static final Logger LOG = Logger.getLogger(SimulationEngine.class.getName());
    private static final String STATUS_UNDER_WAY = "under_way";
    private static final String STATUS_ON_SITE = "on_site";
    private static final long COMPLETION_DELAY_MS = 30_000; // 30 seconds

    private final ApiClient api;
    private final String apiBaseUrl;
    private final Map<String, VehicleState> vehicleStates = new ConcurrentHashMap<>();
    
    // Track interventions waiting for completion (interventionId -> first arrived timestamp)
    private final Map<String, Instant> interventionCompletionTimers = new ConcurrentHashMap<>();

    private long lastTickTime = System.currentTimeMillis();

    public SimulationEngine(ApiClient api, String apiBaseUrl) {
        this.api = api;
        this.apiBaseUrl = apiBaseUrl;
        LOG.info("[ENGINE] SimulationEngine initialized");
    }

    /**
     * Main simulation tick - called periodically.
     * Uses wall-clock delta to calculate progress independent of tick rate.
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
     * Sync tracked vehicles with API - add new under_way units, remove finished ones.
     */
    private void syncVehicles() {
        List<UnitInfo> units = api.loadUnits();
        
        // Count units by status for logging
        long underWayCount = units.stream().filter(u -> STATUS_UNDER_WAY.equals(u.status)).count();
        long onSiteCount = units.stream().filter(u -> STATUS_ON_SITE.equals(u.status)).count();
        LOG.log(Level.FINE, "[ENGINE] Syncing {0} units ({1} under_way, {2} on_site, {3} tracked)",
                new Object[]{units.size(), underWayCount, onSiteCount, vehicleStates.size()});
        
        Set<String> currentUnderWay = new HashSet<>();

        for (UnitInfo unit : units) {
            if (STATUS_UNDER_WAY.equals(unit.status)) {
                currentUnderWay.add(unit.id);

                // Add new vehicle if not already tracked
                if (!vehicleStates.containsKey(unit.id)) {
                    tryAddVehicle(unit);
                }
            }
        }

        // Remove vehicles that are no longer under_way
        Set<String> toRemove = new HashSet<>();
        for (String unitId : vehicleStates.keySet()) {
            if (!currentUnderWay.contains(unitId)) {
                VehicleState state = vehicleStates.get(unitId);
                // Only remove if already arrived (handled) or no longer under_way
                if (state.hasArrived()) {
                    toRemove.add(unitId);
                    LOG.log(Level.INFO, "[ENGINE] Removing arrived vehicle {0}", unitId);
                } else if (!currentUnderWay.contains(unitId)) {
                    // Unit changed status externally - stop tracking
                    toRemove.add(unitId);
                    LOG.log(Level.INFO, "[ENGINE] Vehicle {0} no longer under_way, removing", unitId);
                }
            }
        }
        toRemove.forEach(vehicleStates::remove);
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
            LOG.log(Level.INFO, "[ENGINE] Now tracking vehicle {0} (callSign={1}, duration={2}s, length={3}m)",
                    new Object[]{unit.id, unit.callSign, route.estimatedDurationSeconds, route.routeLengthMeters});

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

                    // Update unit location in API
                    api.updateUnitLocation(state.getUnitId(), result.currentLat, result.currentLon, Instant.now());

                    LOG.log(Level.FINE, "[ENGINE] Vehicle {0}: progress={1}%, pos=({2}, {3})",
                            new Object[]{state.getUnitId(), String.format("%.1f", result.progressPercent), 
                                        result.currentLat, result.currentLon});

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
     */
    private void handleArrival(VehicleState state) {
        LOG.log(Level.INFO, "[ENGINE] Vehicle {0} arrived at destination", state.getUnitId());
        
        state.setArrivedAt(Instant.now());

        // Update unit status to on_site
        api.updateUnitStatus(state.getUnitId(), STATUS_ON_SITE);

        // Update assignment status to arrived (if we have intervention ID)
        if (state.getInterventionId() != null) {
            api.updateAssignmentStatusByUnit(state.getInterventionId(), state.getUnitId(), "arrived");
            
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

        // Group arrived vehicles by intervention
        Map<String, List<VehicleState>> byIntervention = new HashMap<>();
        for (VehicleState state : vehicleStates.values()) {
            if (state.hasArrived() && state.getInterventionId() != null) {
                byIntervention.computeIfAbsent(state.getInterventionId(), k -> new ArrayList<>())
                        .add(state);
            }
        }

        // Check each intervention with a completion timer
        Set<String> toComplete = new HashSet<>();
        Instant now = Instant.now();

        for (Map.Entry<String, Instant> entry : interventionCompletionTimers.entrySet()) {
            String interventionId = entry.getKey();
            Instant timerStart = entry.getValue();

            // Check if all tracked vehicles for this intervention have arrived
            List<VehicleState> arrivedForIntervention = byIntervention.getOrDefault(interventionId, List.of());
            
            // Also check if there are still vehicles in transit for this intervention
            boolean hasVehiclesInTransit = vehicleStates.values().stream()
                    .anyMatch(s -> interventionId.equals(s.getInterventionId()) && !s.hasArrived());

            if (hasVehiclesInTransit) {
                // Reset timer - not all vehicles have arrived yet
                interventionCompletionTimers.put(interventionId, now);
                continue;
            }

            // All vehicles arrived - check if 30s have passed
            long elapsedMs = now.toEpochMilli() - timerStart.toEpochMilli();
            if (elapsedMs >= COMPLETION_DELAY_MS && !arrivedForIntervention.isEmpty()) {
                toComplete.add(interventionId);
            }
        }

        // Complete interventions
        for (String interventionId : toComplete) {
            try {
                LOG.log(Level.INFO, "[ENGINE] Completing intervention {0} after 30s delay", interventionId);
                api.updateInterventionStatus(interventionId, "completed");
                interventionCompletionTimers.remove(interventionId);
                
                // Remove all vehicles for this intervention from tracking
                vehicleStates.entrySet().removeIf(e -> interventionId.equals(e.getValue().getInterventionId()));
                
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[ENGINE] Failed to complete intervention {0}: {1}", 
                        new Object[]{interventionId, e.getMessage()});
            }
        }
    }

    /**
     * Get a snapshot of all tracked vehicles for the HTTP endpoint.
     */
    public List<VehicleSnapshot> snapshotVehicles() {
        List<VehicleSnapshot> snapshots = new ArrayList<>();
        for (VehicleState state : vehicleStates.values()) {
            snapshots.add(new VehicleSnapshot(
                    state.getUnitId(),
                    state.getCurrentLat(),
                    state.getCurrentLon(),
                    state.getProgressPercent(),
                    state.hasArrived() ? STATUS_ON_SITE : STATUS_UNDER_WAY,
                    state.getInterventionId(),
                    calculateHeading(state)
            ));
        }
        return snapshots;
    }

    /**
     * Calculate approximate heading based on movement (placeholder - would need previous position).
     */
    private int calculateHeading(VehicleState state) {
        // For now, return 0 - a real implementation would track previous position
        return 0;
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

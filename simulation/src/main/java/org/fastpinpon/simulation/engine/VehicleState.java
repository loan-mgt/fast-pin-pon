package org.fastpinpon.simulation.engine;

import java.time.Instant;

/**
 * Tracks the simulation state for a single vehicle in transit.
 */
public final class VehicleState {
    private final String unitId;
    private final String interventionId;
    private final String assignmentId;
    private final double estimatedDurationSeconds;
    private final double routeLengthMeters;
    private final Instant startedAt;
    private final Integer severity;
    private final boolean autoSimulated;

    private double progressPercent;
    private double currentLat;
    private double currentLon;
    private Instant arrivedAt; // null until unit reaches destination

    public static final class InitialPosition {
        public final double lat;
        public final double lon;
        public final double progressPercent;

        public InitialPosition(double lat, double lon, double progressPercent) {
            this.lat = lat;
            this.lon = lon;
            this.progressPercent = progressPercent;
        }
    }

    public static final class VehicleConfig {
        public final String unitId;
        public final String interventionId;
        public final String assignmentId;
        public final double estimatedDurationSeconds;
        public final double routeLengthMeters;
        public final Integer severity;
        public final InitialPosition initialPosition;
        public final boolean autoSimulated;

        @SuppressWarnings("java:S107") // Suppress "too many parameters" - config object pattern is intentional
        public VehicleConfig(String unitId, String interventionId, String assignmentId,
                             double estimatedDurationSeconds, double routeLengthMeters,
                             Integer severity, InitialPosition initialPosition, boolean autoSimulated) {
            this.unitId = unitId;
            this.interventionId = interventionId;
            this.assignmentId = assignmentId;
            this.estimatedDurationSeconds = estimatedDurationSeconds;
            this.routeLengthMeters = routeLengthMeters;
            this.severity = severity;
            this.initialPosition = initialPosition;
            this.autoSimulated = autoSimulated;
        }
    }

    public VehicleState(VehicleConfig config) {
        this.unitId = config.unitId;
        this.interventionId = config.interventionId;
        this.assignmentId = config.assignmentId;
        this.estimatedDurationSeconds = config.estimatedDurationSeconds;
        this.routeLengthMeters = config.routeLengthMeters;
        this.startedAt = Instant.now();
        this.progressPercent = config.initialPosition.progressPercent;
        this.currentLat = config.initialPosition.lat;
        this.currentLon = config.initialPosition.lon;
        this.arrivedAt = null;
        this.severity = config.severity;
        this.autoSimulated = config.autoSimulated;
    }

    public boolean isAutoSimulated() {
        return autoSimulated;
    }

    public Integer getSeverity() {
        return severity;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getInterventionId() {
        return interventionId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public double getEstimatedDurationSeconds() {
        return estimatedDurationSeconds;
    }

    public double getRouteLengthMeters() {
        return routeLengthMeters;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(double progressPercent) {
        this.progressPercent = Math.min(100.0, Math.max(0.0, progressPercent));
    }

    public double getCurrentLat() {
        return currentLat;
    }

    public void setCurrentLat(double currentLat) {
        this.currentLat = currentLat;
    }

    public double getCurrentLon() {
        return currentLon;
    }

    public void setCurrentLon(double currentLon) {
        this.currentLon = currentLon;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(Instant arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public boolean hasArrived() {
        return arrivedAt != null;
    }

    /**
     * Calculate the progress increment for a given delta time.
     * @param deltaSeconds elapsed time in seconds
     * @return progress increment as percentage (0-100)
     */
    public double calculateProgressIncrement(double deltaSeconds) {
        if (estimatedDurationSeconds <= 0) {
            return 100.0; // Instant arrival if no duration
        }
        return (deltaSeconds / estimatedDurationSeconds) * 100.0;
    }
}

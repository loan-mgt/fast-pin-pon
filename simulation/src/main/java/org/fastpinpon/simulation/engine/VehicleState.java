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

    private double progressPercent;
    private double currentLat;
    private double currentLon;
    private Instant arrivedAt; // null until unit reaches destination

    public VehicleState(String unitId, String interventionId, String assignmentId,
                        double estimatedDurationSeconds, double routeLengthMeters,
                        double initialProgressPercent, double initialLat, double initialLon) {
        this.unitId = unitId;
        this.interventionId = interventionId;
        this.assignmentId = assignmentId;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
        this.routeLengthMeters = routeLengthMeters;
        this.startedAt = Instant.now();
        this.progressPercent = initialProgressPercent;
        this.currentLat = initialLat;
        this.currentLon = initialLon;
        this.arrivedAt = null;
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

package org.fastpinpon.engine.domain.model;

import java.util.Objects;

/**
 * Immutable domain model for a dispatch candidate with computed score.
 */
public final class ScoredCandidate implements Comparable<ScoredCandidate> {

    private final String unitId;
    private final String callSign;
    private final String unitTypeCode;
    private final String homeBase;
    private final String status;
    private final double latitude;
    private final double longitude;
    private final double travelTimeSeconds;
    private final double distanceMeters;
    private final int otherUnitsAtBase;
    private final String currentAssignmentId;
    private final String currentInterventionId;
    private final Integer currentInterventionSeverity;
    private final double score;

    private ScoredCandidate(Builder builder) {
        this.unitId = Objects.requireNonNull(builder.unitId, "unitId must not be null");
        this.callSign = Objects.requireNonNull(builder.callSign, "callSign must not be null");
        this.unitTypeCode = Objects.requireNonNull(builder.unitTypeCode, "unitTypeCode must not be null");
        this.homeBase = builder.homeBase;
        this.status = builder.status;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.travelTimeSeconds = builder.travelTimeSeconds;
        this.distanceMeters = builder.distanceMeters;
        this.otherUnitsAtBase = builder.otherUnitsAtBase;
        this.currentAssignmentId = builder.currentAssignmentId;
        this.currentInterventionId = builder.currentInterventionId;
        this.currentInterventionSeverity = builder.currentInterventionSeverity;
        this.score = builder.score;
    }

    // Getters
    public String getUnitId() {
        return unitId;
    }

    public String getCallSign() {
        return callSign;
    }

    public String getUnitTypeCode() {
        return unitTypeCode;
    }

    public String getHomeBase() {
        return homeBase;
    }

    public String getStatus() {
        return status;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getTravelTimeSeconds() {
        return travelTimeSeconds;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public int getOtherUnitsAtBase() {
        return otherUnitsAtBase;
    }

    public String getCurrentAssignmentId() {
        return currentAssignmentId;
    }

    public String getCurrentInterventionId() {
        return currentInterventionId;
    }

    public Integer getCurrentInterventionSeverity() {
        return currentInterventionSeverity;
    }

    public double getScore() {
        return score;
    }

    /**
     * Check if this candidate requires preemption (is currently assigned elsewhere).
     */
    public boolean requiresPreemption() {
        return currentAssignmentId != null && !currentAssignmentId.isEmpty();
    }

    /**
     * Check if this candidate is available.
     */
    public boolean isAvailable() {
        return "available".equals(status);
    }

    @Override
    public int compareTo(ScoredCandidate other) {
        // Lower score is better
        return Double.compare(this.score, other.score);
    }

    @Override
    public String toString() {
        return String.format("ScoredCandidate{callSign='%s', score=%.2f, travelTime=%.1fs, preemption=%s}",
                callSign, score, travelTimeSeconds, requiresPreemption());
    }

    /**
     * Builder for ScoredCandidate.
     */
    public static final class Builder {
        private String unitId;
        private String callSign;
        private String unitTypeCode;
        private String homeBase;
        private String status;
        private double latitude;
        private double longitude;
        private double travelTimeSeconds;
        private double distanceMeters;
        private int otherUnitsAtBase;
        private String currentAssignmentId;
        private String currentInterventionId;
        private Integer currentInterventionSeverity;
        private double score;

        public Builder unitId(String unitId) {
            this.unitId = unitId;
            return this;
        }

        public Builder callSign(String callSign) {
            this.callSign = callSign;
            return this;
        }

        public Builder unitTypeCode(String unitTypeCode) {
            this.unitTypeCode = unitTypeCode;
            return this;
        }

        public Builder homeBase(String homeBase) {
            this.homeBase = homeBase;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder latitude(double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder travelTimeSeconds(double travelTimeSeconds) {
            this.travelTimeSeconds = travelTimeSeconds;
            return this;
        }

        public Builder distanceMeters(double distanceMeters) {
            this.distanceMeters = distanceMeters;
            return this;
        }

        public Builder otherUnitsAtBase(int otherUnitsAtBase) {
            this.otherUnitsAtBase = otherUnitsAtBase;
            return this;
        }

        public Builder currentAssignmentId(String currentAssignmentId) {
            this.currentAssignmentId = currentAssignmentId;
            return this;
        }

        public Builder currentInterventionId(String currentInterventionId) {
            this.currentInterventionId = currentInterventionId;
            return this;
        }

        public Builder currentInterventionSeverity(Integer currentInterventionSeverity) {
            this.currentInterventionSeverity = currentInterventionSeverity;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public ScoredCandidate build() {
            return new ScoredCandidate(this);
        }
    }
}

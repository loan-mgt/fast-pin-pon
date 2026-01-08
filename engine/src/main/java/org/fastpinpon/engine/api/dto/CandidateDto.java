package org.fastpinpon.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for a dispatch candidate unit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CandidateDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("call_sign")
    private String callSign;

    @JsonProperty("unit_type_code")
    private String unitTypeCode;

    @JsonProperty("home_base")
    private String homeBase;

    @JsonProperty("status")
    private String status;

    @JsonProperty("location")
    private LocationDto location;

    @JsonProperty("travel_time_seconds")
    private double travelTimeSeconds;

    @JsonProperty("distance_meters")
    private double distanceMeters;

    @JsonProperty("other_units_at_base")
    private int otherUnitsAtBase;

    @JsonProperty("current_assignment_id")
    private String currentAssignmentId;

    @JsonProperty("current_intervention_id")
    private String currentInterventionId;

    @JsonProperty("current_intervention_severity")
    private Integer currentInterventionSeverity;

    @JsonProperty("current_intervention_priority")
    private Integer currentInterventionPriority;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCallSign() {
        return callSign;
    }

    public void setCallSign(String callSign) {
        this.callSign = callSign;
    }

    public String getUnitTypeCode() {
        return unitTypeCode;
    }

    public void setUnitTypeCode(String unitTypeCode) {
        this.unitTypeCode = unitTypeCode;
    }

    public String getHomeBase() {
        return homeBase;
    }

    public void setHomeBase(String homeBase) {
        this.homeBase = homeBase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocationDto getLocation() {
        return location;
    }

    public void setLocation(LocationDto location) {
        this.location = location;
    }

    public double getTravelTimeSeconds() {
        return travelTimeSeconds;
    }

    public void setTravelTimeSeconds(double travelTimeSeconds) {
        this.travelTimeSeconds = travelTimeSeconds;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public int getOtherUnitsAtBase() {
        return otherUnitsAtBase;
    }

    public void setOtherUnitsAtBase(int otherUnitsAtBase) {
        this.otherUnitsAtBase = otherUnitsAtBase;
    }

    public String getCurrentAssignmentId() {
        return currentAssignmentId;
    }

    public void setCurrentAssignmentId(String currentAssignmentId) {
        this.currentAssignmentId = currentAssignmentId;
    }

    public String getCurrentInterventionId() {
        return currentInterventionId;
    }

    public void setCurrentInterventionId(String currentInterventionId) {
        this.currentInterventionId = currentInterventionId;
    }

    public Integer getCurrentInterventionSeverity() {
        return currentInterventionSeverity;
    }

    public void setCurrentInterventionSeverity(Integer currentInterventionSeverity) {
        this.currentInterventionSeverity = currentInterventionSeverity;
    }

    public Integer getCurrentInterventionPriority() {
        return currentInterventionPriority;
    }

    public void setCurrentInterventionPriority(Integer currentInterventionPriority) {
        this.currentInterventionPriority = currentInterventionPriority;
    }

    /**
     * Check if this candidate is currently assigned to another intervention.
     */
    public boolean isCurrentlyAssigned() {
        return currentAssignmentId != null && !currentAssignmentId.isEmpty();
    }

    /**
     * Check if this candidate is available (not assigned).
     */
    public boolean isAvailable() {
        return "available".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class LocationDto {
        @JsonProperty("latitude")
        private double latitude;

        @JsonProperty("longitude")
        private double longitude;

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
    }
}

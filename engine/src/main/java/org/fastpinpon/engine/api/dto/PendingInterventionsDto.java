package org.fastpinpon.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /v1/dispatch/pending endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PendingInterventionsDto {

    @JsonProperty("interventions")
    private List<PendingInterventionDto> interventions;

    public List<PendingInterventionDto> getInterventions() {
        return interventions;
    }

    public void setInterventions(List<PendingInterventionDto> interventions) {
        this.interventions = interventions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class PendingInterventionDto {
        @JsonProperty("intervention_id")
        private String interventionId;

        @JsonProperty("event_id")
        private String eventId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("priority")
        private int priority;

        @JsonProperty("event_severity")
        private int eventSeverity;

        @JsonProperty("event_type_code")
        private String eventTypeCode;

        @JsonProperty("recommended_unit_types")
        private List<String> recommendedUnitTypes;

        @JsonProperty("location")
        private CandidateDto.LocationDto location;

        @JsonProperty("assigned_units_count")
        private long assignedUnitsCount;

        @JsonProperty("created_at")
        private String createdAt;

        public String getInterventionId() {
            return interventionId;
        }

        public void setInterventionId(String interventionId) {
            this.interventionId = interventionId;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public int getEventSeverity() {
            return eventSeverity;
        }

        public void setEventSeverity(int eventSeverity) {
            this.eventSeverity = eventSeverity;
        }

        public String getEventTypeCode() {
            return eventTypeCode;
        }

        public void setEventTypeCode(String eventTypeCode) {
            this.eventTypeCode = eventTypeCode;
        }

        public List<String> getRecommendedUnitTypes() {
            return recommendedUnitTypes;
        }

        public void setRecommendedUnitTypes(List<String> recommendedUnitTypes) {
            this.recommendedUnitTypes = recommendedUnitTypes;
        }

        public CandidateDto.LocationDto getLocation() {
            return location;
        }

        public void setLocation(CandidateDto.LocationDto location) {
            this.location = location;
        }

        public long getAssignedUnitsCount() {
            return assignedUnitsCount;
        }

        public void setAssignedUnitsCount(long assignedUnitsCount) {
            this.assignedUnitsCount = assignedUnitsCount;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        /**
         * Check if this intervention needs more units assigned.
         */
        public boolean needsMoreUnits() {
            // Use event severity as target number of units
            return assignedUnitsCount < eventSeverity;
        }
    }
}

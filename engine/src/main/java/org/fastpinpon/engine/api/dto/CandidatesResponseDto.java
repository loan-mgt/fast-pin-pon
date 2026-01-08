package org.fastpinpon.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /v1/interventions/{id}/candidates endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CandidatesResponseDto {

    @JsonProperty("intervention_id")
    private String interventionId;

    @JsonProperty("event_severity")
    private int eventSeverity;

    @JsonProperty("recommended_unit_types")
    private List<String> recommendedUnitTypes;

    @JsonProperty("candidates")
    private List<CandidateDto> candidates;

    public String getInterventionId() {
        return interventionId;
    }

    public void setInterventionId(String interventionId) {
        this.interventionId = interventionId;
    }

    public int getEventSeverity() {
        return eventSeverity;
    }

    public void setEventSeverity(int eventSeverity) {
        this.eventSeverity = eventSeverity;
    }

    public List<String> getRecommendedUnitTypes() {
        return recommendedUnitTypes;
    }

    public void setRecommendedUnitTypes(List<String> recommendedUnitTypes) {
        this.recommendedUnitTypes = recommendedUnitTypes;
    }

    public List<CandidateDto> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<CandidateDto> candidates) {
        this.candidates = candidates;
    }
}

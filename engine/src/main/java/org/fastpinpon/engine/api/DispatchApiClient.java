package org.fastpinpon.engine.api;

import org.fastpinpon.engine.api.dto.CandidatesResponseDto;
import org.fastpinpon.engine.api.dto.PendingInterventionsDto;
import org.fastpinpon.engine.api.dto.StaticDataDto;

/**
 * Client interface for dispatch-related API endpoints.
 */
public interface DispatchApiClient {

    /**
     * Get static data for engine initialization.
     * GET /v1/dispatch/static
     */
    StaticDataDto getStaticData();

    /**
     * Get dispatch candidates for an intervention.
     * GET /v1/interventions/{interventionId}/candidates
     */
    CandidatesResponseDto getCandidates(String interventionId);

    /**
     * Get pending interventions awaiting dispatch.
     * GET /v1/dispatch/pending
     */
    PendingInterventionsDto getPendingInterventions();

    /**
     * Assign a unit to an intervention.
     * POST /v1/interventions/{interventionId}/assignments
     *
     * @return assignment ID or null on failure
     */
    String assignUnit(String interventionId, String unitId, String role);

    /**
     * Release a unit from its current assignment (for preemption).
     * DELETE /v1/interventions/{interventionId}/assignments/{unitId}
     * or PATCH to update status to 'released'
     */
    void releaseAssignment(String assignmentId);

    /**
     * Update unit status.
     * PATCH /v1/units/{unitId}/status
     */
    void updateUnitStatus(String unitId, String status);
}

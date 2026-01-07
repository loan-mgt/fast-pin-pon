package org.fastpinpon.engine.domain.service;

import org.fastpinpon.engine.domain.model.ScoredCandidate;

import java.util.List;

/**
 * Service for dispatching units to interventions.
 */
public interface DispatchService {

    /**
     * Dispatch units to a specific intervention.
     *
     * @param interventionId the intervention to dispatch units to
     * @return list of units that were dispatched
     */
    List<ScoredCandidate> dispatchForIntervention(String interventionId);

    /**
     * Run periodic dispatch for all pending interventions.
     * Called by scheduler every N seconds.
     *
     * @return total number of units dispatched
     */
    int periodicDispatch();
}

package org.fastpinpon.engine.domain.service;

import org.fastpinpon.engine.api.dto.CandidateDto;
import org.fastpinpon.engine.domain.model.DispatchConfig;
import org.fastpinpon.engine.domain.model.ScoredCandidate;

/**
 * Service for calculating dispatch scores for candidates.
 */
public interface ScoringService {

    /**
     * Calculate score for a candidate.
     * Lower score = better candidate.
     *
     * @param candidate the candidate to score
     * @param targetSeverity the severity of the target intervention
     * @param config the dispatch configuration
     * @return scored candidate with computed score
     */
    ScoredCandidate score(CandidateDto candidate, int targetSeverity, DispatchConfig config);
}

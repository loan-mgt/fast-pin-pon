package org.fastpinpon.engine.domain.service;

import org.fastpinpon.engine.api.DispatchApiClient;
import org.fastpinpon.engine.api.dto.CandidatesResponseDto;
import org.fastpinpon.engine.api.dto.PendingInterventionsDto;
import org.fastpinpon.engine.cache.StaticDataCache;
import org.fastpinpon.engine.domain.model.DispatchConfig;
import org.fastpinpon.engine.domain.model.ScoredCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

/**
 * Implementation of DispatchService.
 * Handles unit assignment using multi-objective scoring.
 */
public final class DispatchServiceImpl implements DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchServiceImpl.class);

    private final DispatchApiClient apiClient;
    private final StaticDataCache cache;
    private final ScoringService scoringService;

    public DispatchServiceImpl(DispatchApiClient apiClient, StaticDataCache cache, ScoringService scoringService) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.scoringService = Objects.requireNonNull(scoringService, "scoringService must not be null");
    }

    @Override
    public List<ScoredCandidate> dispatchForIntervention(String interventionId) {
        log.info("Dispatching units for intervention: {}", interventionId);

        try {
            // Get candidates from API
            CandidatesResponseDto response = apiClient.getCandidates(interventionId);
            if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
                log.warn("No candidates available for intervention: {}", interventionId);
                return Collections.emptyList();
            }

            int targetSeverity = response.getEventSeverity();
            DispatchConfig config = cache.getConfig();

            // Score all candidates
            List<ScoredCandidate> scoredCandidates = response.getCandidates().stream()
                    .map(c -> scoringService.score(c, targetSeverity, config))
                    .filter(sc -> sc.getScore() < Double.MAX_VALUE) // Filter disqualified
                    .sorted() // Sort by score ascending (best first)
                    .collect(Collectors.toList());

            if (scoredCandidates.isEmpty()) {
                log.warn("No eligible candidates after scoring for intervention: {}", interventionId);
                return Collections.emptyList();
            }

            // Determine how many units to dispatch (based on severity)
            int unitsToDispatch = Math.min(targetSeverity, scoredCandidates.size());
            
            List<ScoredCandidate> dispatched = new ArrayList<>();
            for (int i = 0; i < unitsToDispatch; i++) {
                ScoredCandidate candidate = scoredCandidates.get(i);
                ScoredCandidate result = assignCandidate(interventionId, candidate, i);
                if (result != null) {
                    dispatched.add(result);
                }
            }

            log.info("Dispatched {}/{} units to intervention {}",
                    dispatched.size(), unitsToDispatch, interventionId);

            return dispatched;

        } catch (Exception e) {
            log.error("Error dispatching for intervention: {}", interventionId, e);
            return Collections.emptyList();
        }
    }

    private ScoredCandidate assignCandidate(String interventionId, ScoredCandidate candidate, int position) {
        try {
            // Handle preemption if necessary
            if (candidate.requiresPreemption()) {
                log.info("Preempting {} from intervention {}",
                        candidate.getCallSign(), candidate.getCurrentInterventionId());
                apiClient.releaseAssignment(candidate.getCurrentAssignmentId());
            }

            // Create new assignment
            String role = determineRole(position);
            String assignmentId = apiClient.assignUnit(interventionId, candidate.getUnitId(), role);

            if (assignmentId != null) {
                log.info("Dispatched {} to intervention {} (score={:.2f}, ETA={:.1f}s)",
                        candidate.getCallSign(), interventionId, candidate.getScore(), candidate.getTravelTimeSeconds());
                return candidate;
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch {}: {}", candidate.getCallSign(), e.getMessage());
        }
        return null;
    }

    @Override
    public int periodicDispatch() {
        log.debug("Running periodic dispatch...");

        try {
            PendingInterventionsDto pending = apiClient.getPendingInterventions();
            if (pending == null || pending.getInterventions() == null || pending.getInterventions().isEmpty()) {
                log.debug("No pending interventions");
                return 0;
            }

            int totalDispatched = 0;
            for (PendingInterventionsDto.PendingInterventionDto intervention : pending.getInterventions()) {
                // Only dispatch if intervention needs more units
                if (intervention.needsMoreUnits()) {
                    List<ScoredCandidate> dispatched = dispatchForIntervention(intervention.getInterventionId());
                    totalDispatched += dispatched.size();
                }
            }

            if (totalDispatched > 0) {
                log.info("Periodic dispatch completed: {} units dispatched", totalDispatched);
            }

            return totalDispatched;

        } catch (Exception e) {
            log.error("Error in periodic dispatch", e);
            return 0;
        }
    }

    /**
     * Determine the role for a dispatched unit based on position.
     */
    private String determineRole(int position) {
        if (position == 0) {
            return "lead";
        }
        return "support";
    }
}

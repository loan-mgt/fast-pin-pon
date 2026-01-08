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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of DispatchService.
 * Handles unit assignment using multi-objective scoring.
 */
public final class DispatchServiceImpl implements DispatchService {

    private static final Logger LOG = Logger.getLogger(DispatchServiceImpl.class.getName());

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
        LOG.info(() -> "Dispatching units for intervention: " + interventionId);

        try {
            // Get candidates from API
            CandidatesResponseDto response = apiClient.getCandidates(interventionId);
            if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
                LOG.warning(() -> "No candidates available for intervention: " + interventionId);
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
                LOG.warning(() -> "No eligible candidates after scoring for intervention: " + interventionId);
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

            LOG.info(() -> String.format("Dispatched %d/%d units to intervention %s",
                    dispatched.size(), unitsToDispatch, interventionId));

            return dispatched;

        } catch (Exception e) {
            LOG.log(Level.SEVERE, e, () -> "Error dispatching for intervention: " + interventionId);
            return Collections.emptyList();
        }
    }

    private ScoredCandidate assignCandidate(String interventionId, ScoredCandidate candidate, int position) {
        try {
            // Handle preemption if necessary
            if (candidate.requiresPreemption()) {
                LOG.info(() -> String.format("Preempting %s from intervention %s",
                        candidate.getCallSign(), candidate.getCurrentInterventionId()));
                apiClient.releaseAssignment(candidate.getCurrentAssignmentId());
            }

            // Create new assignment
            String role = determineRole(position);
            String assignmentId = apiClient.assignUnit(interventionId, candidate.getUnitId(), role);

            if (assignmentId != null) {
                LOG.info(() -> String.format("Dispatched %s to intervention %s (score=%.2f, ETA=%.1fs)",
                        candidate.getCallSign(), interventionId, candidate.getScore(), candidate.getTravelTimeSeconds()));
                return candidate;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "Failed to dispatch " + candidate.getCallSign());
        }
        return null;
    }

    @Override
    public int periodicDispatch() {
        LOG.fine("Running periodic dispatch...");

        try {
            PendingInterventionsDto pending = apiClient.getPendingInterventions();
            if (pending == null || pending.getInterventions() == null || pending.getInterventions().isEmpty()) {
                LOG.fine("No pending interventions");
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
                final int count = totalDispatched;
                LOG.info(() -> "Periodic dispatch completed: " + count + " units dispatched");
            }

            return totalDispatched;

        } catch (Exception e) {
            LOG.log(Level.SEVERE, e, () -> "Error in periodic dispatch");
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

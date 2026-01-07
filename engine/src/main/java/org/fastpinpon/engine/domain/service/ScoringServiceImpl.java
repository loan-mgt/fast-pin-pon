package org.fastpinpon.engine.domain.service;

import org.fastpinpon.engine.api.dto.CandidateDto;
import org.fastpinpon.engine.domain.model.DispatchConfig;
import org.fastpinpon.engine.domain.model.ScoredCandidate;

import java.util.logging.Logger;

/**
 * Implementation of ScoringService using multi-objective weighted scoring.
 * 
 * Score formula (lower = better):
 *   score = w1 * travel_time_seconds
 *         + w2 * coverage_penalty
 *         + w3 * capability_match_bonus (negative = better)
 *         + w5 * preemption_severity_delta (negative = better)
 *         + w6 * reassignment_cost
 */
public final class ScoringServiceImpl implements ScoringService {

    private static final Logger LOG = Logger.getLogger(ScoringServiceImpl.class.getName());
    
    // Maximum score to disqualify a candidate
    private static final double DISQUALIFIED_SCORE = Double.MAX_VALUE;

    @Override
    public ScoredCandidate score(CandidateDto candidate, int targetSeverity, DispatchConfig config) {
        double score = calculateTravelScore(candidate, config);
        score += calculateCoverageScore(candidate, config);
        score += calculateCapabilityScore(candidate, config);
        score += calculatePreemptionScore(candidate, targetSeverity, config);

        final double finalScore = score;
        LOG.fine(() -> String.format(
                "Scored %s: travel=%.1f, coverage=%.1f, total=%.2f",
                candidate.getCallSign(),
                candidate.getTravelTimeSeconds(),
                calculateCoverageScore(candidate, config),
                finalScore));

        return buildScoredCandidate(candidate, score);
    }

    /**
     * Calculate travel time component of score.
     * Primary factor - directly weighted travel time in seconds.
     */
    private double calculateTravelScore(CandidateDto candidate, DispatchConfig config) {
        return config.getTravelTimeWeight() * candidate.getTravelTimeSeconds();
    }

    /**
     * Calculate coverage penalty component.
     * Penalizes depleting units from bases below minimum reserve.
     */
    private double calculateCoverageScore(CandidateDto candidate, DispatchConfig config) {
        int otherUnits = candidate.getOtherUnitsAtBase();
        int minReserve = config.getMinReservePerBase();

        // If taking this unit leaves the base below reserve, apply penalty
        if (otherUnits < minReserve) {
            int shortage = minReserve - otherUnits;
            return config.getCoveragePenaltyWeight() * shortage * 100; // Scale penalty
        }
        return 0.0;
    }

    /**
     * Calculate capability match bonus.
     * Gives bonus (negative score) for exact unit type match.
     * For now, assumes all candidates passed through type filter are matches.
     */
    private double calculateCapabilityScore(CandidateDto candidate, DispatchConfig config) {
        // Bonus for being an available unit (not requiring preemption)
        if (candidate.isAvailable()) {
            return config.getCapabilityMatchWeight(); // Negative = bonus
        }
        return 0.0;
    }

    /**
     * Calculate preemption score component.
     * Handles units that are already assigned to other interventions.
     */
    private double calculatePreemptionScore(CandidateDto candidate, int targetSeverity, DispatchConfig config) {
        // If not currently assigned, no preemption needed
        if (!candidate.isCurrentlyAssigned()) {
            return 0.0;
        }

        Integer currentSeverity = candidate.getCurrentInterventionSeverity();
        if (currentSeverity == null) {
            // Unknown current severity - disqualify for safety
            return DISQUALIFIED_SCORE;
        }

        int severityDelta = targetSeverity - currentSeverity;
        int threshold = config.getPreemptionSeverityThreshold();

        // If severity delta is below threshold, disqualify this candidate
        if (severityDelta < threshold) {
            LOG.fine(() -> String.format(
                    "Disqualifying %s: severity delta %d < threshold %d",
                    candidate.getCallSign(), severityDelta, threshold));
            return DISQUALIFIED_SCORE;
        }

        // Apply preemption bonus (negative) based on severity delta
        // Plus reassignment cost (positive penalty)
        double preemptionBonus = config.getPreemptionDeltaWeight() * severityDelta;
        double reassignmentPenalty = config.getReassignmentCost();

        return preemptionBonus + reassignmentPenalty;
    }

    /**
     * Build a ScoredCandidate from DTO and computed score.
     */
    private ScoredCandidate buildScoredCandidate(CandidateDto dto, double score) {
        double lat = dto.getLocation() != null ? dto.getLocation().getLatitude() : 0;
        double lon = dto.getLocation() != null ? dto.getLocation().getLongitude() : 0;

        return new ScoredCandidate.Builder()
                .unitId(dto.getId())
                .callSign(dto.getCallSign())
                .unitTypeCode(dto.getUnitTypeCode())
                .homeBase(dto.getHomeBase())
                .status(dto.getStatus())
                .latitude(lat)
                .longitude(lon)
                .travelTimeSeconds(dto.getTravelTimeSeconds())
                .distanceMeters(dto.getDistanceMeters())
                .otherUnitsAtBase(dto.getOtherUnitsAtBase())
                .currentAssignmentId(dto.getCurrentAssignmentId())
                .currentInterventionId(dto.getCurrentInterventionId())
                .currentInterventionSeverity(dto.getCurrentInterventionSeverity())
                .score(score)
                .build();
    }
}

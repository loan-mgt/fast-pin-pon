package org.fastpinpon.engine.domain.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for dispatch scoring weights and thresholds.
 * Loaded from API at startup and cached.
 */
public final class DispatchConfig {

    private final Map<String, Double> values;

    // Weight keys
    public static final String WEIGHT_TRAVEL_TIME = "weight_travel_time";
    public static final String WEIGHT_COVERAGE_PENALTY = "weight_coverage_penalty";
    public static final String WEIGHT_CAPABILITY_MATCH = "weight_capability_match";
    public static final String WEIGHT_EN_ROUTE_PROGRESS = "weight_en_route_progress";
    public static final String WEIGHT_PREEMPTION_DELTA = "weight_preemption_delta";
    public static final String WEIGHT_REASSIGNMENT_COST = "weight_reassignment_cost";
    
    // Threshold keys
    public static final String MIN_RESERVE_PER_BASE = "min_reserve_per_base";
    public static final String PREEMPTION_SEVERITY_THRESHOLD = "preemption_severity_threshold";
    public static final String MAX_CANDIDATES_PER_DISPATCH = "max_candidates_per_dispatch";

    private DispatchConfig(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    /**
     * Creates a DispatchConfig from a map of key-value pairs.
     */
    public static DispatchConfig fromMap(Map<String, Double> values) {
        Objects.requireNonNull(values, "values must not be null");
        return new DispatchConfig(values);
    }

    /**
     * Creates a default configuration.
     */
    public static DispatchConfig defaults() {
        Map<String, Double> defaults = new HashMap<>();
        defaults.put(WEIGHT_TRAVEL_TIME, 1.0);
        defaults.put(WEIGHT_COVERAGE_PENALTY, 0.3);
        defaults.put(WEIGHT_CAPABILITY_MATCH, -50.0);
        defaults.put(WEIGHT_EN_ROUTE_PROGRESS, 0.2);
        defaults.put(WEIGHT_PREEMPTION_DELTA, -100.0);
        defaults.put(WEIGHT_REASSIGNMENT_COST, 60.0);
        defaults.put(MIN_RESERVE_PER_BASE, 1.0);
        defaults.put(PREEMPTION_SEVERITY_THRESHOLD, 2.0);
        defaults.put(MAX_CANDIDATES_PER_DISPATCH, 10.0);
        return new DispatchConfig(defaults);
    }

    /**
     * Gets a configuration value by key.
     */
    public double get(String key) {
        Double value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown config key: " + key);
        }
        return value;
    }

    /**
     * Gets a configuration value by key, with a default.
     */
    public double getOrDefault(String key, double defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    // Convenience getters for weights
    public double getTravelTimeWeight() {
        return getOrDefault(WEIGHT_TRAVEL_TIME, 1.0);
    }

    public double getCoveragePenaltyWeight() {
        return getOrDefault(WEIGHT_COVERAGE_PENALTY, 0.3);
    }

    public double getCapabilityMatchWeight() {
        return getOrDefault(WEIGHT_CAPABILITY_MATCH, -50.0);
    }

    public double getEnRouteProgressWeight() {
        return getOrDefault(WEIGHT_EN_ROUTE_PROGRESS, 0.2);
    }

    public double getPreemptionDeltaWeight() {
        return getOrDefault(WEIGHT_PREEMPTION_DELTA, -100.0);
    }

    public double getReassignmentCost() {
        return getOrDefault(WEIGHT_REASSIGNMENT_COST, 60.0);
    }

    // Convenience getters for thresholds
    public int getMinReservePerBase() {
        return (int) getOrDefault(MIN_RESERVE_PER_BASE, 1.0);
    }

    public int getPreemptionSeverityThreshold() {
        return (int) getOrDefault(PREEMPTION_SEVERITY_THRESHOLD, 2.0);
    }

    public int getMaxCandidatesPerDispatch() {
        return (int) getOrDefault(MAX_CANDIDATES_PER_DISPATCH, 10.0);
    }

    @Override
    public String toString() {
        return "DispatchConfig" + values;
    }
}

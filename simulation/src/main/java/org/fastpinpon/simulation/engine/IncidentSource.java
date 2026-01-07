package org.fastpinpon.simulation.engine;

import org.fastpinpon.simulation.model.Incident;

/**
 * Interface for incident sources.
 * Implementations can provide incidents from different sources:
 * - Random generation (simulation)
 * - Real events from API
 * - External systems (CAD, 911, etc.)
 */
public interface IncidentSource {

    /**
     * Check if a new incident is available from this source.
     * 
     * @return true if a new incident should be generated/fetched
     */
    boolean hasNewIncident();

    /**
     * Generate or fetch the next incident from this source.
     * Should only be called when hasNewIncident() returns true.
     * 
     * @return the new incident, or null if none available
     */
    Incident nextIncident();

    /**
     * Called after an incident has been successfully processed by the decision engine.
     * Allows the source to perform any post-processing (e.g., mark as handled).
     * 
     * @param incident the incident that was processed
     */
    default void onIncidentProcessed(Incident incident) {
        // Default: no-op
    }
}

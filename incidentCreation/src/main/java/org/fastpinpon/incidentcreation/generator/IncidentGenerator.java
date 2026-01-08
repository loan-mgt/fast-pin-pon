package org.fastpinpon.incidentcreation.generator;

import org.fastpinpon.incidentcreation.model.Incident;
import org.fastpinpon.incidentcreation.model.IncidentType;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Random incident generator for simulation purposes.
 * Generates incidents at configurable intervals with random parameters.
 */
public final class IncidentGenerator {
    private static final Logger LOG = Logger.getLogger(IncidentGenerator.class.getName());

    private static final double CITY_CENTER_LAT = 45.75;
    private static final double CITY_CENTER_LON = 4.85;
    private static final long DEFAULT_INCIDENT_INTERVAL_SECONDS = 60;

    // Unit types typically needed for each incident type
    private static final Map<IncidentType, List<String>> INCIDENT_UNIT_TYPES = new EnumMap<>(IncidentType.class);
    
    static {
        // Fire incidents need fire trucks (FPT) and ladder trucks (EPA)
        INCIDENT_UNIT_TYPES.put(IncidentType.FEU, Arrays.asList("FPT", "EPA"));
        // Accidents need rescue vehicles (VSAV) and sometimes fire trucks
        INCIDENT_UNIT_TYPES.put(IncidentType.ACCIDENT, Arrays.asList("VSAV", "FPT"));
        // Floods need pumping equipment (MPR) and general purpose vehicles
        INCIDENT_UNIT_TYPES.put(IncidentType.INONDATION, Arrays.asList("MPR", "VLHR"));
    }

    private final Random random = new Random();
    private final long incidentIntervalSeconds;

    private int incidentSequence = 0;
    private Instant lastIncidentAt;
    private boolean firstIncidentSpawned = false;
    private Incident pendingIncident = null;

    /**
     * Create a generator with the default 60-second interval between incidents.
     */
    public IncidentGenerator() {
        this(DEFAULT_INCIDENT_INTERVAL_SECONDS);
    }

    /**
     * Create a generator with a custom interval between incidents.
     * 
     * @param incidentIntervalSeconds seconds between incident generation
     */
    public IncidentGenerator(long incidentIntervalSeconds) {
        this.incidentIntervalSeconds = incidentIntervalSeconds;
        this.lastIncidentAt = Instant.now();
    }

    /**
     * Check if a new incident is available.
     * 
     * @return true if a new incident should be generated
     */
    public boolean hasNewIncident() {
        if (pendingIncident != null) {
            return true;
        }

        Instant now = Instant.now();
        
        // Always generate a first incident immediately
        if (!firstIncidentSpawned) {
            pendingIncident = generateIncident();
            firstIncidentSpawned = true;
            lastIncidentAt = now;
            return true;
        }

        // Generate subsequent incidents at intervals
        if (now.getEpochSecond() - lastIncidentAt.getEpochSecond() >= incidentIntervalSeconds) {
            pendingIncident = generateIncident();
            lastIncidentAt = now;
            return true;
        }

        return false;
    }

    /**
     * Get the next generated incident.
     * Should only be called when hasNewIncident() returns true.
     * 
     * @return the new incident, or null if none available
     */
    public Incident nextIncident() {
        Incident result = pendingIncident;
        pendingIncident = null;
        return result;
    }

    /**
     * Generate a random incident with random type, location, and severity.
     * Also assigns required unit types based on incident type.
     * 
     * @return the generated incident
     */
    private Incident generateIncident() {
        IncidentType type = IncidentType.values()[random.nextInt(IncidentType.values().length)];
        double lat = CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.05;
        double lon = CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.05;
        int gravite = 1 + random.nextInt(5);
        int number = ++incidentSequence;

        Incident incident = new Incident(number, type, lat, lon, gravite);
        
        // Assign required unit types based on incident type
        List<String> requiredTypes = INCIDENT_UNIT_TYPES.get(type);
        if (requiredTypes != null) {
            incident.setRequiredUnitTypes(requiredTypes);
        }
        
        if (LOG.isLoggable(Level.INFO)) {
            String types = requiredTypes != null ? requiredTypes.toString() : "any";
            LOG.log(Level.INFO, "[Generator] Generated incident #{0}: Type={1}, Severity={2}, RequiredUnits={3}, Location=({4}, {5})",
                    new Object[]{number, type, gravite, types,
                            String.format("%.5f", lat), String.format("%.5f", lon)});
        }

        return incident;
    }

    /**
     * Get the current incident sequence number.
     * 
     * @return the last assigned incident number
     */
    public int getIncidentSequence() {
        return incidentSequence;
    }

    /**
     * Set the incident sequence number (useful for resuming from a saved state).
     * 
     * @param sequence the sequence number to start from
     */
    public void setIncidentSequence(int sequence) {
        this.incidentSequence = sequence;
    }

    /**
     * Get the configured interval between incidents.
     * 
     * @return interval in seconds
     */
    public long getIncidentIntervalSeconds() {
        return incidentIntervalSeconds;
    }
}

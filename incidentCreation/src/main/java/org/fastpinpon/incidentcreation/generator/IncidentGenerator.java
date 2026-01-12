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
 * Generates incidents at configurable intervals with weighted probability distribution.
 * 
 * Probability distribution:
 * - 70%: CRASH, RESCUE_MEDICAL, OTHER (common incidents)
 * - 20%: FIRE_URBAN, FIRE_INDUSTRIAL (fire incidents)
 * - 10%: HAZMAT, AQUATIC_RESCUE (rare/specialized incidents)
 */
public final class IncidentGenerator {
    private static final Logger LOG = Logger.getLogger(IncidentGenerator.class.getName());

    private static final double CITY_CENTER_LAT = 45.75;
    private static final double CITY_CENTER_LON = 4.85;
    private static final long DEFAULT_INCIDENT_INTERVAL_SECONDS = 180;

    // Water locations along the Rhône and Saône rivers in Lyon for AQUATIC_RESCUE incidents
    // Format: {latitude, longitude}
    private static final double[][] WATER_LOCATIONS = {
        // Saône river (west side of Presqu'île, flows north to south)
        {45.7850, 4.8280},  // Saône - Île Barbe area
        {45.7780, 4.8260},  // Saône - Vaise
        {45.7700, 4.8270},  // Saône - Saint-Paul
        {45.7650, 4.8290},  // Saône - Vieux Lyon
        {45.7580, 4.8270},  // Saône - Saint-Georges
        {45.7520, 4.8250},  // Saône - near Perrache
        {45.7450, 4.8220},  // Saône - approaching Confluence
        
        // Rhône river (east side of Presqu'île, flows north to south)
        {45.7900, 4.8580},  // Rhône - Parc de la Tête d'Or area
        {45.7820, 4.8560},  // Rhône - Cité Internationale
        {45.7750, 4.8500},  // Rhône - Part-Dieu area
        {45.7680, 4.8450},  // Rhône - Guillotière
        {45.7600, 4.8400},  // Rhône - Jean Macé area
        {45.7530, 4.8350},  // Rhône - Gerland
        {45.7450, 4.8300},  // Rhône - approaching Confluence
        
        // Confluence (where Rhône and Saône meet)
        {45.7380, 4.8180},  // Confluence - south tip
        {45.7350, 4.8150},  // Confluence - La Confluence district
        {45.7320, 4.8120},  // Confluence - south
    };

    // Incident type probability tiers
    private static final IncidentType[] COMMON_TYPES = {
        IncidentType.CRASH, IncidentType.RESCUE_MEDICAL, IncidentType.OTHER
    };
    private static final IncidentType[] FIRE_TYPES = {
        IncidentType.FIRE_URBAN, IncidentType.FIRE_INDUSTRIAL
    };
    private static final IncidentType[] RARE_TYPES = {
        IncidentType.HAZMAT, IncidentType.AQUATIC_RESCUE
    };

    // Unit types typically needed for each incident type
    private static final Map<IncidentType, List<String>> INCIDENT_UNIT_TYPES = new EnumMap<>(IncidentType.class);
    
    static {
        // Common incidents (70%)
        INCIDENT_UNIT_TYPES.put(IncidentType.CRASH, Arrays.asList("VER", "VSAV"));
        INCIDENT_UNIT_TYPES.put(IncidentType.RESCUE_MEDICAL, Arrays.asList("VSAV"));
        INCIDENT_UNIT_TYPES.put(IncidentType.OTHER, Arrays.asList("VSAV"));
        
        // Fire incidents (20%)
        INCIDENT_UNIT_TYPES.put(IncidentType.FIRE_URBAN, Arrays.asList("FPT", "EPA"));
        INCIDENT_UNIT_TYPES.put(IncidentType.FIRE_INDUSTRIAL, Arrays.asList("FPT", "FPTL", "EPA"));
        
        // Rare incidents (10%)
        INCIDENT_UNIT_TYPES.put(IncidentType.HAZMAT, Arrays.asList("FPTL", "VLHR"));
        INCIDENT_UNIT_TYPES.put(IncidentType.AQUATIC_RESCUE, Arrays.asList("VER", "VIM"));
    }

    private final Random random = new Random();
    private final long incidentIntervalSeconds;

    private int incidentSequence = 0;
    private Instant lastIncidentAt;
    private boolean firstIncidentSpawned = false;
    private Incident pendingIncident = null;

    /**
     * Create a generator with the default 180-second interval between incidents.
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
     * Incident type distribution:
     * - 70%: CRASH, RESCUE_MEDICAL, OTHER
     * - 20%: FIRE_URBAN, FIRE_INDUSTRIAL
     * - 10%: HAZMAT, AQUATIC_RESCUE
     * 
     * Special location handling:
     * - AQUATIC_RESCUE: Generated on Rhône/Saône rivers only
     * 
     * @return the generated incident
     */
    private Incident generateIncident() {
        IncidentType type = selectWeightedIncidentType();
        
        double lat;
        double lon;
        
        if (type == IncidentType.AQUATIC_RESCUE) {
            // AQUATIC_RESCUE incidents are generated on the rivers (Rhône/Saône)
            double[] waterLocation = WATER_LOCATIONS[random.nextInt(WATER_LOCATIONS.length)];
            // Add small random offset to avoid exact same spots (±50m)
            lat = waterLocation[0] + (random.nextDouble() - 0.5) * 0.001;
            lon = waterLocation[1] + (random.nextDouble() - 0.5) * 0.001;
        } else {
            // Other incidents spread across the city using Gaussian distribution
            double latOffset = random.nextGaussian() * 0.03;  // ~3km standard deviation
            double lonOffset = random.nextGaussian() * 0.04;  // ~3km standard deviation
            lat = CITY_CENTER_LAT + latOffset;
            lon = CITY_CENTER_LON + lonOffset;
        }
        
        int gravite = generateSeverityForType(type);
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

    /**
     * Select an incident type based on weighted probability distribution.
     * - 70%: CRASH, RESCUE_MEDICAL, OTHER (common incidents)
     * - 20%: FIRE_URBAN, FIRE_INDUSTRIAL (fire incidents)
     * - 10%: HAZMAT, AQUATIC_RESCUE (rare/specialized incidents)
     * 
     * @return the selected incident type
     */
    private IncidentType selectWeightedIncidentType() {
        int roll = random.nextInt(100);  // 0-99
        
        if (roll < 70) {
            // 70% - Common incidents
            return COMMON_TYPES[random.nextInt(COMMON_TYPES.length)];
        } else if (roll < 90) {
            // 20% - Fire incidents
            return FIRE_TYPES[random.nextInt(FIRE_TYPES.length)];
        } else {
            // 10% - Rare/specialized incidents
            return RARE_TYPES[random.nextInt(RARE_TYPES.length)];
        }
    }

    /**
     * Generate a severity based on weighted probability distribution.
     * - 70%: severity 1 or 2 (Faible)
     * - 20%: severity 3 or 4 (Modéré/Élevée)
     * - 10%: severity 5 (Critique)
     * 
     * @param type the incident type (unused, kept for API compatibility)
     * @return severity level 1-5
     */
    private int generateSeverityForType(IncidentType type) {
        int roll = random.nextInt(100);  // 0-99
        
        if (roll < 70) {
            // 70% - Faible (severity 1 or 2)
            return 1 + random.nextInt(2);  // Returns 1 or 2
        } else if (roll < 90) {
            // 20% - Modéré/Élevée (severity 3 or 4)
            return 3 + random.nextInt(2);  // Returns 3 or 4
        } else {
            // 10% - Critique (severity 5)
            return 5;
        }
    }
}

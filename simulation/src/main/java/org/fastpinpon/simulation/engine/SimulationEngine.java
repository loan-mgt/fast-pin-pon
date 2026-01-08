package org.fastpinpon.simulation.engine;

import org.fastpinpon.simulation.api.ApiClient;
import org.fastpinpon.simulation.model.BaseLocation;
import org.fastpinpon.simulation.model.Incident;
import org.fastpinpon.simulation.model.Vehicle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simulation engine that orchestrates incident generation and response decision-making.
 * 
 * This engine uses:
 * - An IncidentSource (default: IncidentGenerator) to produce incidents
 * - A DecisionEngine to handle unit dispatch and incident lifecycle
 * 
 * The engine is designed for flexibility:
 * - Replace the IncidentSource to use real events instead of simulation
 * - The DecisionEngine works independently of the incident source
 * - Multiple incident sources can be used simultaneously
 */
public final class SimulationEngine {
    private static final Logger LOG = Logger.getLogger(SimulationEngine.class.getName());

    private final ApiClient api;
    private final String apiBaseUrl;
    private final Random random = new Random();
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final DecisionEngine decisionEngine;
        private final List<BaseLocation> bases;
    private final List<IncidentSource> incidentSources = new ArrayList<>();

    private int tickCounter = 0;

    private static final double CITY_CENTER_LAT = 45.75;
    private static final double CITY_CENTER_LON = 4.85;
    
    // Fleet distribution: 35 total units
    // Lyon Confluence: 8, Villeurbanne: 8, Cusset: 8, Lyon Part-Dieu: 11
    private static final int TARGET_FLEET_SIZE = 35;
    
    // Units per base (custom distribution)
    private static final int UNITS_CONFLUENCE = 8;
    private static final int UNITS_VILLEURBANNE = 8;
    private static final int UNITS_CUSSET = 8;
    private static final int UNITS_PART_DIEU = 11;

    // Base names
    private static final String BASE_VILLEURBANNE = "Villeurbanne";
    private static final String BASE_CONFLUENCE = "Lyon Confluence";
    private static final String BASE_PART_DIEU = "Lyon Part-Dieu";
    private static final String BASE_CUSSET = "Cusset";

        private static final BaseLocation[] DEFAULT_BASES = new BaseLocation[]{
            new BaseLocation(BASE_VILLEURBANNE, 45.766180, 4.878770),
            new BaseLocation(BASE_CONFLUENCE, 45.741054, 4.823733),
            new BaseLocation(BASE_PART_DIEU, 45.760540, 4.861700),
            new BaseLocation(BASE_CUSSET, 45.76623, 4.89534),
    };

    public static final class VehicleSnapshot {
        private final String unitId;
        private final String callSign;
        private final double lat;
        private final double lon;
        private final String status;
        private final String incidentId;
        private final String unitTypeCode;
        private final String homeBase;
        private final Instant lastUpdate;

        public VehicleSnapshot(String unitId, String callSign, double lat, double lon, String status,
                               String incidentId, String unitTypeCode, String homeBase, Instant lastUpdate) {
            this.unitId = unitId;
            this.callSign = callSign;
            this.lat = lat;
            this.lon = lon;
            this.status = status;
            this.incidentId = incidentId;
            this.unitTypeCode = unitTypeCode;
            this.homeBase = homeBase;
            this.lastUpdate = lastUpdate;
        }

        public String getUnitId() {
            return unitId;
        }

        public String getCallSign() {
            return callSign;
        }

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
        }

        public String getStatus() {
            return status;
        }

        public String getIncidentId() {
            return incidentId;
        }

        public String getUnitTypeCode() {
            return unitTypeCode;
        }

        public String getHomeBase() {
            return homeBase;
        }

        public Instant getLastUpdate() {
            return lastUpdate;
        }
    }

    /**
     * Create a simulation engine with the default random incident generator.
     * 
     * @param api the API client
     * @param apiBaseUrl the API base URL for routing
     */
    public SimulationEngine(ApiClient api, String apiBaseUrl) {
        this(api, apiBaseUrl, new IncidentGenerator());
    }

    /**
     * Create a simulation engine with a custom incident source.
     * 
     * @param api the API client
     * @param apiBaseUrl the API base URL for routing
     * @param incidentSource the source of incidents
     */
    public SimulationEngine(ApiClient api, String apiBaseUrl, IncidentSource incidentSource) {
        this.api = api;
        this.apiBaseUrl = apiBaseUrl;
        // Load stations from API; fallback to defaults
        List<BaseLocation> stations = api.loadStations();
        this.bases = stations != null && !stations.isEmpty() ? stations : new ArrayList<>(Arrays.asList(DEFAULT_BASES));
        bootstrapUnits();
        this.decisionEngine = new DecisionEngine(api, vehicles, this.bases, apiBaseUrl);
        if (incidentSource != null) {
            this.incidentSources.add(incidentSource);
        }
    }

    /**
     * Create a simulation engine without any incident source.
     * Use addIncidentSource() to add sources, or call processIncident() directly.
     * 
     * @param api the API client
     * @param apiBaseUrl the API base URL for routing
     * @param noGenerator set to true to create without generator
     */
    public SimulationEngine(ApiClient api, String apiBaseUrl, boolean noGenerator) {
        this.api = api;
        this.apiBaseUrl = apiBaseUrl;
        List<BaseLocation> stations = api.loadStations();
        this.bases = stations != null && !stations.isEmpty() ? stations : new ArrayList<>(Arrays.asList(DEFAULT_BASES));
        bootstrapUnits();
        this.decisionEngine = new DecisionEngine(api, vehicles, this.bases, apiBaseUrl);
        if (!noGenerator) {
            this.incidentSources.add(new IncidentGenerator());
        }
    }

    /**
     * Add an incident source to the engine.
     * Multiple sources can be active simultaneously.
     * 
     * @param source the incident source to add
     */
    public void addIncidentSource(IncidentSource source) {
        if (source != null) {
            incidentSources.add(source);
        }
    }

    /**
     * Remove an incident source from the engine.
     * 
     * @param source the incident source to remove
     */
    public void removeIncidentSource(IncidentSource source) {
        incidentSources.remove(source);
    }

    /**
     * Clear all incident sources.
     * Useful when switching from simulation to real events.
     */
    public void clearIncidentSources() {
        incidentSources.clear();
    }

    /**
     * Directly process an incident without using a source.
     * This allows external systems to inject incidents directly.
     * 
     * @param incident the incident to process
     * @return true if the incident was successfully processed
     */
    public boolean processIncident(Incident incident) {
        return decisionEngine.processNewIncident(incident);
    }

    /**
     * Process an incident that already exists in the API.
     * Use this for real events that are already registered.
     * 
     * @param incident the incident with eventId already set
     * @return true if the incident was successfully processed
     */
    public boolean processExistingIncident(Incident incident) {
        return decisionEngine.processExistingIncident(incident);
    }

    /**
     * Main simulation tick - called periodically.
     * Checks for new incidents from all sources and advances the simulation state.
     */
    public synchronized void tick() {
        if (tickCounter % 3 == 0) {
            logTickStatus();
        }
        tickCounter++;
        
        // Check all incident sources for new incidents
        pollIncidentSources();
        
        // Advance incident and vehicle states
        decisionEngine.tick();
        
        // Push telemetry to API
        decisionEngine.pushTelemetry();
    }

    /**
     * Get the decision engine for direct access to decision-making capabilities.
     * 
     * @return the decision engine
     */
    public DecisionEngine getDecisionEngine() {
        return decisionEngine;
    }

    /**
     * Get all managed vehicles.
     * 
     * @return list of vehicles
     */
    public List<Vehicle> getVehicles() {
        return new ArrayList<>(vehicles);
    }

    /**
     * Snapshot current vehicle telemetry for external consumers (e.g., bridge).
     * The simulation engine remains the single source of movement; consumers only read.
     */
    public synchronized List<VehicleSnapshot> snapshotVehicles() {
        List<VehicleSnapshot> snapshots = new ArrayList<>(vehicles.size());
        for (Vehicle v : vehicles) {
            String incidentId = null;
            Incident incident = v.getCurrentIncident();
            if (incident != null) {
                if (incident.getId() != null) {
                    incidentId = incident.getId().toString();
                } else if (incident.getEventId() != null) {
                    incidentId = incident.getEventId();
                }
            }
            snapshots.add(new VehicleSnapshot(
                    v.getUnitId(),
                    v.getCallSign(),
                    v.getLat(),
                    v.getLon(),
                    snapshotStatus(v.getEtat()),
                    incidentId,
                    v.getUnitTypeCode(),
                    v.getHomeBase(),
                    v.getLastUpdate()
            ));
        }
        return snapshots;
    }

    // =========================================================================
    // Incident Source Management
    // =========================================================================

    private void pollIncidentSources() {
        for (IncidentSource source : incidentSources) {
            while (source.hasNewIncident()) {
                Incident incident = source.nextIncident();
                if (incident != null) {
                    boolean success = decisionEngine.processNewIncident(incident);
                    if (success) {
                        source.onIncidentProcessed(incident);
                    }
                }
            }
        }
    }

    // =========================================================================
    // Fleet Management
    // =========================================================================

    private void bootstrapUnits() {
        List<ApiClient.UnitInfo> units = api.loadUnits();
        collectUnitTypes(units);
        createMissingUnits(units);
        materializeVehicles(units);
        logFleetDistribution();
    }

    private void logFleetDistribution() {
        if (!LOG.isLoggable(Level.INFO)) {
            return;
        }
        // Count units per base
        int[] counts = new int[bases.size()];
        for (Vehicle v : vehicles) {
            for (int i = 0; i < bases.size(); i++) {
                if (bases.get(i).name.equals(v.getHomeBase())) {
                    counts[i]++;
                    break;
                }
            }
        }
        StringBuilder sb = new StringBuilder("[SIM] Fleet distribution (").append(vehicles.size()).append(" units): ");
        for (int i = 0; i < bases.size(); i++) {
            sb.append(bases.get(i).name).append("=").append(counts[i]);
            if (i < bases.size() - 1) {
                sb.append(", ");
            }
        }
        LOG.info(sb.toString());
    }

    private void collectUnitTypes(List<ApiClient.UnitInfo> units) {
        if (!api.unitTypeCodes.isEmpty()) {
            return;
        }
        for (ApiClient.UnitInfo u : units) {
            if (u.unitTypeCode != null && !u.unitTypeCode.isEmpty() && !api.unitTypeCodes.contains(u.unitTypeCode)) {
                api.unitTypeCodes.add(u.unitTypeCode);
            }
        }
    }

    private void createMissingUnits(List<ApiClient.UnitInfo> units) {
        // Count how many units already exist per base for positioning
        Map<String, Integer> baseUnitCounts = new HashMap<>();
        baseUnitCounts.put(BASE_CONFLUENCE, 0);
        baseUnitCounts.put(BASE_VILLEURBANNE, 0);
        baseUnitCounts.put(BASE_CUSSET, 0);
        baseUnitCounts.put(BASE_PART_DIEU, 0);
        
        for (ApiClient.UnitInfo u : units) {
            if (u.homeBase != null && baseUnitCounts.containsKey(u.homeBase)) {
                baseUnitCounts.put(u.homeBase, baseUnitCounts.get(u.homeBase) + 1);
            }
        }
        
        // Target units per base
        Map<String, Integer> targetPerBase = new HashMap<>();
        targetPerBase.put(BASE_CONFLUENCE, UNITS_CONFLUENCE);
        targetPerBase.put(BASE_VILLEURBANNE, UNITS_VILLEURBANNE);
        targetPerBase.put(BASE_CUSSET, UNITS_CUSSET);
        targetPerBase.put(BASE_PART_DIEU, UNITS_PART_DIEU);

        // Fill each base to its target count
        for (BaseLocation base : bases) {
            int target = targetPerBase.getOrDefault(base.name, 8);
            int current = baseUnitCounts.getOrDefault(base.name, 0);
            
            while (current < target && units.size() < TARGET_FLEET_SIZE) {
                String typeCode = pickUsableType(units);
                if (typeCode == null) {
                    return;
                }
                
                // Position units in a horizontal row (East-West line)
                double spacingMeters = 15.0;
                double spacingLon = spacingMeters / 78500.0;
                
                // Center the row based on number of units at this base
                double centerOffset = (target - 1) / 2.0;
                double offsetLon = (current - centerOffset) * spacingLon;
                double offsetLat = -0.0001;  // ~11m south of base point
                
                ApiClient.UnitInfo created = api.createUnit(
                        String.format("%s-%02d", getBasePrefix(base.name), current + 1),
                        typeCode,
                        base.name,
                        base.lat + offsetLat,
                        base.lon + offsetLon
                );
                if (created == null) {
                    return;
                }
                units.add(created);
                current++;
                baseUnitCounts.put(base.name, current);
            }
        }
    }

    private String pickUsableType(List<ApiClient.UnitInfo> units) {
        String typeCode = api.pickUnitType();
        if (typeCode != null) {
            return typeCode;
        }
        if (!units.isEmpty()) {
            return units.get(0).unitTypeCode;
        }
        return null;
    }

    private void materializeVehicles(List<ApiClient.UnitInfo> units) {
        // Target units per base for centering calculation
        Map<String, Integer> targetPerBase = new HashMap<>();
        targetPerBase.put(BASE_CONFLUENCE, UNITS_CONFLUENCE);
        targetPerBase.put(BASE_VILLEURBANNE, UNITS_VILLEURBANNE);
        targetPerBase.put(BASE_CUSSET, UNITS_CUSSET);
        targetPerBase.put(BASE_PART_DIEU, UNITS_PART_DIEU);
        
        // Group units by home base and position them in rows
        Map<String, Integer> baseUnitIndex = new HashMap<>();
        for (String baseName : new String[]{BASE_CONFLUENCE, BASE_PART_DIEU, BASE_VILLEURBANNE, BASE_CUSSET}) {
            baseUnitIndex.put(baseName, 0);
        }
        
        for (ApiClient.UnitInfo u : units) {
            String home = determineHomeBase(u);
            
            // Always position units in a row at their home base for consistent display
            BaseLocation homeBase = getBaseByName(home);
            double lat;
            double lon;
            
            if (homeBase != null) {
                int unitAtBase = baseUnitIndex.getOrDefault(home, 0);
                int targetUnits = targetPerBase.getOrDefault(home, 8);
                
                // Position units in a horizontal row (East-West line)
                double spacingMeters = 15.0;
                double spacingLon = spacingMeters / 78500.0;
                
                // Center the row based on target unit count at this base
                double centerOffset = (targetUnits - 1) / 2.0;
                double offsetLon = (unitAtBase - centerOffset) * spacingLon;
                double offsetLat = -0.0001;  // ~11m south of base point
                
                lat = homeBase.lat + offsetLat;
                lon = homeBase.lon + offsetLon;
                
                baseUnitIndex.put(home, unitAtBase + 1);
            } else {
                // Fallback for unknown base
                lat = CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.02;
                lon = CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.02;
            }
            
            Vehicle v = new Vehicle(u.id, u.callSign, u.unitTypeCode, home, lat, lon);
            vehicles.add(v);
            
            // Update API with correct position
            api.updateUnitLocation(u.id, lat, lon, Instant.now());
        }
    }

    private BaseLocation getBaseByName(String name) {
        for (BaseLocation base : bases) {
            if (base.name.equals(name)) {
                return base;
            }
        }
        return null;
    }
    
    private String determineHomeBase(ApiClient.UnitInfo u) {
        if (u.homeBase != null && !u.homeBase.isEmpty()) {
            return u.homeBase;
        }
        double lat = u.latitude != null ? u.latitude : CITY_CENTER_LAT;
        double lon = u.longitude != null ? u.longitude : CITY_CENTER_LON;
        return nearestBaseName(lat, lon);
    }

    private String getBasePrefix(String baseName) {
        switch (baseName) {
            case BASE_CONFLUENCE: return "CON";
            case BASE_PART_DIEU: return "PDI";
            case BASE_VILLEURBANNE: return "VIL";
            case BASE_CUSSET: return "CUS";
            default: return "UNK";
        }
    }

    // =========================================================================
    // Logging
    // =========================================================================

    private void logTickStatus() {
        List<Incident> activeIncidents = decisionEngine.getActiveIncidents();
        if (activeIncidents.isEmpty()) {
            return;
        }
        int activeVehicles = decisionEngine.getActiveVehicleCount();
        logActiveIncidentSummary(activeIncidents, activeVehicles);
        logIncidentsSnapshot(activeIncidents);
    }

    private void logActiveIncidentSummary(List<Incident> activeIncidents, int active) {
        if (!LOG.isLoggable(Level.INFO)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < activeIncidents.size(); i++) {
            sb.append("{").append(activeIncidents.get(i).getNumber()).append("}");
            if (i < activeIncidents.size() - 1) {
                sb.append(",");
            }
        }
        LOG.log(Level.INFO, "[SIM] Treating incident : {0} | Active units = {1}",
                new Object[]{sb, active});
    }

    private void logIncidentsSnapshot(List<Incident> scope) {
        for (Incident incident : scope) {
            List<Vehicle> involved = new ArrayList<>();
            for (Vehicle v : vehicles) {
                if (v.getCurrentIncident() == incident) {
                    involved.add(v);
                }
            }
            if (involved.isEmpty()) {
                continue;
            }
            involved.sort((a, b) -> a.getCallSign().compareToIgnoreCase(b.getCallSign()));
            String area = nearestBaseName(incident.getLat(), incident.getLon());
            StringBuilder line = new StringBuilder();
            line.append("[SIM] Incident ").append(incident.getNumber()).append(" - ").append(area).append(" : ");
            for (int i = 0; i < involved.size(); i++) {
                Vehicle v = involved.get(i);
                line.append(v.getCallSign()).append(" [").append(readableState(v.getEtat())).append("]");
                if (i < involved.size() - 1) {
                    line.append(", ");
                }
            }
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, line.toString());
            }
        }
    }

    private String readableState(org.fastpinpon.simulation.model.VehicleState state) {
        switch (state) {
            case DISPONIBLE:
                return "available";
            case EN_ROUTE:
                return "under way";
            case SUR_PLACE:
                return "on site";
            case RETOUR:
                return "return";
            default:
                return "unknown";
        }
    }

    private String snapshotStatus(org.fastpinpon.simulation.model.VehicleState state) {
        switch (state) {
            case DISPONIBLE:
                return "available";
            case EN_ROUTE:
                return "under_way";
            case SUR_PLACE:
                return "on_site";
            case RETOUR:
                return "under_way";
            default:
                return "available";
        }
    }

    private String nearestBaseName(double lat, double lon) {
        String name = BASE_PART_DIEU;
        double best = Double.MAX_VALUE;
        for (BaseLocation b : bases) {
            double d = Math.pow(lat - b.lat, 2) + Math.pow(lon - b.lon, 2);
            if (d < best) {
                best = d;
                name = b.name;
            }
        }
        return name;
    }
}

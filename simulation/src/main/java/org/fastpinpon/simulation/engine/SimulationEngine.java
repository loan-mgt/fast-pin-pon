package org.fastpinpon.simulation.engine;

import org.fastpinpon.simulation.api.ApiClient;
import org.fastpinpon.simulation.model.BaseLocation;
import org.fastpinpon.simulation.model.Incident;
import org.fastpinpon.simulation.model.IncidentState;
import org.fastpinpon.simulation.model.IncidentType;
import org.fastpinpon.simulation.model.Vehicle;
import org.fastpinpon.simulation.model.VehicleState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simulation engine:
 * - Spawns random incidents, posts them as events to the Fast Pin Pon API.
 * - Creates an intervention for each event, assigns available units, and drives their lifecycle.
 * - Periodically pushes unit status/location and event heartbeats.
 */
public final class SimulationEngine {
    private static final Logger LOG = Logger.getLogger(SimulationEngine.class.getName());

    private final ApiClient api;
    private final Random random = new Random();
    private final List<Incident> incidents = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();
    private int incidentSequence = 0;
    private int tickCounter = 0;

    private static final long MIN_TRAVEL_SECONDS = 10;
    private static final long INCIDENT_RESOLVE_SECONDS = 60;
    private static final long RETURN_SECONDS = 10;

    private final Instant simStart = Instant.now();
    private Instant lastIncidentAt = simStart;
    private boolean firstIncidentSpawned = false;

    private static final String SIM_INCIDENT_PREFIX = "[SIM] Incident ";

    // Base names
    private static final String BASE_VILLEURBANNE = "Villeurbanne";
    private static final String BASE_CONFLUENCE = "Lyon Confluence";
    private static final String BASE_PART_DIEU = "Lyon Part-Dieu";
    private static final String BASE_CUSSET = "Cusset";

    // Backend status constants to avoid enum mismatches
    private static final String EVENT_STATUS_ACK = "acknowledged";
    private static final String EVENT_STATUS_CLOSED = "closed";
    private static final String ASSIGNMENT_STATUS_ARRIVED = "arrived";
    private static final String ASSIGNMENT_STATUS_RELEASED = "released";
    // API intervention statuses (align with backend enum)
    private static final String INTERVENTION_STATUS_EN_ROUTE = "created"; // initial state accepted by API
    private static final String INTERVENTION_STATUS_ON_SITE = "on_site";
    private static final String INTERVENTION_STATUS_COMPLETED = "completed";

    // Using the API's current accepted unit statuses (avoid 400 from validator)
    private static final String UNIT_STATUS_AVAILABLE = "available";
    private static final String UNIT_STATUS_EN_ROUTE = "under_way";
    private static final String UNIT_STATUS_ON_SITE = "on_site";

    private static final boolean PATCH_ASSIGNMENT_STATUS = true;
    private static final boolean PATCH_INTERVENTION_STATUS = true;
    private static final boolean PATCH_EVENT_STATUS = true;

    private static final DateTimeFormatter INTERVENTION_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final double CITY_CENTER_LAT = 45.75;
    private static final double CITY_CENTER_LON = 4.85;
    private static final int TARGET_FLEET_SIZE = 35;

    private static final BaseLocation[] BASES = new BaseLocation[]{
            new BaseLocation(BASE_VILLEURBANNE, 45.7719, 4.8902),
            new BaseLocation(BASE_CONFLUENCE, 45.7421, 4.8158),
            new BaseLocation(BASE_PART_DIEU, 45.7601, 4.8590),
            new BaseLocation(BASE_CUSSET, 45.7744, 4.8957),
    };

    private static final Map<String, List<String>> BASE_PRIORITY = new HashMap<>();

    static {
        BASE_PRIORITY.put(BASE_CONFLUENCE, Arrays.asList(BASE_CONFLUENCE, BASE_PART_DIEU, BASE_VILLEURBANNE, BASE_CUSSET));
        BASE_PRIORITY.put(BASE_PART_DIEU, Arrays.asList(BASE_PART_DIEU, BASE_VILLEURBANNE, BASE_CONFLUENCE, BASE_CUSSET));
        BASE_PRIORITY.put(BASE_VILLEURBANNE, Arrays.asList(BASE_VILLEURBANNE, BASE_PART_DIEU, BASE_CUSSET, BASE_CONFLUENCE));
        BASE_PRIORITY.put(BASE_CUSSET, Arrays.asList(BASE_CUSSET, BASE_VILLEURBANNE, BASE_PART_DIEU, BASE_CONFLUENCE));
    }

    public SimulationEngine(ApiClient api) {
        this.api = api;
        bootstrapUnits();
    }

    public synchronized void tick() {
        if (tickCounter % 3 == 0) { // log every 3 ticks to reduce noise
            logTickStatus();
        }
        tickCounter++;
        maybeSpawnIncident();
        advanceIncidents();
        advanceVehicles();
        cleanupResolvedIncidents();
        pushTelemetry();
    }

    private void bootstrapUnits() {
        List<ApiClient.UnitInfo> units = api.loadUnits();
        collectUnitTypes(units);
        createMissingUnits(units);
        materializeVehicles(units);
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "[SIM] Units loaded: {0}", vehicles.size());
        }
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
        int idx = 0;
        while (units.size() < TARGET_FLEET_SIZE) {
            String typeCode = pickUsableType(units);
            if (typeCode == null) {
                return; // cannot create without a unit type code
            }
            BaseLocation base = BASES[idx % BASES.length];
            ApiClient.UnitInfo created = api.createUnit(
                    String.format("U%02d", units.size() + 1),
                    typeCode,
                    base.name,
                    base.lat + (random.nextDouble() - 0.5) * 0.01,
                    base.lon + (random.nextDouble() - 0.5) * 0.01
            );
            if (created == null) {
                return;
            }
            units.add(created);
            idx++;
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
        for (ApiClient.UnitInfo u : units) {
            double lat = u.latitude != null ? u.latitude : CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.02;
            double lon = u.longitude != null ? u.longitude : CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.02;
            String home = (u.homeBase == null || u.homeBase.isEmpty()) ? nearestBaseName(lat, lon) : u.homeBase;
            vehicles.add(new Vehicle(u.id, u.callSign, u.unitTypeCode, home, lat, lon));
        }
    }

    private void logTickStatus() {
        List<Incident> activeIncidents = collectActiveIncidents();
        if (activeIncidents.isEmpty()) {
            return;
        }
        int active = countActiveUnits();
        logActiveIncidentSummary(activeIncidents, active);
        logIncidentsSnapshot(activeIncidents);
    }

    private List<Incident> collectActiveIncidents() {
        List<Incident> activeIncidents = new ArrayList<>();
        for (Incident inc : incidents) {
            if (inc.getEtat() != IncidentState.RESOLU) {
                activeIncidents.add(inc);
            }
        }
        return activeIncidents;
    }

    private int countActiveUnits() {
        int active = 0;
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() != null && v.getCurrentIncident().getEtat() != IncidentState.RESOLU) {
                active++;
            }
        }
        return active;
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

    private void maybeSpawnIncident() {
        Instant now = Instant.now();
        if (!firstIncidentSpawned) {
            spawnIncident();
            lastIncidentAt = now;
            firstIncidentSpawned = true;
            return;
        }
        if (now.getEpochSecond() - lastIncidentAt.getEpochSecond() >= 60) {
            spawnIncident();
            lastIncidentAt = now;
        }
    }

    private void spawnIncident() {
        IncidentType type = IncidentType.values()[random.nextInt(IncidentType.values().length)];
        double lat = CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.05;
        double lon = CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.05;
        int gravite = 1 + random.nextInt(5);
        createIncident(type, lat, lon, gravite);
    }

    private void createIncident(IncidentType type, double lat, double lon, int gravite) {
        int number = ++incidentSequence;

        String eventId = api.createEvent(type, number, lat, lon, gravite);
        if (eventId == null) {
            return;
        }

        UUID uuid;
        try {
            uuid = java.util.UUID.fromString(eventId);
        } catch (Exception e) {
            LOG.warning("Failed to parse eventId as UUID: " + eventId);
            return;
        }

        Incident incident = new Incident(uuid, number, type, lat, lon, gravite);
        incident.setEventId(eventId);
        incidents.add(incident);

        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "\n{0}{1}: {2} | Type: {3} | G: {4} | Area: {5} | Loc: ({6})",
                    new Object[]{
                            SIM_INCIDENT_PREFIX,
                        incident.getNumber(),
                        incident.getId(),
                            type,
                            gravite,
                            nearestBaseName(lat, lon),
                            String.format(Locale.US, "%.5f, %.5f", lat, lon)
                    });
        }

        String interventionId = api.createIntervention(eventId, gravite);
        if (interventionId != null) {
            incident.setInterventionId(interventionId);
            String ts = INTERVENTION_TS.format(Instant.now());
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "[SIM] Intervention created at {0} for incident {1} in {2}",
                        new Object[]{ts, incident.getNumber(), nearestBaseName(lat, lon)});
            }
            dispatchUnits(interventionId, incident);
        }
    }

    private void dispatchUnits(String interventionId, Incident incident) {
        String incidentArea = nearestBaseName(incident.getLat(), incident.getLon());
        List<Vehicle> availableVehicles = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.getEtat() == VehicleState.DISPONIBLE) {
                availableVehicles.add(v);
            }
        }

        if (availableVehicles.isEmpty()) {
            LOG.log(Level.INFO, "[SIM] No available units for incident {0}", incident.getId());
            return;
        }

        availableVehicles.sort((a, b) -> {
            int pa = priorityIndex(incidentArea, a.getHomeBase());
            int pb = priorityIndex(incidentArea, b.getHomeBase());
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            return Double.compare(
                    distance(a.getLat(), a.getLon(), incident.getLat(), incident.getLon()),
                    distance(b.getLat(), b.getLon(), incident.getLat(), incident.getLon())
            );
        });

        int needed = Math.min(incident.getGravite(), availableVehicles.size());
        for (int i = 0; i < needed; i++) {
            Vehicle v = availableVehicles.get(i);
            v.setCurrentIncident(incident);
            v.setEtat(VehicleState.EN_ROUTE);
            v.setAssignmentId(api.assignUnit(interventionId, v.getUnitId(), "unit"));
            v.setLastUpdate(Instant.now());
            v.setEnRouteSince(v.getLastUpdate());
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "[SIM] Assign {0} (from {1}) -> incident {2} (assignment {3})",
                        new Object[]{v.getCallSign(), nvl(v.getHomeBase(), "N/A"), incident.getNumber(), v.getAssignmentId()});
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (incident.getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
            api.updateInterventionStatus(incident.getInterventionId(), INTERVENTION_STATUS_EN_ROUTE);
        }
    }

    private void advanceIncidents() {
        Instant now = Instant.now();
        for (Incident inc : incidents) {
            if (inc.getEtat() == IncidentState.NOUVEAU && hasArrivedUnit(inc)) {
                inc.setEtat(IncidentState.EN_COURS);
                inc.setLastUpdate(now);
                if (PATCH_EVENT_STATUS) {
                    api.updateEventStatus(inc.getEventId(), EVENT_STATUS_ACK);
                }
                if (inc.getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
                    api.updateInterventionStatus(inc.getInterventionId(), INTERVENTION_STATUS_ON_SITE);
                }
            }
            if (inc.getEtat() == IncidentState.EN_COURS && now.getEpochSecond() - inc.getCreatedAt().getEpochSecond() > INCIDENT_RESOLVE_SECONDS) {
                resolveIncident(inc, "auto-resolve");
            }
        }
    }

    private boolean hasArrivedUnit(Incident inc) {
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == inc && v.getEtat() == VehicleState.SUR_PLACE) {
                return true;
            }
        }
        return false;
    }

    private void resolveIncident(Incident inc, String reason) {
        if (inc.getEtat() == IncidentState.RESOLU) {
            return;
        }
        inc.setEtat(IncidentState.RESOLU);
        inc.setLastUpdate(Instant.now());
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "{0}{1} resolved ({2})", new Object[]{SIM_INCIDENT_PREFIX, inc.getNumber(), reason});
        }
        if (PATCH_EVENT_STATUS) {
            api.updateEventStatus(inc.getEventId(), EVENT_STATUS_CLOSED);
        }
        if (inc.getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
            api.updateInterventionStatus(inc.getInterventionId(), INTERVENTION_STATUS_COMPLETED);
        }
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == inc) {
                v.setEtat(VehicleState.RETOUR);
                v.setLastUpdate(Instant.now());
                v.setReturnSince(v.getLastUpdate());
                if (PATCH_ASSIGNMENT_STATUS) {
                    api.updateAssignmentStatus(v.getAssignmentId(), ASSIGNMENT_STATUS_RELEASED);
                }
            }
        }
        printIncidentStatusLine(inc);
    }

    private void advanceVehicles() {
        for (Vehicle v : orderedVehiclesByIncident()) {
            switch (v.getEtat()) {
                case DISPONIBLE:
                    break;
                case EN_ROUTE:
                    handleEnRoute(v);
                    break;
                case SUR_PLACE:
                    handleSurPlace(v);
                    break;
                case RETOUR:
                    handleReturn(v);
                    break;
            }
        }
    }

    private void handleEnRoute(Vehicle v) {
        if (v.getCurrentIncident() == null) {
            return;
        }
        moveTowards(v, v.getCurrentIncident().getLat(), v.getCurrentIncident().getLon(), 0.02);
        v.setLastUpdate(Instant.now());
        long travelSeconds = v.getEnRouteSince() == null ? Long.MAX_VALUE :
                Instant.now().getEpochSecond() - v.getEnRouteSince().getEpochSecond();
        if (travelSeconds >= MIN_TRAVEL_SECONDS) {
            v.setEtat(VehicleState.SUR_PLACE);
            v.setLastUpdate(Instant.now());
            v.setEnRouteSince(null);
            if (PATCH_ASSIGNMENT_STATUS) {
                api.updateAssignmentStatus(v.getAssignmentId(), ASSIGNMENT_STATUS_ARRIVED);
            }
            printIncidentStatusLine(v.getCurrentIncident());
            if (v.getCurrentIncident().getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
                api.updateInterventionStatus(v.getCurrentIncident().getInterventionId(), INTERVENTION_STATUS_ON_SITE);
            }
        }
    }

    private void handleSurPlace(Vehicle v) {
        if (v.getCurrentIncident() != null && v.getCurrentIncident().getEtat() == IncidentState.RESOLU) {
            v.setEtat(VehicleState.RETOUR);
            v.setReturnSince(Instant.now());
            v.setLastUpdate(v.getReturnSince());
        }
    }

    private void handleReturn(Vehicle v) {
        moveTowards(v, CITY_CENTER_LAT, CITY_CENTER_LON, 0.02);
        v.setLastUpdate(Instant.now());
        long returnSeconds = v.getReturnSince() == null ? Long.MAX_VALUE :
                Instant.now().getEpochSecond() - v.getReturnSince().getEpochSecond();
        if (returnSeconds >= RETURN_SECONDS) {
            v.setEtat(VehicleState.DISPONIBLE);
            v.setCurrentIncident(null);
            v.setAssignmentId(null);
            v.setEnRouteSince(null);
            v.setReturnSince(null);
            v.setLastUpdate(Instant.now());
            LOG.log(Level.INFO, "[SIM] {0} back available", v.getCallSign());
        }
    }

    private void cleanupResolvedIncidents() {
        incidents.removeIf(inc -> inc.getEtat() == IncidentState.RESOLU && !hasAnyVehicleOnIncident(inc));
    }

    private boolean hasAnyVehicleOnIncident(Incident incident) {
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == incident) {
                return true;
            }
        }
        return false;
    }

    private void pushTelemetry() {
        for (Vehicle v : vehicles) {
            api.updateUnitStatus(v.getUnitId(), mapVehicleState(v.getEtat()));
            api.updateUnitLocation(v.getUnitId(), v.getLat(), v.getLon(), v.getLastUpdate());
        }
        for (Incident inc : incidents) {
            if (inc.getEventId() != null) {
                api.logHeartbeat(inc.getEventId());
            }
        }
    }

    private String mapVehicleState(VehicleState state) {
        switch (state) {
            case DISPONIBLE:
                return UNIT_STATUS_AVAILABLE;
            case EN_ROUTE:
                return UNIT_STATUS_EN_ROUTE;
            case SUR_PLACE:
                return UNIT_STATUS_ON_SITE;
            case RETOUR:
                return UNIT_STATUS_EN_ROUTE;
            default:
                return UNIT_STATUS_AVAILABLE;
        }
    }

    private List<Vehicle> orderedVehiclesByIncident() {
        List<Vehicle> ordered = new ArrayList<>(vehicles);
        ordered.sort((a, b) -> {
            if (a.getCurrentIncident() == null && b.getCurrentIncident() == null) return 0;
            if (a.getCurrentIncident() == null) return 1;
            if (b.getCurrentIncident() == null) return -1;
            return Integer.compare(a.getCurrentIncident().getNumber(), b.getCurrentIncident().getNumber());
        });
        return ordered;
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
            line.append(SIM_INCIDENT_PREFIX).append(incident.getNumber()).append(" - ").append(area).append(" : ");
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

    private void printIncidentStatusLine(Incident incident) {
        List<Vehicle> involved = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == incident) {
                involved.add(v);
            }
        }
        if (involved.isEmpty()) {
            return;
        }
        involved.sort((a, b) -> a.getCallSign().compareToIgnoreCase(b.getCallSign()));
        String area = nearestBaseName(incident.getLat(), incident.getLon());
        StringBuilder sb = new StringBuilder();
        sb.append(SIM_INCIDENT_PREFIX).append(incident.getNumber()).append(" - ").append(area).append(" : ");
        for (int i = 0; i < involved.size(); i++) {
            Vehicle v = involved.get(i);
            sb.append(v.getCallSign()).append(" [").append(readableState(v.getEtat())).append("]");
            if (i < involved.size() - 1) {
                sb.append(", ");
            }
        }
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, sb.toString());
        }
    }

    private String readableState(VehicleState state) {
        switch (state) {
            case DISPONIBLE:
                return UNIT_STATUS_AVAILABLE;
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

    private void moveTowards(Vehicle v, double targetLat, double targetLon, double step) {
        double dLat = targetLat - v.getLat();
        double dLon = targetLon - v.getLon();
        double len = Math.sqrt(dLat * dLat + dLon * dLon);
        if (len < 1e-6) {
            return;
        }
        v.setLat(v.getLat() + (dLat / len) * step);
        v.setLon(v.getLon() + (dLon / len) * step);
    }

    private int priorityIndex(String incidentArea, String baseName) {
        List<String> order = BASE_PRIORITY.get(incidentArea);
        if (order == null) {
            order = Collections.emptyList();
        }
        int idx = order.indexOf(baseName);
        if (idx == -1) {
            return order.size() + 1;
        }
        return idx;
    }

    private String nearestBaseName(double lat, double lon) {
        String name = BASE_PART_DIEU;
        double best = Double.MAX_VALUE;
        for (BaseLocation b : BASES) {
            double d = Math.pow(lat - b.lat, 2) + Math.pow(lon - b.lon, 2);
            if (d < best) {
                best = d;
                name = b.name;
            }
        }
        return name;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private String nvl(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
}

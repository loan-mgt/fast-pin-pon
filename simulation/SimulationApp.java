import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple simulator:
 * - Spawns random incidents, posts them as events to the Fast Pin Pon API.
 * - Creates an intervention for each event, assigns available units, and drives their lifecycle.
 * - Periodically pushes unit status/location and event heartbeats.
 */
public class SimulationApp {

    public static void main(String[] args) {
        String apiBaseUrl = "http://localhost:8081";
        ApiClient api = new ApiClient(apiBaseUrl);
        SimulationEngine engine = new SimulationEngine(api);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                engine.tick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 3, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));
    }

    // ===== Enums =====
    enum IncidentType { FEU, ACCIDENT, INONDATION }
    enum IncidentState { NOUVEAU, EN_COURS, RESOLU }
    enum VehicleState { DISPONIBLE, EN_ROUTE, SUR_PLACE, RETOUR }

    // ===== Models =====
    static class Incident {

        final UUID id = UUID.randomUUID();
        final int number;
        final Instant createdAt = Instant.now();
        IncidentType type;
        double lat;
        double lon;
        int gravite; // 1..5
        IncidentState etat = IncidentState.NOUVEAU;
        Instant lastUpdate = createdAt;
        String eventId;
        String interventionId;

        Incident(int number, IncidentType type, double lat, double lon, int gravite) {
            this.number = number;
            this.type = type;
            this.lat = lat;
            this.lon = lon;
            this.gravite = Math.max(1, Math.min(5, gravite));
        }
    }

    static class Vehicle {

        final String unitId;     // API unit id
        final String callSign;   // API call_sign
        String unitTypeCode;
        String homeBase;
        double lat;
        double lon;
        VehicleState etat = VehicleState.DISPONIBLE;
        Incident currentIncident;
        String assignmentId;
        Instant lastUpdate = Instant.now();
        Instant enRouteSince;
        Instant returnSince;

        Vehicle(String unitId, String callSign, String unitTypeCode, String homeBase, double lat, double lon) {
            this.unitId = unitId;
            this.callSign = callSign;
            this.unitTypeCode = unitTypeCode;
            this.homeBase = homeBase;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static class BaseLocation {
        final String name;
        final double lat;
        final double lon;

        BaseLocation(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    // ===== Simulation engine =====
    static class SimulationEngine {
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

        // Backend status constants to avoid enum mismatches
        private static final String EVENT_STATUS_ACK = "acknowledged";
        private static final String EVENT_STATUS_CLOSED = "closed";
        private static final String ASSIGNMENT_STATUS_ARRIVED = "arrived";
        private static final String ASSIGNMENT_STATUS_RELEASED = "released";
        private static final String INTERVENTION_STATUS_EN_ROUTE = "en_route";
        private static final String INTERVENTION_STATUS_ON_SITE = "on_site";
        private static final String INTERVENTION_STATUS_COMPLETED = "completed";
        // Using the API's current accepted unit statuses (avoid 400 from validator)
        private static final String UNIT_STATUS_AVAILABLE = "available";
        private static final String UNIT_STATUS_EN_ROUTE = "en_route";
        private static final String UNIT_STATUS_ON_SITE = "on_site";
        // Disable assignment status patches to avoid backend enum/type issues
        private static final boolean PATCH_ASSIGNMENT_STATUS = true;
        private static final boolean PATCH_INTERVENTION_STATUS = true;
        private static final boolean PATCH_EVENT_STATUS = true;

        private static final DateTimeFormatter INTERVENTION_TS =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        private static final double CITY_CENTER_LAT = 45.75;
        private static final double CITY_CENTER_LON = 4.85;
        private static final int TARGET_FLEET_SIZE = 35;

        private static final BaseLocation[] BASES = new BaseLocation[]{
                new BaseLocation("Villeurbanne", 45.7719, 4.8902),
                new BaseLocation("Lyon Confluence", 45.7421, 4.8158),
                new BaseLocation("Lyon Part-Dieu", 45.7601, 4.8590),
                new BaseLocation("Cusset", 45.7744, 4.8957),
        };

        private static final Map<String, List<String>> BASE_PRIORITY = Map.of(
                "Lyon Confluence", List.of("Lyon Confluence", "Lyon Part-Dieu", "Villeurbanne", "Cusset"),
                "Lyon Part-Dieu", List.of("Lyon Part-Dieu", "Villeurbanne", "Lyon Confluence", "Cusset"),
                "Villeurbanne", List.of("Villeurbanne", "Lyon Part-Dieu", "Cusset", "Lyon Confluence"),
                "Cusset", List.of("Cusset", "Villeurbanne", "Lyon Part-Dieu", "Lyon Confluence")
        );

        SimulationEngine(ApiClient api) {
            this.api = api;
            bootstrapUnits();
        }

        private String nearestBaseName(double lat, double lon) {
            String name = "Lyon";
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

        private void bootstrapUnits() {
            List<ApiClient.UnitInfo> units = api.loadUnits();
            if (api.unitTypeCodes.isEmpty()) {
                for (ApiClient.UnitInfo u : units) {
                    if (u.unitTypeCode != null && !u.unitTypeCode.isEmpty() && !api.unitTypeCodes.contains(u.unitTypeCode)) {
                        api.unitTypeCodes.add(u.unitTypeCode);
                    }
                }
            }

            int idx = 0;
            while (units.size() < TARGET_FLEET_SIZE) {
                String typeCode = api.pickUnitType();
                if (typeCode == null && !units.isEmpty()) {
                    typeCode = units.get(0).unitTypeCode;
                }
                if (typeCode == null) {
                    break; // cannot create without a unit type code
                }
                String callSign = String.format("U%02d", units.size() + 1);
                BaseLocation base = BASES[idx % BASES.length];
                double lat = base.lat + (random.nextDouble() - 0.5) * 0.01;
                double lon = base.lon + (random.nextDouble() - 0.5) * 0.01;
                ApiClient.UnitInfo created = api.createUnit(callSign, typeCode, base.name, lat, lon);
                if (created == null) {
                    break;
                }
                units.add(created);
                idx++;
            }

            for (ApiClient.UnitInfo u : units) {
                double lat = u.latitude != null ? u.latitude : CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.02;
                double lon = u.longitude != null ? u.longitude : CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.02;
                String home = u.homeBase;
                if (home == null || home.isEmpty()) {
                    home = nearestBaseName(lat, lon);
                }
                vehicles.add(new Vehicle(u.id, u.callSign, u.unitTypeCode, home, lat, lon));
            }
            System.out.println("[SIM] Units loaded: " + vehicles.size());
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

        private void logTickStatus() {
            int active = 0;
            List<Incident> activeIncidents = new ArrayList<>();
            for (Incident inc : incidents) {
                if (inc.etat != IncidentState.RESOLU) {
                    activeIncidents.add(inc);
                }
            }
            for (Vehicle v : vehicles) {
                if (v.currentIncident != null && v.currentIncident.etat != IncidentState.RESOLU) {
                    active++;
                }
            }
            if (!activeIncidents.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("[SIM] Treating incident : ");
                for (int i = 0; i < activeIncidents.size(); i++) {
                    sb.append("{").append(activeIncidents.get(i).number).append("}");
                    if (i < activeIncidents.size() - 1) {
                        sb.append(",");
                    }
                }
                sb.append(" | Active units = ").append(active);
                System.out.println(sb.toString());
                logIncidentsSnapshot(activeIncidents);
            }
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
            Incident incident = new Incident(++incidentSequence, type, lat, lon, gravite);
            incidents.add(incident);
            System.out.println("\n[SIM] Incident " + incident.number + ": " + incident.id + " | Type: " + type + " | G: " + gravite +
                    " | Area: " + nearestBaseName(lat, lon) + " | Loc: (" + String.format(Locale.US, "%.5f, %.5f", lat, lon) + ")");

            String eventId = api.createEvent(incident);
            if (eventId == null) {
                return;
            }
            incident.eventId = eventId;

            String interventionId = api.createIntervention(eventId, gravite);
            if (interventionId != null) {
                incident.interventionId = interventionId;
                String ts = INTERVENTION_TS.format(Instant.now());
                System.out.println("[SIM] Intervention created at " + ts + " for incident " + incident.number +
                        " in " + nearestBaseName(lat, lon));
                dispatchUnits(interventionId, incident);
            }
        }

        private void dispatchUnits(String interventionId, Incident incident) {
            String incidentArea = nearestBaseName(incident.lat, incident.lon);
            List<Vehicle> availableVehicles = new ArrayList<>();
            for (Vehicle v : vehicles) {
                if (v.etat == VehicleState.DISPONIBLE) {
                    availableVehicles.add(v);
                }
            }

            if (availableVehicles.isEmpty()) {
                System.out.println("[SIM] No available units for incident " + incident.id);
                return;
            }

            availableVehicles.sort((a, b) -> {
                int pa = priorityIndex(incidentArea, a.homeBase);
                int pb = priorityIndex(incidentArea, b.homeBase);
                if (pa != pb) {
                    return Integer.compare(pa, pb);
                }
                return Double.compare(
                        distance(a.lat, a.lon, incident.lat, incident.lon),
                        distance(b.lat, b.lon, incident.lat, incident.lon)
                );
            });

            int needed = Math.min(incident.gravite, availableVehicles.size()); // assign as many as severity, capped by availability
            for (int i = 0; i < needed; i++) {
                Vehicle v = availableVehicles.get(i);
                v.currentIncident = incident;
                v.etat = VehicleState.EN_ROUTE;
                v.assignmentId = api.assignUnit(interventionId, v.unitId, "unit");
                v.lastUpdate = Instant.now();
                v.enRouteSince = v.lastUpdate;
                System.out.println("[SIM] Assign " + v.callSign + " (from " + nvl(v.homeBase, "N/A") + ") -> incident " + incident.number +
                        " (assignment " + v.assignmentId + ")");
                // small pause to simulate sequential decision-making
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (incident.interventionId != null && PATCH_INTERVENTION_STATUS) {
                api.updateInterventionStatus(incident.interventionId, INTERVENTION_STATUS_EN_ROUTE);
            }
        }

        private String nvl(String value, String defaultValue) {
            return (value == null || value.isEmpty()) ? defaultValue : value;
        }

        private int priorityIndex(String incidentArea, String baseName) {
            List<String> order = BASE_PRIORITY.getOrDefault(incidentArea, List.of());
            int idx = order.indexOf(baseName);
            if (idx == -1) {
                // fallback: use distance to incident area base
                return order.size() + 1;
            }
            return idx;
        }

        private void advanceIncidents() {
            Instant now = Instant.now();
            for (Incident inc : incidents) {
                if (inc.etat == IncidentState.NOUVEAU && hasArrivedUnit(inc)) {
                    inc.etat = IncidentState.EN_COURS;
                    inc.lastUpdate = now;
                    if (PATCH_EVENT_STATUS) {
                        api.updateEventStatus(inc.eventId, EVENT_STATUS_ACK);
                    }
                    if (inc.interventionId != null && PATCH_INTERVENTION_STATUS) {
                        api.updateInterventionStatus(inc.interventionId, INTERVENTION_STATUS_ON_SITE);
                    }
                }
                if (inc.etat == IncidentState.EN_COURS && now.getEpochSecond() - inc.createdAt.getEpochSecond() > INCIDENT_RESOLVE_SECONDS) {
                    resolveIncident(inc, "auto-resolve");
                }
            }
        }

        private boolean hasArrivedUnit(Incident inc) {
            for (Vehicle v : vehicles) {
                if (v.currentIncident == inc && v.etat == VehicleState.SUR_PLACE) {
                    return true;
                }
            }
            return false;
        }

        private void resolveIncident(Incident inc, String reason) {
            if (inc.etat == IncidentState.RESOLU) {
                return;
            }
            inc.etat = IncidentState.RESOLU;
            inc.lastUpdate = Instant.now();
            System.out.println("[SIM] Incident " + inc.number + " resolved (" + reason + ")");
            if (PATCH_EVENT_STATUS) {
                api.updateEventStatus(inc.eventId, EVENT_STATUS_CLOSED);
            }
            if (inc.interventionId != null && PATCH_INTERVENTION_STATUS) {
                api.updateInterventionStatus(inc.interventionId, INTERVENTION_STATUS_COMPLETED);
            }
            for (Vehicle v : vehicles) {
                if (v.currentIncident == inc) {
                    v.etat = VehicleState.RETOUR;
                    v.lastUpdate = Instant.now();
                    v.returnSince = v.lastUpdate;
                    if (PATCH_ASSIGNMENT_STATUS) {
                        api.updateAssignmentStatus(v.assignmentId, ASSIGNMENT_STATUS_RELEASED);
                    }
                }
            }
            printIncidentStatusLine(inc);
        }

        private void advanceVehicles() {
            // Process vehicles by incident number to favor older incidents first
            List<Vehicle> ordered = new ArrayList<>(vehicles);
            ordered.sort((a, b) -> {
                if (a.currentIncident == null && b.currentIncident == null) return 0;
                if (a.currentIncident == null) return 1;
                if (b.currentIncident == null) return -1;
                return Integer.compare(a.currentIncident.number, b.currentIncident.number);
            });
            for (Vehicle v : ordered) {
                switch (v.etat) {
                    case DISPONIBLE:
                        break;
                    case EN_ROUTE:
                        if (v.currentIncident != null) {
                            moveTowards(v, v.currentIncident.lat, v.currentIncident.lon, 0.02); // faster travel for simulation
                            v.lastUpdate = Instant.now();
                            long travelSeconds = v.enRouteSince == null ? Long.MAX_VALUE :
                                    Instant.now().getEpochSecond() - v.enRouteSince.getEpochSecond();
                            if (travelSeconds >= MIN_TRAVEL_SECONDS) { // force arrival at exactly 10s
                                v.etat = VehicleState.SUR_PLACE;
                                v.lastUpdate = Instant.now();
                                v.enRouteSince = null;
                                if (PATCH_ASSIGNMENT_STATUS) {
                                    api.updateAssignmentStatus(v.assignmentId, ASSIGNMENT_STATUS_ARRIVED);
                                }
                                printIncidentStatusLine(v.currentIncident);
                                if (v.currentIncident.interventionId != null && PATCH_INTERVENTION_STATUS) {
                                    api.updateInterventionStatus(v.currentIncident.interventionId, INTERVENTION_STATUS_ON_SITE);
                                }
                            }
                        }
                        break;
                    case SUR_PLACE:
                        if (v.currentIncident != null && v.currentIncident.etat == IncidentState.RESOLU) {
                            v.etat = VehicleState.RETOUR;
                            v.lastUpdate = Instant.now();
                        }
                        break;
                    case RETOUR:
                        moveTowards(v, CITY_CENTER_LAT, CITY_CENTER_LON, 0.02);
                        v.lastUpdate = Instant.now();
                        long returnSeconds = v.returnSince == null ? Long.MAX_VALUE :
                                Instant.now().getEpochSecond() - v.returnSince.getEpochSecond();
                        if (returnSeconds >= RETURN_SECONDS) {
                            v.etat = VehicleState.DISPONIBLE;
                            v.currentIncident = null;
                            v.assignmentId = null;
                            v.enRouteSince = null;
                            v.returnSince = null;
                            v.lastUpdate = Instant.now();
                            System.out.println("[SIM] " + v.callSign + " back available");
                        }
                        break;
                }
            }
        }

        private void pushTelemetry() {
            for (Vehicle v : vehicles) {
                api.updateUnitStatus(v.unitId, mapVehicleState(v.etat));
                api.updateUnitLocation(v.unitId, v.lat, v.lon, v.lastUpdate);
            }
            for (Incident inc : incidents) {
                if (inc.eventId != null) {
                    api.logHeartbeat(inc.eventId);
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

        private void logIncidentsSnapshot(List<Incident> scope) {
            for (Incident incident : scope) {
                List<Vehicle> involved = new ArrayList<>();
                for (Vehicle v : vehicles) {
                    if (v.currentIncident == incident) {
                        involved.add(v);
                    }
                }
                if (involved.isEmpty()) {
                    continue;
                }
                involved.sort((a, b) -> a.callSign.compareToIgnoreCase(b.callSign));
                String area = nearestBaseName(incident.lat, incident.lon);
                StringBuilder line = new StringBuilder();
                line.append("[SIM] Incident ").append(incident.number).append(" - ").append(area).append(" : ");
                for (int i = 0; i < involved.size(); i++) {
                    Vehicle v = involved.get(i);
                    line.append(v.callSign).append(" [").append(readableState(v.etat)).append("]");
                    if (i < involved.size() - 1) {
                        line.append(", ");
                    }
                }
                System.out.println(line.toString());
            }
        }

        private void printIncidentStatusLine(Incident incident) {
            List<Vehicle> involved = new ArrayList<>();
            for (Vehicle v : vehicles) {
                if (v.currentIncident == incident) {
                    involved.add(v);
                }
            }
            if (involved.isEmpty()) {
                return;
            }
            involved.sort((a, b) -> a.callSign.compareToIgnoreCase(b.callSign));
            String area = nearestBaseName(incident.lat, incident.lon);
            StringBuilder sb = new StringBuilder();
            sb.append("[SIM] Incident ").append(incident.number).append(" - ").append(area).append(" : ");
            for (int i = 0; i < involved.size(); i++) {
                Vehicle v = involved.get(i);
                sb.append(v.callSign).append(" [").append(readableState(v.etat)).append("]");
                if (i < involved.size() - 1) {
                    sb.append(", ");
                }
            }
            System.out.println(sb.toString());
        }

        private String readableState(VehicleState state) {
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

        private void cleanupResolvedIncidents() {
            incidents.removeIf(inc -> inc.etat == IncidentState.RESOLU && !hasAnyVehicleOnIncident(inc));
        }

        private boolean hasAnyVehicleOnIncident(Incident incident) {
            for (Vehicle v : vehicles) {
                if (v.currentIncident == incident) {
                    return true;
                }
            }
            return false;
        }


        private void moveTowards(Vehicle v, double targetLat, double targetLon, double step) {
            double dLat = targetLat - v.lat;
            double dLon = targetLon - v.lon;
            double len = Math.sqrt(dLat * dLat + dLon * dLon);
            if (len < 1e-6) {
                return;
            }
            v.lat += (dLat / len) * step;
            v.lon += (dLon / len) * step;
        }

        private double distance(double lat1, double lon1, double lat2, double lon2) {
            double dLat = lat2 - lat1;
            double dLon = lon2 - lon1;
            return Math.sqrt(dLat * dLat + dLon * dLon);
        }
    }

    // ===== API Client =====
    static class ApiClient {
        private final String baseUrl;
        private final HttpClient http = HttpClient.newHttpClient();
        private final DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;
        private final Random random = new Random();
        private final List<String> eventTypeCodes = new ArrayList<>();
        final List<String> unitTypeCodes = new ArrayList<>();

        ApiClient(String baseUrlRaw) {
            this.baseUrl = baseUrlRaw.endsWith("/") ? baseUrlRaw.substring(0, baseUrlRaw.length() - 1) : baseUrlRaw;
            loadEventTypes();
            loadUnitTypes();
        }

        static class UnitInfo {
            final String id;
            final String callSign;
            final String homeBase;
            final String unitTypeCode;
            final String status;
            final Double latitude;
            final Double longitude;

            UnitInfo(String id, String callSign, String homeBase, String unitTypeCode, String status, Double latitude, Double longitude) {
                this.id = id;
                this.callSign = callSign;
                this.homeBase = homeBase;
                this.unitTypeCode = unitTypeCode;
                this.status = status;
                this.latitude = latitude;
                this.longitude = longitude;
            }
        }

        List<UnitInfo> loadUnits() {
            List<UnitInfo> res = new ArrayList<>();
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/units"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() >= 400) {
                    System.err.println("[API] GET /v1/units -> " + resp.statusCode() + " body=" + resp.body());
                    return res;
                }

                String body = resp.body();
                int idx = 0;
                while (true) {
                    int idPos = body.indexOf("\"id\"", idx);
                    if (idPos == -1) {
                        break;
                    }
                    String id = extractString(body, "\"id\"", idPos);
                    String callSign = extractNearest(body, "\"call_sign\"", idPos);
                    String homeBase = extractNearest(body, "\"home_base\"", idPos);
                    String typeCode = extractNearest(body, "\"unit_type_code\"", idPos);
                    String status = extractNearest(body, "\"status\"", idPos);
                    Double lat = extractDoubleNearest(body, "\"latitude\"", idPos);
                    Double lon = extractDoubleNearest(body, "\"longitude\"", idPos);
                    if (id != null) {
                        res.add(new UnitInfo(id, nvl(callSign, id), nvl(homeBase, ""), nvl(typeCode, ""), nvl(status, ""), lat, lon));
                    }
                    idx = idPos + 4;
                }
            } catch (Exception e) {
                System.err.println("[API] GET /v1/units error: " + e.getMessage());
            }
            return res;
        }

        String createEvent(Incident inc) {
            String typeCode = pickEventType();
            if (typeCode == null) {
                System.err.println("[API] No event type code available; cannot create event.");
                return null;
            }
            String title = "SIM-" + inc.type + "-" + inc.id.toString().substring(0, 8);
            String json = String.format(Locale.US,
                    "{ \"title\": \"%s\", \"event_type_code\": \"%s\", \"latitude\": %.6f, \"longitude\": %.6f, \"severity\": %d, \"report_source\": \"simulation\" }",
                    escape(title), escape(typeCode), inc.lat, inc.lon, inc.gravite);

            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/events"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(), HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() >= 400) {
                    System.err.println("[API] POST /v1/events -> " + resp.statusCode() + " body=" + resp.body());
                    return null;
                }
                String id = extractString(resp.body(), "\"id\"", 0);
                System.out.println("[API] Event created (id=" + id + ")");
                return id;
            } catch (Exception e) {
                System.err.println("[API] POST /v1/events error: " + e.getMessage());
                return null;
            }
        }

        String createIntervention(String eventId, int priority) {
            String json = String.format(Locale.US,
                    "{ \"event_id\": \"%s\", \"priority\": %d, \"decision_mode\": \"auto_suggested\" }",
                    escape(eventId), priority);
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/interventions"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] POST /v1/interventions -> " + resp.statusCode() + " body=" + resp.body());
                    return null;
                }
                String id = extractString(resp.body(), "\"id\"", 0);
                return id;
            } catch (Exception e) {
                System.err.println("[API] POST /v1/interventions error: " + e.getMessage());
                return null;
            }
        }

        String assignUnit(String interventionId, String unitId, String role) {
            String json = String.format(Locale.US,
                    "{ \"unit_id\": \"%s\", \"role\": \"%s\" }",
                    escape(unitId), escape(role));
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/interventions/" + interventionId + "/assignments"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] POST assignment -> " + resp.statusCode() + " body=" + resp.body());
                    return null;
                }
                return extractString(resp.body(), "\"id\"", 0);
            } catch (Exception e) {
                System.err.println("[API] POST assignment error: " + e.getMessage());
                return null;
            }
        }

        void updateAssignmentStatus(String assignmentId, String status) {
            if (assignmentId == null || assignmentId.isBlank()) {
                return;
            }
            patchJson("/v1/assignments/" + assignmentId + "/status", "{ \"status\": \"" + escape(status) + "\" }");
        }

        void updateInterventionStatus(String interventionId, String status) {
            if (interventionId == null || interventionId.isBlank()) {
                return;
            }
            patchJson("/v1/interventions/" + interventionId + "/status", "{ \"status\": \"" + escape(status) + "\" }");
        }

        void updateEventStatus(String eventId, String status) {
            if (eventId == null || eventId.isBlank()) {
                return;
            }
            patchJson("/v1/events/" + eventId + "/status", "{ \"status\": \"" + escape(status) + "\" }");
        }

        void logHeartbeat(String eventId) {
            if (eventId == null) {
                return;
            }
            postJson("/v1/events/" + eventId + "/logs", "{ \"actor\": \"simulator\", \"code\": \"heartbeat\", \"payload\": [] }");
        }

        void updateUnitStatus(String unitId, String status) {
            if (unitId == null || unitId.isBlank()) {
                return;
            }
            patchJson("/v1/units/" + unitId + "/status", "{ \"status\": \"" + escape(status) + "\" }");
        }

        void updateUnitLocation(String unitId, double lat, double lon, Instant recordedAt) {
            if (unitId == null || unitId.isBlank()) {
                return;
            }
            String json = String.format(Locale.US,
                    "{ \"latitude\": %.6f, \"longitude\": %.6f, \"recorded_at\": \"%s\" }",
                    lat, lon, iso.format(recordedAt));
            patchJson("/v1/units/" + unitId + "/location", json);
        }

        // ---- helpers ----
        private void loadEventTypes() {
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/event-types"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] GET /v1/event-types -> " + resp.statusCode() + " body=" + resp.body());
                    return;
                }
                String body = resp.body();
                int idx = 0;
                while (true) {
                    int pos = body.indexOf("\"code\"", idx);
                    if (pos == -1) {
                        break;
                    }
                    String code = extractString(body, "\"code\"", pos);
                    if (code != null) {
                        eventTypeCodes.add(code);
                    }
                    idx = pos + 6;
                }
                System.out.println("[API] Event types: " + eventTypeCodes);
            } catch (Exception e) {
                System.err.println("[API] GET /v1/event-types error: " + e.getMessage());
            }
        }

        private String pickEventType() {
            if (eventTypeCodes.isEmpty()) {
                return null;
            }
            return eventTypeCodes.get(random.nextInt(eventTypeCodes.size()));
        }

        private void loadUnitTypes() {
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/unit-types"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] GET /v1/unit-types -> " + resp.statusCode() + " body=" + resp.body());
                    return;
                }
                String body = resp.body();
                int idx = 0;
                while (true) {
                    int pos = body.indexOf("\"code\"", idx);
                    if (pos == -1) {
                        break;
                    }
                    String code = extractString(body, "\"code\"", pos);
                    if (code != null && !unitTypeCodes.contains(code)) {
                        unitTypeCodes.add(code);
                    }
                    idx = pos + 6;
                }
                System.out.println("[API] Unit types: " + unitTypeCodes);
            } catch (Exception e) {
                System.err.println("[API] GET /v1/unit-types error: " + e.getMessage());
            }
        }

        String pickUnitType() {
            if (unitTypeCodes.isEmpty()) {
                return null;
            }
            return unitTypeCodes.get(random.nextInt(unitTypeCodes.size()));
        }

        UnitInfo createUnit(String callSign, String unitTypeCode, String homeBase, double lat, double lon) {
            String json = String.format(Locale.US,
                    "{ \"call_sign\": \"%s\", \"unit_type_code\": \"%s\", \"home_base\": \"%s\", \"status\": \"available\", \"latitude\": %.6f, \"longitude\": %.6f }",
                    escape(callSign), escape(unitTypeCode), escape(homeBase), lat, lon);
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/units"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] POST /v1/units -> " + resp.statusCode() + " body=" + resp.body());
                    return null;
                }
                String id = extractString(resp.body(), "\"id\"", 0);
                if (id == null) {
                    return null;
                }
                return new UnitInfo(id, callSign, homeBase, unitTypeCode, "available", lat, lon);
            } catch (Exception e) {
                System.err.println("[API] POST /v1/units error: " + e.getMessage());
                return null;
            }
        }

        private void postJson(String path, String body) {
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] POST " + path + " -> " + resp.statusCode() + " body=" + resp.body());
                }
            } catch (Exception e) {
                System.err.println("[API] POST " + path + " error: " + e.getMessage());
            }
        }

        private void patchJson(String path, String body) {
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] PATCH " + path + " -> " + resp.statusCode() + " body=" + resp.body() +
                            " payload=" + body);
                }
            } catch (Exception e) {
                System.err.println("[API] PATCH " + path + " error: " + e.getMessage());
            }
        }

        private static String extractString(String body, String field, int from) {
            int pos = body.indexOf(field, from);
            if (pos == -1) {
                return null;
            }
            int colon = body.indexOf(":", pos);
            int q1 = body.indexOf("\"", colon);
            int q2 = body.indexOf("\"", q1 + 1);
            if (colon == -1 || q1 == -1 || q2 == -1) {
                return null;
            }
            return body.substring(q1 + 1, q2);
        }

        private static String extractNearest(String body, String field, int around) {
            int back = body.lastIndexOf(field, around);
            if (back != -1) {
                String v = extractString(body, field, back);
                if (v != null) {
                    return v;
                }
            }
            return extractString(body, field, around);
        }

        private static Double extractDoubleNearest(String body, String field, int around) {
            int back = body.lastIndexOf(field, around);
            if (back != -1) {
                Double v = extractDouble(body, field, back);
                if (v != null) {
                    return v;
                }
            }
            return extractDouble(body, field, around);
        }

        private static Double extractDouble(String body, String field, int from) {
            int pos = body.indexOf(field, from);
            if (pos == -1) {
                return null;
            }
            int colon = body.indexOf(":", pos);
            if (colon == -1) {
                return null;
            }
            int start = colon + 1;
            while (start < body.length() && (body.charAt(start) == ' ' || body.charAt(start) == '\"')) {
                start++;
            }
            int end = start;
            while (end < body.length() && "0123456789+-.eE".indexOf(body.charAt(end)) != -1) {
                end++;
            }
            if (start == end) {
                return null;
            }
            try {
                return Double.parseDouble(body.substring(start, end));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static String nvl(String v, String d) {
            return v == null ? d : v;
        }

        private static String escape(String v) {
            return v.replace("\"", "\\\"");
        }
    }
}

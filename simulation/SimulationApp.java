import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulation d'incidents et véhicules et envoi de l'état vers Fast Pin Pon API.
 * - Génération d'incidents (type, localisation, gravité, horodatage) et évolution.
 * - Simulation des positions / états des véhicules avec transmission périodique.
 */
public class SimulationApp {

    public static void main(String[] args) {
        // par défaut : Fast Pin Pon prod ; overridable via API_BASE_URL ou argument
        String apiBaseUrl = System.getenv().getOrDefault(
                "API_BASE_URL",
                "https://api.fast-pin-pon.4loop.org"
        );
        if (args.length > 0) {
            apiBaseUrl = args[0];
        }

        ApiClient apiClient = new ApiClient(apiBaseUrl);
        SimulationEngine engine = new SimulationEngine(apiClient);

        // Tick de simu périodique
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                engine.tick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);

        // Thread CLI
        Thread cli = new Thread(new CommandListener(engine), "cli-listener");
        cli.setDaemon(true);
        cli.start();

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));
    }

    // =========== ENUMS METIER ===========

    enum IncidentType {
        FEU, ACCIDENT, INONDATION
    }

    enum IncidentState {
        NOUVEAU, EN_COURS, RESOLU
    }

    enum VehicleState {
        DISPONIBLE, EN_ROUTE, SUR_PLACE, RETOUR
    }

    // =========== MODELES ===========

    static class Incident {
        final UUID id;           // ID interne simu
        IncidentType type;
        double lat;
        double lon;
        int gravite;             // 1..3
        IncidentState etat;
        final Instant createdAt;
        Instant lastUpdate;

        // ID de l'event côté Fast Pin Pon (POST /v1/events)
        String eventId;

        Incident(IncidentType type, double lat, double lon, int gravite) {
            this.id = UUID.randomUUID();
            this.type = type;
            this.lat = lat;
            this.lon = lon;
            this.gravite = gravite;
            this.etat = IncidentState.NOUVEAU;
            this.createdAt = Instant.now();
            this.lastUpdate = this.createdAt;
            this.eventId = null;
        }
    }

    static class Vehicle {
        final UUID id;           // ID interne simu
        final String matricule;  // on l'utilise comme unitID / call_sign côté API
        double lat;
        double lon;
        VehicleState etat;
        Incident currentIncident; // null si libre
        Instant lastUpdate;

        Vehicle(String matricule, double lat, double lon) {
            this.id = UUID.randomUUID();
            this.matricule = matricule;
            this.lat = lat;
            this.lon = lon;
            this.etat = VehicleState.DISPONIBLE;
            this.lastUpdate = Instant.now();
        }
    }

    // =========== MOTEUR DE SIMU ===========

    static class SimulationEngine {

        private final ApiClient api;
        private final Random random = new Random();

        private final List<Incident> incidents = new ArrayList<>();
        private final List<Vehicle> vehicles = new ArrayList<>();

        private static final double CITY_CENTER_LAT = 45.75;
        private static final double CITY_CENTER_LON = 4.85;

        SimulationEngine(ApiClient api) {
            this.api = api;
            initVehicles();
        }

        private void initVehicles() {
            vehicles.add(new Vehicle("VSAV1", CITY_CENTER_LAT + 0.01, CITY_CENTER_LON + 0.01));
            vehicles.add(new Vehicle("FPT1",  CITY_CENTER_LAT - 0.01, CITY_CENTER_LON - 0.01));
            vehicles.add(new Vehicle("VSAV2", CITY_CENTER_LAT + 0.02, CITY_CENTER_LON - 0.01));
        }

        public synchronized void tick() {
            maybeCreateIncident();
            updateIncidentsState();
            updateVehicles();
            pushStateToApi();
        }

        // -------- Incidents --------

        private void maybeCreateIncident() {
            if (random.nextDouble() < 0.2) {
                IncidentType type = IncidentType.values()[random.nextInt(IncidentType.values().length)];
                double lat = CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.05;
                double lon = CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.05;
                int gravite = 1 + random.nextInt(3);
                createIncident(type, lat, lon, gravite, "auto");
            }
        }

        synchronized Incident createIncident(IncidentType type, double lat, double lon, int gravite, String source) {
            Incident incident = new Incident(type, lat, lon, gravite);
            incidents.add(incident);
            System.out.println();
            System.out.println("[SIMU] (" + source + ") Nouvel incident : " + incident.id + " " + type + " Gravite : " + gravite);
            api.createIncident(incident);
            return incident;
        }

        synchronized boolean resolveIncident(UUID incidentId, String reason) {
            for (Incident incident : incidents) {
                if (incident.id.equals(incidentId) && incident.etat != IncidentState.RESOLU) {
                    incident.etat = IncidentState.RESOLU;
                    incident.lastUpdate = Instant.now();
                    System.out.println();
                    System.out.println("[SIMU] Incident résolu (" + reason + ") : " + incident.id);
                    api.updateIncident(incident);
                    return true;
                }
            }
            return false;
        }

        private void updateIncidentsState() {
            Instant now = Instant.now();
            for (Incident incident : incidents) {
                long ageSec = now.getEpochSecond() - incident.createdAt.getEpochSecond();

                switch (incident.etat) {
                    case NOUVEAU:
                        if (vehicles.stream().anyMatch(v -> v.currentIncident == incident)) {
                            incident.etat = IncidentState.EN_COURS;
                            incident.lastUpdate = now;
                            api.updateIncident(incident);
                        }
                        break;
                    case EN_COURS:
                        if (ageSec > 120) {
                            resolveIncident(incident.id, "auto");
                        }
                        break;
                    case RESOLU:
                        break;
                }
            }
        }

        // -------- Véhicules --------

        private void updateVehicles() {
            for (Vehicle v : vehicles) {
                switch (v.etat) {
                    case DISPONIBLE:
                        Optional<Incident> target = incidents.stream()
                                .filter(i -> i.etat != IncidentState.RESOLU)
                                .filter(i -> vehicles.stream().noneMatch(veh -> veh.currentIncident == i))
                                .findAny();
                        target.ifPresent(incident -> {
                            v.currentIncident = incident;
                            v.etat = VehicleState.EN_ROUTE;
                            v.lastUpdate = Instant.now();
                            System.out.println();
                            System.out.println("[SIMU] " + v.matricule + " envoyé sur " + incident.id);
                        });
                        break;

                    case EN_ROUTE:
                        if (v.currentIncident != null) {
                            moveTowards(v, v.currentIncident.lat, v.currentIncident.lon, 0.001);
                            if (distance(v.lat, v.lon, v.currentIncident.lat, v.currentIncident.lon) < 0.0005) {
                                v.etat = VehicleState.SUR_PLACE;
                                v.lastUpdate = Instant.now();
                                System.out.println();
                                System.out.println("[SIMU] " + v.matricule + " sur place " + v.currentIncident.id);
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
                        moveTowards(v, CITY_CENTER_LAT, CITY_CENTER_LON, 0.001);
                        if (distance(v.lat, v.lon, CITY_CENTER_LAT, CITY_CENTER_LON) < 0.0005) {
                            v.etat = VehicleState.DISPONIBLE;
                            v.currentIncident = null;
                            v.lastUpdate = Instant.now();
                            System.out.println();
                            System.out.println("[SIMU] " + v.matricule + " de retour et dispo");
                        }
                        break;
                }
            }
        }

        synchronized boolean setVehicleState(String matricule, VehicleState newState, String note) {
            for (Vehicle v : vehicles) {
                if (v.matricule.equalsIgnoreCase(matricule)) {
                    v.etat = newState;
                    v.lastUpdate = Instant.now();
                    if (newState == VehicleState.DISPONIBLE) {
                        v.currentIncident = null;
                    }
                    System.out.println();
                    System.out.println("[SIMU] Etat manuel " + v.matricule + " -> " + newState + (note.isEmpty() ? "" : " (" + note + ")"));
                    api.sendVehicleReport(v, note);
                    return true;
                }
            }
            return false;
        }

        // -------- Utilitaires --------

        private void moveTowards(Vehicle v, double targetLat, double targetLon, double step) {
            double dLat = targetLat - v.lat;
            double dLon = targetLon - v.lon;
            double length = Math.sqrt(dLat * dLat + dLon * dLon);

            if (length < 1e-6) {
                return;
            }

            v.lat += (dLat / length) * step;
            v.lon += (dLon / length) * step;
        }

        private double distance(double lat1, double lon1, double lat2, double lon2) {
            double dLat = lat2 - lat1;
            double dLon = lon2 - lon1;
            return Math.sqrt(dLat * dLat + dLon * dLon);
        }

        private void pushStateToApi() {
            for (Vehicle v : vehicles) {
                api.sendVehiclePosition(v); // status + location unit
            }
            for (Incident incident : incidents) {
                api.heartbeatIncident(incident); // log "heartbeat" sur l'event
            }
        }

        synchronized List<Incident> getIncidentsSnapshot() {
            return new ArrayList<>(incidents);
        }

        synchronized List<Vehicle> getVehiclesSnapshot() {
            return new ArrayList<>(vehicles);
        }
    }

    // =========== CLIENT HTTP VERS FAST PIN PON API ===========

    static class ApiClient {
        private final String baseUrl;
        private final HttpClient http;
        private final DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;

        // pour choisir un event_type_code aléatoire
        private final Random random = new Random();
        private final List<String> eventTypeCodes = new ArrayList<>();

        ApiClient(String baseUrl) {
            this.baseUrl = baseUrl;
            this.http = HttpClient.newHttpClient();
            loadEventTypes(); // on récupère les event types dès le départ
        }

        // ---- CHARGEMENT DYNAMIQUE DES EVENT TYPES ----

        private void loadEventTypes() {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/event-types"))
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] GET /v1/event-types -> " + resp.statusCode()
                            + " body=" + resp.body());
                    return;
                }

                String body = resp.body();
                String marker = "\"code\"";
                int idx = 0;
                while (true) {
                    int pos = body.indexOf(marker, idx);
                    if (pos == -1) break;

                    int colon = body.indexOf(":", pos);
                    if (colon == -1) break;

                    int firstQuote = body.indexOf("\"", colon);
                    int secondQuote = body.indexOf("\"", firstQuote + 1);
                    if (firstQuote == -1 || secondQuote == -1) break;

                    String code = body.substring(firstQuote + 1, secondQuote);
                    if (!code.isEmpty() && !eventTypeCodes.contains(code)) {
                        eventTypeCodes.add(code);
                    }

                    idx = secondQuote + 1;
                }

                if (eventTypeCodes.isEmpty()) {
                    System.err.println("[API] /v1/event-types a répondu mais aucun code trouvé.");
                } else {
                    System.out.println("[API] Event types chargés : " + eventTypeCodes);
                }
            } catch (Exception e) {
                System.err.println("[API] Erreur GET /v1/event-types : " + e.getMessage());
            }
        }

        private String pickRandomEventTypeCode() {
            if (eventTypeCodes.isEmpty()) {
                return null;
            }
            return eventTypeCodes.get(random.nextInt(eventTypeCodes.size()));
        }

        // ---- MAPPINGS ----

        private String mapIncidentStateToEventStatus(IncidentState state) {
            switch (state) {
                case NOUVEAU:
                    return "open";
                case EN_COURS:
                    return "acknowledged";
                case RESOLU:
                    return "closed";
                default:
                    return "open";
            }
        }

        private String mapVehicleStateToUnitStatus(VehicleState state) {
            switch (state) {
                case DISPONIBLE:
                    return "available";
                case EN_ROUTE:
                    return "en_route";
                case SUR_PLACE:
                    return "on_site";
                case RETOUR:
                    return "en_route"; // retour dépôt
                default:
                    return "available";
            }
        }

        // ---- INCIDENTS -> EVENTS ----

        void createIncident(Incident incident) {
            String eventTypeCode = pickRandomEventTypeCode();
            if (eventTypeCode == null) {
                System.err.println("[API] Aucun event_type_code disponible, event non créé.");
                return;
            }

            String title = "SIM-" + incident.type + "-" + incident.id.toString().substring(0, 8);

            String json = String.format(Locale.US,
                    "{ \"event_type_code\": \"%s\", " +
                      "\"latitude\": %.6f, " +
                      "\"longitude\": %.6f, " +
                      "\"severity\": %d, " +
                      "\"title\": \"%s\", " +
                      "\"report_source\": \"simulation\" }",
                    eventTypeCode,
                    incident.lat,
                    incident.lon,
                    Math.max(1, Math.min(5, incident.gravite)),
                    escapeJson(title)
            );

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/events"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    System.err.println("[API] POST /v1/events -> " + resp.statusCode() + " body=" + resp.body());
                    return;
                }

                String body = resp.body();
                String marker = "\"id\":\"";
                int idx = body.indexOf(marker);
                if (idx != -1) {
                    int start = idx + marker.length();
                    int end = body.indexOf("\"", start);
                    if (end > start) {
                        incident.eventId = body.substring(start, end);
                        System.out.println("[API] Event créé, eventId=" + incident.eventId
                                + " (type=" + eventTypeCode + ")");
                    }
                }
            } catch (Exception e) {
                System.err.println("[API] Erreur POST /v1/events : " + e.getMessage());
            }
        }

        void updateIncident(Incident incident) {
            if (incident.eventId == null) {
                return;
            }
            String status = mapIncidentStateToEventStatus(incident.etat);
            String json = String.format(Locale.US,
                    "{ \"status\": \"%s\" }",
                    status
            );
            patchJson("/v1/events/" + incident.eventId + "/status", json);
        }

        void heartbeatIncident(Incident incident) {
            if (incident.eventId == null) {
                return;
            }
            String json = "{ \"actor\": \"simulator\", \"code\": \"heartbeat\", \"payload\": [] }";
            postJson("/v1/events/" + incident.eventId + "/logs", json);
        }

        // ---- VEHICULES -> UNITS ----

        void sendVehiclePosition(Vehicle v) {
            String unitId = v.matricule;

            // 1) status du véhicule
            String statusJson = String.format(Locale.US,
                    "{ \"status\": \"%s\" }",
                    mapVehicleStateToUnitStatus(v.etat)
            );
            patchJson("/v1/units/" + unitId + "/status", statusJson);

            // 2) localisation
            String locJson = String.format(Locale.US,
                    "{ \"latitude\": %.6f, \"longitude\": %.6f, \"recorded_at\": \"%s\" }",
                    v.lat, v.lon, iso.format(v.lastUpdate)
            );
            patchJson("/v1/units/" + unitId + "/location", locJson);
        }

        void sendVehicleReport(Vehicle v, String note) {
            if (v.currentIncident == null || v.currentIncident.eventId == null) {
                return;
            }
            String safeNote = note == null ? "" : escapeJson(note);
            if (safeNote.length() > 120) {
                safeNote = safeNote.substring(0, 120);
            }
            String json = String.format(Locale.US,
                    "{ \"actor\": \"%s\", \"code\": \"%s\", \"payload\": [] }",
                    escapeJson(v.matricule),
                    safeNote.isEmpty() ? "status_update" : safeNote
            );
            postJson("/v1/events/" + v.currentIncident.eventId + "/logs", json);
        }

        // ---- HTTP helpers ----

        private void postJson(String path, String jsonBody) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(resp -> {
                            if (resp.statusCode() >= 400) {
                                System.err.println("[API] POST " + path + " -> " + resp.statusCode());
                            }
                        });
            } catch (Exception e) {
                System.err.println("[API] Erreur POST " + path + " : " + e.getMessage());
            }
        }

        private void patchJson(String path, String jsonBody) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(resp -> {
                            if (resp.statusCode() >= 400) {
                                System.err.println("[API] PATCH " + path + " -> " + resp.statusCode());
                            }
                        });
            } catch (Exception e) {
                System.err.println("[API] Erreur PATCH " + path + " : " + e.getMessage());
            }
        }

        private String escapeJson(String value) {
            return value.replace("\"", "\\\"");
        }
    }

    // =========== CLI ===========

    static class CommandListener implements Runnable {
        private final SimulationEngine engine;

        CommandListener(SimulationEngine engine) {
            this.engine = engine;
        }

        @Override
        public void run() {
            printHelp();
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("[CMD] ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                handleCommand(line);
            }
        }

        private void handleCommand(String line) {
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);
            try {
                switch (cmd) {
                    case "help":
                        printHelp();
                        break;
                    case "incident":
                        // incident <type> <lat> <lon> <gravite>
                        if (parts.length < 5) {
                            System.out.println("Usage: incident <FEU|ACCIDENT|INONDATION> <lat> <lon> <gravite 1-3>");
                            break;
                        }
                        IncidentType type = IncidentType.valueOf(parts[1].toUpperCase(Locale.ROOT));
                        double lat = Double.parseDouble(parts[2]);
                        double lon = Double.parseDouble(parts[3]);
                        int gravite = Integer.parseInt(parts[4]);
                        engine.createIncident(type, lat, lon, gravite, "manuel");
                        break;
                    case "resolve":
                        // resolve <incidentId>
                        if (parts.length < 2) {
                            System.out.println("Usage: resolve <incidentId>");
                            break;
                        }
                        UUID iid = UUID.fromString(parts[1]);
                        if (!engine.resolveIncident(iid, "manuel")) {
                            System.out.println("Incident introuvable: " + iid);
                        }
                        break;
                    case "etat":
                        // etat <matriculeVehicule> <DISPONIBLE|EN_ROUTE|SUR_PLACE|RETOUR> [note...]
                        if (parts.length < 3) {
                            System.out.println("Usage: etat <matricule> <DISPONIBLE|EN_ROUTE|SUR_PLACE|RETOUR> [note]");
                            break;
                        }
                        VehicleState vstate = VehicleState.valueOf(parts[2].toUpperCase(Locale.ROOT));
                        String note = (parts.length > 3) ? line.substring(line.indexOf(parts[3])) : "";
                        if (!engine.setVehicleState(parts[1], vstate, note)) {
                            System.out.println("Vehicule introuvable: " + parts[1]);
                        }
                        break;
                    case "liste":
                        printState();
                        break;
                    default:
                        System.out.println("Commande inconnue: " + cmd);
                        printHelp();
                }
            } catch (Exception e) {
                System.err.println("Erreur commande: " + e.getMessage());
            }
        }

        private void printState() {
            System.out.println("Incidents:");
            for (Incident inc : engine.getIncidentsSnapshot()) {
                System.out.println(" - " + inc.id + " | " + inc.type + " | Gravite " + inc.gravite + " | " + inc.etat + " au : (" + inc.lat + "," + inc.lon + ")" );
            }
            System.out.println("Vehicules:");
            for (Vehicle v : engine.getVehiclesSnapshot()) {
                String incidentId = v.currentIncident == null ? "none" : v.currentIncident.id.toString();
                System.out.println(" - " + v.matricule + " " + v.etat + " (" + v.lat + "," + v.lon + ") incident=" + incidentId);
            }
        }

        private void printHelp() {
            System.out.println("Commandes:");
            System.out.println(" help                               : afficher l'aide");
            System.out.println(" incident <type> <lat> <lon> <g>    : créer un incident manuel");
            System.out.println(" resolve <incidentId>               : marquer un incident résolu");
            System.out.println(" etat <matricule> <etat> [note]     : forcer l'état d'un véhicule (simule terminal)");
            System.out.println(" liste                              : afficher un snapshot incidents/véhicules");
        }
    }
}

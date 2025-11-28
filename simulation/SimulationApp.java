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
 * Simulation d'incidents et véhicules et envoi de l'état vers l'API
 * Le cahier des charges exige :
 * - Génération d'incidents (type, localisation, gravité, horodatage) et les faire évoluer.
 * - Simulation des positions de véhicules et leurs mouvements avec une transmission périodique.
 */
public class SimulationApp {

    public static void main(String[] args) {
        // Variable d'env propre : API_BASE_URL
        String apiBaseUrl = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080/api");
        if (args.length > 0) {
            apiBaseUrl = args[0];
        }

        ApiClient apiClient = new ApiClient(apiBaseUrl);          // wrapper pour faire les requêtes HTTP.
        SimulationEngine engine = new SimulationEngine(apiClient); // logique de simulation (incidents + véhicules)

        // Scheduler: simulation périodique automatique à chaque tick
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                engine.tick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS); // 0: démarrage immédiat, 10: période en secondes

        // CLI thread: permet de rentrer les données manuellement (simulate vehicle terminal / operator input)
        Thread cli = new Thread(new CommandListener(engine), "cli-listener");
        cli.setDaemon(true);
        cli.start();

        // Hook d’arrêt : quand la JVM s’arrête (Ctrl+C ou autre), on coupe proprement le scheduler.
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
        final UUID id;     // identifiant unique, immuable.
        IncidentType type;
        double lat; // latitude
        double lon; // longitude
        int gravite; // 1 à 3
        IncidentState etat;
        final Instant createdAt; // timestamp de création, immuable.
        Instant lastUpdate; // dernière fois qu’on a modifié l’état.

        Incident(IncidentType type, double lat, double lon, int gravite) {
            this.id = UUID.randomUUID();
            this.type = type;
            this.lat = lat;
            this.lon = lon;
            this.gravite = gravite;
            this.etat = IncidentState.NOUVEAU;
            this.createdAt = Instant.now();
            this.lastUpdate = this.createdAt;
        }
    }

    static class Vehicle {
        final UUID id;
        final String matricule; // id vehicule
        double lat;
        double lon;
        VehicleState etat;
        Incident currentIncident; // incident sur lequel le véhicule est engagé (= null, si libre)
        Instant lastUpdate; // quand on a touché à son état / position pour la dernière fois.

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

        // Paramètres de simu
        private static final double CITY_CENTER_LAT = 45.75;
        private static final double CITY_CENTER_LON = 4.85;

        SimulationEngine(ApiClient api) {
            this.api = api;
            initVehicles();
        }

        private void initVehicles() {
            vehicles.add(new Vehicle("VSAV1", CITY_CENTER_LAT + 0.01, CITY_CENTER_LON + 0.01));
            vehicles.add(new Vehicle("FPT1", CITY_CENTER_LAT - 0.01, CITY_CENTER_LON - 0.01));
            vehicles.add(new Vehicle("VSAV2", CITY_CENTER_LAT + 0.02, CITY_CENTER_LON - 0.01));
        }

        public synchronized void tick() {
            maybeCreateIncident();   // créer un incident aléatoire parfois,
            updateIncidentsState();  // faire évoluer leur état,
            updateVehicles();        // faire bouger / changer l’état des véhicules,
            pushStateToApi();        // envoyer tout ça à l’API.
        }

        // -------- Incidents --------

        private void maybeCreateIncident() {
            // ~20% de chance de générer un incident à chaque tick → en moyenne un incident toutes les ~10 s.
            if (random.nextDouble() < 0.2) {
                IncidentType type = IncidentType.values()[random.nextInt(IncidentType.values().length)];
                // Coordonnées générées autour du centre ville dans un carré +/- 0.025°.
                double lat = CITY_CENTER_LAT + (random.nextDouble() - 0.5) * 0.05;
                double lon = CITY_CENTER_LON + (random.nextDouble() - 0.5) * 0.05;
                int gravite = 1 + random.nextInt(3);
                createIncident(type, lat, lon, gravite, "auto");
            }
        }

        // synchronized car aussi appelée par la CLI
        synchronized Incident createIncident(IncidentType type, double lat, double lon, int gravite, String source) {
            Incident incident = new Incident(type, lat, lon, gravite);
            incidents.add(incident);
            System.out.println();
            System.out.println("[SIMU] (" + source + ") Nouvel incident : " + incident.id + " " + type + " Gravite : " + gravite);
            api.createIncident(incident); // appelle l’API (POST /incidents) pour que le backend soit au courant.
            return incident;
        }

        // synchronized car aussi appelée par la CLI
        synchronized boolean resolveIncident(UUID incidentId, String reason) {
            // Cette méthode sert à résoudre un incident
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
                        // si au moins un véhicule est affecté -> EN_COURS
                        if (vehicles.stream().anyMatch(v -> v.currentIncident == incident)) {
                            incident.etat = IncidentState.EN_COURS;
                            incident.lastUpdate = now;
                            api.updateIncident(incident);
                        }
                        break;
                    case EN_COURS:
                        // incident se résout après ~120s si non résolu manuellement
                        if (ageSec > 120) {
                            resolveIncident(incident.id, "auto");
                        }
                        break;
                    case RESOLU:
                        // rien
                        break;
                }
            }
        }

        // -------- Véhicules --------

        private void updateVehicles() {
            for (Vehicle v : vehicles) {
                switch (v.etat) {
                    case DISPONIBLE:
                        // chercher un incident NON RESOLU et NON encore pris
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
                            // si très proche de l'incident → SUR_PLACE
                            if (distance(v.lat, v.lon, v.currentIncident.lat, v.currentIncident.lon) < 0.0005) {
                                v.etat = VehicleState.SUR_PLACE;
                                v.lastUpdate = Instant.now();
                                System.out.println();
                                System.out.println("[SIMU] " + v.matricule + " sur place " + v.currentIncident.id);
                            }
                        }
                        break;

                    case SUR_PLACE:
                        // après résolution → retour
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

        // synchronized car appelée par la CLI
        synchronized boolean setVehicleState(String matricule, VehicleState newState, String note) {
            // Changement manuel d'état de véhicule
            for (Vehicle v : vehicles) {
                if (v.matricule.equalsIgnoreCase(matricule)) {
                    v.etat = newState;
                    v.lastUpdate = Instant.now();
                    if (newState == VehicleState.DISPONIBLE) {
                        v.currentIncident = null;
                    }
                    System.out.println();
                    System.out.println("[SIMU] Etat manuel " + v.matricule + " -> " + newState + (note.isEmpty() ? "" : " (" + note + ")"));
                    api.sendVehicleReport(v, note); // Envoi d'un report à l'API (genre message de terminal de bord)
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
            // pousser positions véhicules à chaque tick
            for (Vehicle v : vehicles) {
                api.sendVehiclePosition(v); //(POST /vehicules/position)
            }
            // envoyer un heartbeat d'incident (états + horodatage)
            for (Incident incident : incidents) {
                api.heartbeatIncident(incident); //(PATCH /incidents/{id}/heartbeat).
            }
        }

        // ---- Helpers for CLI ----

        synchronized List<Incident> getIncidentsSnapshot() {
            return new ArrayList<>(incidents);
        }

        synchronized List<Vehicle> getVehiclesSnapshot() {
            return new ArrayList<>(vehicles);
        }
    }

    // =========== CLIENT HTTP VERS L'API ===========

    static class ApiClient {
        private final String baseUrl;
        private final HttpClient http;
        private final DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;

        ApiClient(String baseUrl) {
            this.baseUrl = baseUrl;
            this.http = HttpClient.newHttpClient();
        }

        void createIncident(Incident incident) {
            String json = String.format(Locale.US,
                    "{ \"id\": \"%s\", \"type\": \"%s\", \"lat\": %.6f, \"lon\": %.6f, \"gravite\": %d, \"etat\": \"%s\", \"createdAt\": \"%s\", \"lastUpdate\": \"%s\" }",
                    incident.id, incident.type, incident.lat, incident.lon, incident.gravite, incident.etat,
                    iso.format(incident.createdAt), iso.format(incident.lastUpdate));

            postJson("/incidents", json);
        }

        void updateIncident(Incident incident) {
            String json = String.format(Locale.US,
                    "{ \"etat\": \"%s\", \"lastUpdate\": \"%s\" }",
                    incident.etat, iso.format(incident.lastUpdate == null ? Instant.now() : incident.lastUpdate));

            patchJson("/incidents/" + incident.id, json);
        }

        void heartbeatIncident(Incident incident) {
            String json = String.format(Locale.US,
                    "{ \"id\": \"%s\", \"etat\": \"%s\", \"gravite\": %d, \"lastUpdate\": \"%s\" }",
                    incident.id, incident.etat, incident.gravite, iso.format(incident.lastUpdate));
            patchJson("/incidents/" + incident.id + "/heartbeat", json);
        }

        void sendVehiclePosition(Vehicle v) {
            String incidentId = (v.currentIncident == null) ? null : v.currentIncident.id.toString();
            String json = String.format(Locale.US,
                    "{ \"vehiculeId\": \"%s\", \"matricule\": \"%s\", \"lat\": %.6f, \"lon\": %.6f, \"etat\": \"%s\", \"incidentId\": %s, \"lastUpdate\": \"%s\" }",
                    v.id, v.matricule, v.lat, v.lon, v.etat,
                    incidentId == null ? "null" : "\"" + incidentId + "\"",
                    iso.format(v.lastUpdate));

            postJson("/vehicules/position", json);
        }

        void sendVehicleReport(Vehicle v, String note) {
            String json = String.format(Locale.US,
                    "{ \"vehiculeId\": \"%s\", \"matricule\": \"%s\", \"etat\": \"%s\", \"note\": \"%s\", \"timestamp\": \"%s\" }",
                    v.id, v.matricule, v.etat, escapeJson(note), iso.format(Instant.now()));
            postJson("/vehicules/report", json);
        }

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

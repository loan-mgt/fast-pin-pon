package org.fastpinpon.incidentcreation;

import org.fastpinpon.incidentcreation.api.ApiClient;
import org.fastpinpon.incidentcreation.generator.IncidentGenerator;
import org.fastpinpon.incidentcreation.model.Incident;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Standalone incident creation service for Fast Pin Pon.
 * 
 * This service generates random incidents and sends them to the API.
 * It runs independently from the simulation engine and decision engine.
 */
public class IncidentCreationApp {
    private static final Logger LOG = Logger.getLogger(IncidentCreationApp.class.getName());

    private static final String API_BASE_URL_KEY = "API_BASE_URL";
    private static final String INCIDENT_INTERVAL_KEY = "INCIDENT_INTERVAL_SECONDS";
    private static final String LOG_FILE_ENV = "INCIDENT_CREATION_LOG_FILE";
    private static final String FILE_LOGGING_ENABLED_ENV = "INCIDENT_CREATION_FILE_LOGGING_ENABLED";
    
    // Keycloak environment variables
    private static final String KEYCLOAK_URL_KEY = "KEYCLOAK_URL";
    private static final String KEYCLOAK_REALM_KEY = "KEYCLOAK_REALM";
    private static final String KEYCLOAK_CLIENT_ID_KEY = "KEYCLOAK_CLIENT_ID";
    private static final String KEYCLOAK_CLIENT_SECRET_KEY = "KEYCLOAK_CLIENT_SECRET";

    private static final String DEFAULT_LOG_FILE = "/app/logs/incident-creation/incident-creation.log";
    
    private static final long DEFAULT_INTERVAL_SECONDS = 180;
    private static final int API_RETRY_COUNT = 30;
    private static final long API_RETRY_DELAY_MS = 2000;

    public static void main(String[] args) {
        configureFileLogging();
        
        String apiBaseUrl = resolveApiBaseUrl();
        long intervalSeconds = resolveIntervalSeconds();
        
        // Resolve Keycloak config
        String keycloakUrl = resolveEnv(KEYCLOAK_URL_KEY, "");
        String keycloakRealm = resolveEnv(KEYCLOAK_REALM_KEY, "");
        String clientId = resolveEnv(KEYCLOAK_CLIENT_ID_KEY, "");
        String clientSecret = resolveEnv(KEYCLOAK_CLIENT_SECRET_KEY, "");
        
        String tokenUrl = "";
        if (!keycloakUrl.isEmpty() && !keycloakRealm.isEmpty()) {
            tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, keycloakRealm);
        }

        LOG.log(Level.INFO, "[IncidentCreation] Starting with API={0}, interval={1}s, tokenUrl={2}", 
                new Object[]{apiBaseUrl, intervalSeconds, tokenUrl});

        ApiClient api = new ApiClient(apiBaseUrl, tokenUrl, clientId, clientSecret);
        
        // Wait for API to become available
        LOG.info("[IncidentCreation] Waiting for API to become available...");
        if (!api.waitForApi(API_RETRY_COUNT, API_RETRY_DELAY_MS)) {
            LOG.severe("[IncidentCreation] API not available after retries. Exiting.");
            System.exit(1);
        }
        LOG.info("[IncidentCreation] API is available. Starting incident generation.");

        IncidentGenerator generator = new IncidentGenerator(intervalSeconds);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Check for new incidents every second
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (generator.hasNewIncident()) {
                    Incident incident = generator.nextIncident();
                    if (incident != null) {
                        String eventId = api.createEvent(incident);
                        if (eventId != null) {
                            LOG.log(Level.INFO, "[IncidentCreation] Created incident #{0} as event {1}",
                                    new Object[]{incident.getNumber(), eventId});
                        } else {
                            LOG.log(Level.WARNING, "[IncidentCreation] Failed to create incident #{0}",
                                    incident.getNumber());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[IncidentCreation] Error in tick", e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("[IncidentCreation] Shutting down...");
            scheduler.shutdownNow();
        }));

        LOG.log(Level.INFO, "[IncidentCreation] Service started. Generating incidents every {0} seconds.", intervalSeconds);
    }

    private static String resolveApiBaseUrl() {
        String fromEnv = System.getenv(API_BASE_URL_KEY);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        String fromDotEnv = dotenv.get(API_BASE_URL_KEY);
        if (fromDotEnv != null && !fromDotEnv.trim().isEmpty()) {
            return fromDotEnv.trim();
        }

        Dotenv parentDotenv = Dotenv.configure()
                .directory("../")
                .ignoreIfMissing()
                .load();
        String fromParent = parentDotenv.get(API_BASE_URL_KEY);
        if (fromParent != null && !fromParent.trim().isEmpty()) {
            return fromParent.trim();
        }

        return "http://localhost:8081";
    }

    private static long resolveIntervalSeconds() {
        String fromEnv = System.getenv(INCIDENT_INTERVAL_KEY);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            try {
                return Long.parseLong(fromEnv.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to default
            }
        }

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        String fromDotEnv = dotenv.get(INCIDENT_INTERVAL_KEY);
        if (fromDotEnv != null && !fromDotEnv.trim().isEmpty()) {
            try {
                return Long.parseLong(fromDotEnv.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to default
            }
        }

        return DEFAULT_INTERVAL_SECONDS;
    }

    private static String resolveEnv(String key, String defaultValue) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        String fromDotEnv = dotenv.get(key);
        if (fromDotEnv != null && !fromDotEnv.trim().isEmpty()) {
            return fromDotEnv.trim();
        }

        Dotenv parentDotenv = Dotenv.configure()
                .directory("../")
                .ignoreIfMissing()
                .load();
        String fromParent = parentDotenv.get(key);
        if (fromParent != null && !fromParent.trim().isEmpty()) {
            return fromParent.trim();
        }

        return defaultValue;
    }

    private static void configureFileLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);

        if (!isFileLoggingEnabled()) {
            return;
        }

        String logFilePath = System.getenv(LOG_FILE_ENV);
        if (logFilePath == null || logFilePath.trim().isEmpty()) {
            logFilePath = DEFAULT_LOG_FILE;
        }

        Path target = Paths.get(logFilePath).toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
            FileHandler handler = new FileHandler(target.toString(), 5 * 1024 * 1024, 3, true);
            handler.setFormatter(new SimpleFormatter());
            root.addHandler(handler);
        } catch (IOException e) {
            root.log(Level.WARNING, "[IncidentCreation] Failed to setup file logging", e);
        }
    }

    private static boolean isFileLoggingEnabled() {
        String val = System.getenv(FILE_LOGGING_ENABLED_ENV);
        if (val == null || val.trim().isEmpty()) {
            return true; // keep file logging on by default
        }
        String normalized = val.trim().toLowerCase();
        return !(normalized.equals("false") || normalized.equals("0") || normalized.equals("no"));
    }
}

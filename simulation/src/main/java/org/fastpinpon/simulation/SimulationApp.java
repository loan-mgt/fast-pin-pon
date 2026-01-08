package org.fastpinpon.simulation;

import org.fastpinpon.simulation.api.ApiClient;
import org.fastpinpon.simulation.engine.SimulationEngine;
import org.fastpinpon.simulation.http.SimulationHttpServer;
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

public class SimulationApp {
    private static final String API_BASE_URL_KEY = "API_BASE_URL";
    private static final String LOG_FILE_ENV = "SIMULATION_LOG_FILE";
    private static final String FILE_LOGGING_ENABLED_ENV = "SIMULATION_FILE_LOGGING_ENABLED";
    private static final String HTTP_ENABLED_ENV = "SIM_HTTP_ENABLED";
    private static final String HTTP_PORT_ENV = "SIM_HTTP_PORT";
    private static final String DISABLE_AUTO_TICK_ENV = "SIM_DISABLE_AUTO_TICK";
    private static final String DEFAULT_LOG_FILE = "/app/logs/simulation/simulation.log";

    public static void main(String[] args) {
        configureFileLogging();
        String apiBaseUrl = resolveApiBaseUrl();
        ApiClient api = new ApiClient(apiBaseUrl);
        SimulationEngine engine = new SimulationEngine(api, apiBaseUrl);

        boolean autoTickEnabled = !envFlag(DISABLE_AUTO_TICK_ENV, false);
        boolean httpEnabled = envFlag(HTTP_ENABLED_ENV, true);
        int httpPort = resolveIntEnv(HTTP_PORT_ENV, 8090);

        if (httpEnabled) {
            try {
                SimulationHttpServer http = new SimulationHttpServer(engine);
                http.start(httpPort);
            } catch (Exception e) {
                Logger.getLogger(SimulationApp.class.getName())
                        .log(Level.WARNING, "[SIM] Failed to start HTTP server", e);
            }
        }

        ScheduledExecutorService scheduler = null;
        if (autoTickEnabled) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            // Tick every 1 second for smooth, real-time vehicle movement
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    engine.tick();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 1, TimeUnit.SECONDS);
        }

        ScheduledExecutorService finalScheduler = scheduler;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalScheduler != null) {
                finalScheduler.shutdownNow();
            }
        }));
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
            root.log(Level.WARNING, "[SIM] Failed to setup file logging", e);
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

    private static boolean envFlag(String key, boolean defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = val.trim().toLowerCase();
        return !(normalized.equals("false") || normalized.equals("0") || normalized.equals("no"));
    }

    private static int resolveIntEnv(String key, int defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

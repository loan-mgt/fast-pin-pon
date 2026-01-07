package org.fastpinpon.engine;

import org.fastpinpon.engine.api.DispatchApiClient;
import org.fastpinpon.engine.api.DispatchApiClientImpl;
import org.fastpinpon.engine.cache.StaticDataCache;
import org.fastpinpon.engine.cache.StaticDataCacheImpl;
import org.fastpinpon.engine.config.EngineConfig;
import org.fastpinpon.engine.domain.service.DispatchService;
import org.fastpinpon.engine.domain.service.DispatchServiceImpl;
import org.fastpinpon.engine.domain.service.ScoringService;
import org.fastpinpon.engine.domain.service.ScoringServiceImpl;
import org.fastpinpon.engine.http.CallbackServer;
import org.fastpinpon.engine.scheduler.DispatchScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Main entry point for the Decision Engine.
 * 
 * The engine handles unit-to-intervention assignment using multi-objective
 * scoring to minimize mean intervention time while maintaining coverage.
 * 
 * Trigger modes:
 * - On intervention creation: API calls POST /dispatch/{interventionId}
 * - Periodic: Scheduler runs every N seconds to redispatch freed units
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Engine startup failed", e);
            System.exit(1);
        }
    }

    private void run() throws Exception {
        LOG.info("=== Fast Pin Pon Decision Engine ===");

        // Load configuration
        EngineConfig config = EngineConfig.fromEnvironment();
        LOG.info(() -> "Configuration: " + config);

        // Configure logging
        configureLogging(config);

        // Create API client
        DispatchApiClient apiClient = new DispatchApiClientImpl(config.getApiBaseUrl());
        LOG.info(() -> "API client configured for: " + config.getApiBaseUrl());

        // Create cache and load static data
        StaticDataCache cache = new StaticDataCacheImpl(apiClient);
        LOG.info("Loading static data from API...");
        cache.refresh();

        if (!cache.isInitialized()) {
            LOG.warning("Cache not fully initialized, using defaults");
        }

        // Create services
        ScoringService scoringService = new ScoringServiceImpl();
        DispatchService dispatchService = new DispatchServiceImpl(apiClient, cache, scoringService);

        // Start callback server
        CallbackServer callbackServer = new CallbackServer(config.getCallbackPort(), cache, dispatchService);
        callbackServer.start();
        LOG.info(() -> "Callback server started on port " + config.getCallbackPort());

        // Start scheduler if enabled
        DispatchScheduler scheduler = null;
        if (config.isSchedulerEnabled()) {
            scheduler = new DispatchScheduler(dispatchService, config.getDispatchIntervalSeconds());
            scheduler.start();
            LOG.info(() -> "Dispatch scheduler started with interval: " + config.getDispatchIntervalSeconds() + "s");
        } else {
            LOG.info("Dispatch scheduler disabled");
        }

        // Register shutdown hook
        final DispatchScheduler finalScheduler = scheduler;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down engine...");
            callbackServer.stop();
            if (finalScheduler != null) {
                finalScheduler.stop();
            }
            LOG.info("Engine shutdown complete");
        }));

        LOG.info("=== Decision Engine started successfully ===");
        LOG.info("Endpoints:");
        LOG.info(() -> "  - Health: http://localhost:" + config.getCallbackPort() + "/health");
        LOG.info(() -> "  - Refresh: POST http://localhost:" + config.getCallbackPort() + "/refresh");
        LOG.info(() -> "  - Dispatch: POST http://localhost:" + config.getCallbackPort() + "/dispatch/{interventionId}");

        // Keep main thread alive
        Thread.currentThread().join();
    }

    /**
     * Configure file logging if enabled.
     */
    private void configureLogging(EngineConfig config) {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);

        if (!config.isFileLoggingEnabled()) {
            return;
        }

        String logFilePath = config.getLogFilePath();
        Path target = Paths.get(logFilePath).toAbsolutePath();

        try {
            Files.createDirectories(target.getParent());
            FileHandler handler = new FileHandler(target.toString(), 5 * 1024 * 1024, 3, true);
            handler.setFormatter(new SimpleFormatter());
            root.addHandler(handler);
            LOG.info(() -> "File logging enabled: " + target);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to setup file logging", e);
        }
    }
}

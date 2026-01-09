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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (InterruptedException e) {
            log.error("Engine interrupted", e);
            Thread.currentThread().interrupt();
            System.exit(1);
        }
    }

    private void run() throws InterruptedException {
        log.info("Starting Fast Pin Pon Decision Engine...");

        // Load configuration
        EngineConfig config = EngineConfig.fromEnvironment();
        log.info("Configuration loaded: {}", config);

        // API client
        DispatchApiClient apiClient = new DispatchApiClientImpl(config);
        log.info("API client configured for: {}", config.getApiBaseUrl());

        // Create cache and load static data
        StaticDataCache cache = new StaticDataCacheImpl(apiClient);
        log.info("Loading static data from API...");
        cache.refresh();

        if (!cache.isInitialized()) {
            log.warn("Cache not fully initialized, using defaults");
        }

        // Create services
        ScoringService scoringService = new ScoringServiceImpl();
        DispatchService dispatchService = new DispatchServiceImpl(apiClient, cache, scoringService);

        // Start callback server
        CallbackServer callbackServer = new CallbackServer(config.getCallbackPort(), cache, dispatchService);
        callbackServer.start();
        log.info("Callback server started on port {}", config.getCallbackPort());

        // Start scheduler if enabled
        DispatchScheduler scheduler = null;
        if (config.isSchedulerEnabled()) {
            scheduler = new DispatchScheduler(dispatchService, config.getDispatchIntervalSeconds());
            scheduler.start();
            log.info("Dispatch scheduler started with interval {}s", config.getDispatchIntervalSeconds());
        } else {
            log.info("Dispatch scheduler disabled");
        }

        // Register shutdown hook
        final DispatchScheduler finalScheduler = scheduler;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down engine...");
            callbackServer.stop();
            if (finalScheduler != null) {
                finalScheduler.stop();
            }
            log.info("Engine shutdown complete");
        }));

        log.info("Decision engine started successfully");
        log.info("Endpoints:");
        log.info("  - Health: http://localhost:{}/health", config.getCallbackPort());
        log.info("  - Refresh: POST http://localhost:{}/refresh", config.getCallbackPort());
        log.info("  - Dispatch: POST http://localhost:{}/dispatch/{{interventionId}}", config.getCallbackPort());

        // Keep main thread alive
        Thread.currentThread().join();
    }

}

package org.fastpinpon.engine.http;

import org.fastpinpon.engine.cache.StaticDataCache;
import org.fastpinpon.engine.domain.service.DispatchService;
import spark.Service;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server for engine callbacks from the API.
 * Exposes endpoints for refresh and dispatch triggers.
 */
public final class CallbackServer {

    private static final Logger LOG = Logger.getLogger(CallbackServer.class.getName());
    private static final String APPLICATION_JSON = "application/json";

    private final Service http;
    private final StaticDataCache cache;
    private final DispatchService dispatchService;

    public CallbackServer(int port, StaticDataCache cache, DispatchService dispatchService) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService must not be null");

        this.http = Service.ignite().port(port);

        registerHandlers();
        LOG.info(() -> "Callback server initialized on port " + port);
    }

    private void registerHandlers() {
        http.get("/health", (req, res) -> {
            res.type(APPLICATION_JSON);
            String status = cache.isInitialized() ? "healthy" : "initializing";
            return String.format("{\"status\":\"%s\"}", status);
        });

        http.post("/refresh", (req, res) -> {
            res.type(APPLICATION_JSON);
            LOG.info("Received refresh request");
            try {
                cache.refresh();
                return "{\"status\":\"refreshed\"}";
            } catch (Exception e) {
                LOG.log(Level.SEVERE, e, () -> "Refresh failed");
                res.status(500);
                return "{\"error\":\"refresh failed\"}";
            }
        });

        http.post("/dispatch/:interventionId", (req, res) -> {
            res.type(APPLICATION_JSON);
            String interventionId = req.params(":interventionId");

            if (interventionId == null || interventionId.isEmpty()) {
                res.status(400);
                return "{\"error\":\"missing intervention ID\"}";
            }

            LOG.info(() -> "Received dispatch request for intervention: " + interventionId);
            try {
                int dispatched = dispatchService.dispatchForIntervention(interventionId).size();
                return String.format("{\"status\":\"dispatched\",\"count\":%d}", dispatched);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, e, () -> "Dispatch failed for " + interventionId);
                res.status(500);
                return "{\"error\":\"dispatch failed\"}";
            }
        });
    }

    /**
     * Start the callback server.
     */
    public void start() {
        http.awaitInitialization();
        LOG.info("Callback server started");
    }

    /**
     * Stop the callback server.
     */
    public void stop() {
        http.stop();
        LOG.info("Callback server stopped");
    }
}

package org.fastpinpon.engine.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fastpinpon.engine.cache.StaticDataCache;
import org.fastpinpon.engine.domain.service.DispatchService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server for engine callbacks from the API.
 * Exposes endpoints for refresh and dispatch triggers.
 */
public final class CallbackServer {

    private static final Logger LOG = Logger.getLogger(CallbackServer.class.getName());

    private final HttpServer server;
    private final StaticDataCache cache;
    private final DispatchService dispatchService;

    public CallbackServer(int port, StaticDataCache cache, DispatchService dispatchService) throws IOException {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService must not be null");

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(4));

        registerHandlers();
        LOG.info(() -> "Callback server initialized on port " + port);
    }

    private void registerHandlers() {
        server.createContext("/health", this::handleHealth);
        server.createContext("/refresh", this::handleRefresh);
        server.createContext("/dispatch/", this::handleDispatch);
    }

    /**
     * Start the callback server.
     */
    public void start() {
        server.start();
        LOG.info("Callback server started");
    }

    /**
     * Stop the callback server.
     */
    public void stop() {
        server.stop(1);
        LOG.info("Callback server stopped");
    }

    /**
     * Health check endpoint.
     * GET /health
     */
    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        String status = cache.isInitialized() ? "healthy" : "initializing";
        sendResponse(exchange, 200, String.format("{\"status\":\"%s\"}", status));
    }

    /**
     * Refresh static data cache.
     * POST /refresh
     */
    private void handleRefresh(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        LOG.info("Received refresh request");
        try {
            cache.refresh();
            sendResponse(exchange, 200, "{\"status\":\"refreshed\"}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Refresh failed", e);
            sendResponse(exchange, 500, "{\"error\":\"refresh failed\"}");
        }
    }

    /**
     * Dispatch units for an intervention.
     * POST /dispatch/{interventionId}
     */
    private void handleDispatch(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String interventionId = extractInterventionId(path);

        if (interventionId == null || interventionId.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"missing intervention ID\"}");
            return;
        }

        LOG.info(() -> "Received dispatch request for intervention: " + interventionId);
        try {
            int dispatched = dispatchService.dispatchForIntervention(interventionId).size();
            sendResponse(exchange, 200, String.format("{\"status\":\"dispatched\",\"count\":%d}", dispatched));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Dispatch failed for " + interventionId, e);
            sendResponse(exchange, 500, "{\"error\":\"dispatch failed\"}");
        }
    }

    /**
     * Extract intervention ID from path like /dispatch/{id}
     */
    private String extractInterventionId(String path) {
        // Path is /dispatch/{interventionId}
        if (path == null || !path.startsWith("/dispatch/")) {
            return null;
        }
        String id = path.substring("/dispatch/".length());
        // Remove trailing slash if present
        if (id.endsWith("/")) {
            id = id.substring(0, id.length() - 1);
        }
        return id.isEmpty() ? null : id;
    }

    /**
     * Send HTTP response.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

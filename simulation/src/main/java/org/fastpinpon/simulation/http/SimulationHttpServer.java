package org.fastpinpon.simulation.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fastpinpon.simulation.engine.SimulationEngine;
import org.fastpinpon.simulation.engine.SimulationEngine.VehicleSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimulationHttpServer {
    private static final Logger LOG = Logger.getLogger(SimulationHttpServer.class.getName());
        private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SimulationEngine engine;
    private HttpServer server;

    public SimulationHttpServer(SimulationEngine engine) {
        this.engine = engine;
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/tick", this::handleTick);
        server.createContext("/units", this::handleUnits);
        server.createContext("/status", this::handleStatus);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        LOG.log(Level.INFO, "[SIM] HTTP server listening on port {0}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleTick(HttpExchange exchange) throws IOException {
        String clientIP = exchange.getRemoteAddress().toString();
        LOG.log(Level.INFO, "[HTTP] <-- {0} {1} from {2}",
                new Object[]{exchange.getRequestMethod(), "/tick", clientIP});
        
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                LOG.log(Level.WARNING, "[HTTP] --> 405 Method Not Allowed");
                sendPlain(exchange, 405, "Method Not Allowed");
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            int count = parsePositiveInt(query.get("count"), 1);
            LOG.log(Level.INFO, "[HTTP] Processing {0} tick(s)...", count);
            
            for (int i = 0; i < count; i++) {
                engine.tick();
            }
            
            List<VehicleSnapshot> snapshots = engine.snapshotVehicles();
            LOG.log(Level.INFO, "[HTTP] --> 200 OK | {0} vehicles returned", snapshots.size());
            writeSnapshots(exchange, snapshots);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[HTTP] --> 500 Error: {0}", e.getMessage());
            sendPlain(exchange, 500, "tick failed: " + e.getMessage());
        }
    }

    private void handleUnits(HttpExchange exchange) throws IOException {
        String clientIP = exchange.getRemoteAddress().toString();
        LOG.log(Level.INFO, "[HTTP] <-- {0} {1} from {2}",
                new Object[]{exchange.getRequestMethod(), "/units", clientIP});
        
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                LOG.log(Level.WARNING, "[HTTP] --> 405 Method Not Allowed");
                sendPlain(exchange, 405, "Method Not Allowed");
                return;
            }
            
            List<VehicleSnapshot> snapshots = engine.snapshotVehicles();
            LOG.log(Level.INFO, "[HTTP] --> 200 OK | {0} vehicles returned", snapshots.size());
            writeSnapshots(exchange, snapshots);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[HTTP] --> 500 Error: {0}", e.getMessage());
            sendPlain(exchange, 500, "units failed: " + e.getMessage());
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        String clientIP = exchange.getRemoteAddress().toString();
        LOG.log(Level.INFO, "[HTTP] <-- {0} {1} from {2}",
                new Object[]{exchange.getRequestMethod(), "/status", clientIP});

        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, Object> status = new HashMap<>();
            status.put("updating_enabled", engine.isUpdatingEnabled());
            status.put("vehicle_count", engine.snapshotVehicles().size());
            // uptime can be calculated if we tracked start time, but for now simple availability is enough
            // or we could add start time to Engine.
            status.put("status", "UP");

            byte[] body = MAPPER.writeValueAsBytes(status);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            LOG.log(Level.INFO, "[HTTP] --> 200 OK | Status returned");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[HTTP] --> 500 Error: {0}", e.getMessage());
            sendPlain(exchange, 500, "status failed: " + e.getMessage());
        }
    }

    private void writeSnapshots(HttpExchange exchange, List<VehicleSnapshot> payload) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void sendPlain(HttpExchange exchange, int status, String msg) throws IOException {
        byte[] body = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> res = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String key = decode(kv[0]);
            String val = kv.length > 1 ? decode(kv[1]) : "";
            res.put(key, val);
        }
        return res;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private int parsePositiveInt(String raw, int defaultValue) {
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

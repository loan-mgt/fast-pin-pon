package org.fastpinpon.simulation.routing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routing service that uses OSRM (Open Source Routing Machine) to calculate
 * road-based routes between points. Uses the public OSRM demo server.
 * 
 * For production use, consider self-hosting OSRM or using a commercial service.
 */
public final class RoutingService {
    private static final Logger LOG = Logger.getLogger(RoutingService.class.getName());
    
    // OSRM public demo server (for development/testing)
    // For production, use your own OSRM instance or a commercial service
    private static final String OSRM_BASE_URL = "https://router.project-osrm.org";
    
    // Connection timeout in milliseconds
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;
    
    // Cache to avoid repeated API calls for the same route
    private static final int MAX_CACHE_SIZE = 100;
    private final java.util.LinkedHashMap<String, List<double[]>> routeCache = 
            new java.util.LinkedHashMap<String, List<double[]>>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, List<double[]>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };
    
    /**
     * Get a route between two points following roads.
     * 
     * @param fromLat starting latitude
     * @param fromLon starting longitude
     * @param toLat destination latitude
     * @param toLon destination longitude
     * @return list of waypoints (lat/lon pairs) forming the route, or null if routing fails
     */
    public List<double[]> getRoute(double fromLat, double fromLon, double toLat, double toLon) {
        String cacheKey = String.format("%.5f,%.5f->%.5f,%.5f", fromLat, fromLon, toLat, toLon);
        
        // Check cache first
        synchronized (routeCache) {
            if (routeCache.containsKey(cacheKey)) {
                return new ArrayList<>(routeCache.get(cacheKey));
            }
        }
        
        try {
            // OSRM expects coordinates as lon,lat (not lat,lon!)
            // Using 'driving' profile - OSRM automatically calculates the fastest route
            // alternatives=false to get only the fastest route
            // steps=false to reduce response size (we only need geometry)
            String urlStr = String.format(
                    "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&alternatives=false&steps=false",
                    OSRM_BASE_URL, fromLon, fromLat, toLon, toLat
            );
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "FastPinPon-Simulation/1.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOG.log(Level.WARNING, "[Routing] OSRM returned status {0}", responseCode);
                return null;
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            List<double[]> waypoints = parseOsrmResponse(response.toString());
            
            if (waypoints != null && !waypoints.isEmpty()) {
                synchronized (routeCache) {
                    routeCache.put(cacheKey, new ArrayList<>(waypoints));
                }
                LOG.log(Level.FINE, "[Routing] Route calculated with {0} waypoints", waypoints.size());
            }
            
            return waypoints;
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to get route: {0}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse OSRM JSON response to extract route coordinates.
     * Simple JSON parsing without external libraries.
     */
    private List<double[]> parseOsrmResponse(String json) {
        List<double[]> waypoints = new ArrayList<>();
        
        try {
            // Find the coordinates array in the GeoJSON geometry
            int coordsStart = json.indexOf("\"coordinates\":");
            if (coordsStart == -1) {
                return null;
            }
            
            int arrayStart = json.indexOf("[[", coordsStart);
            int arrayEnd = json.indexOf("]]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) {
                return null;
            }
            
            String coordsStr = json.substring(arrayStart + 1, arrayEnd + 1);
            
            // Parse each coordinate pair [lon, lat]
            int pos = 0;
            while (pos < coordsStr.length()) {
                int pairStart = coordsStr.indexOf("[", pos);
                if (pairStart == -1) break;
                
                int pairEnd = coordsStr.indexOf("]", pairStart);
                if (pairEnd == -1) break;
                
                String pair = coordsStr.substring(pairStart + 1, pairEnd);
                String[] parts = pair.split(",");
                if (parts.length >= 2) {
                    double lon = Double.parseDouble(parts[0].trim());
                    double lat = Double.parseDouble(parts[1].trim());
                    waypoints.add(new double[]{lat, lon}); // Store as lat, lon
                }
                
                pos = pairEnd + 1;
            }
            
            // Keep all waypoints for accurate road-following at higher speeds
            // Important turns are preserved to keep vehicles on roads
            return simplifyRoute(waypoints, 1);
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to parse OSRM response: {0}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Simplify route by keeping every Nth waypoint, plus start and end.
     * Uses distance-based simplification to ensure we keep important turn points.
     * For fast-moving vehicles, keeps more waypoints to ensure accurate road following.
     */
    private List<double[]> simplifyRoute(List<double[]> route, int keepEveryN) {
        if (route.size() <= 10) {
            return route; // Keep all points for short routes
        }
        
        List<double[]> simplified = new ArrayList<>();
        simplified.add(route.get(0)); // Always keep start
        
        double[] lastKept = route.get(0);
        // Minimum distance between kept points (in degrees, ~50 meters)
        double minDistBetweenPoints = 0.0005;
        
        for (int i = 1; i < route.size() - 1; i++) {
            double[] current = route.get(i);
            double distFromLastKept = Math.sqrt(
                    Math.pow(current[0] - lastKept[0], 2) + 
                    Math.pow(current[1] - lastKept[1], 2)
            );
            
            // Keep point if: it's every Nth, or there's a turn, or sufficient distance
            boolean keepForDistance = distFromLastKept >= minDistBetweenPoints;
            boolean keepForInterval = (i % keepEveryN == 0);
            boolean keepForTurn = false;
            
            // Check for significant turn
            if (i > 0 && i < route.size() - 1) {
                double[] prev = route.get(i - 1);
                double[] next = route.get(i + 1);
                keepForTurn = isSignificantTurn(prev, current, next);
            }
            
            if (keepForInterval || keepForTurn || keepForDistance) {
                simplified.add(current);
                lastKept = current;
            }
        }
        
        simplified.add(route.get(route.size() - 1)); // Always keep end
        
        return simplified;
    }
    
    /**
     * Check if there's a significant turn at the current point.
     */
    private boolean isSignificantTurn(double[] prev, double[] current, double[] next) {
        double angle1 = Math.atan2(current[0] - prev[0], current[1] - prev[1]);
        double angle2 = Math.atan2(next[0] - current[0], next[1] - current[1]);
        double angleDiff = Math.abs(angle2 - angle1);
        // Normalize to 0-PI
        if (angleDiff > Math.PI) {
            angleDiff = 2 * Math.PI - angleDiff;
        }
        // Keep points with turns > 20 degrees
        return angleDiff > Math.toRadians(20);
    }
    
    /**
     * Clear the route cache.
     */
    public void clearCache() {
        synchronized (routeCache) {
            routeCache.clear();
        }
    }
}

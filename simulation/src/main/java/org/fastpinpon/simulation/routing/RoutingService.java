package org.fastpinpon.simulation.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routing service that uses the local pgRouting API to calculate
 * road-based routes between points and manage route storage.
 * 
 * Routes are stored as LINESTRING geometries in the backend with
 * percentage-based position interpolation for smooth vehicle movement.
 */
public final class RoutingService {
    private static final Logger LOG = Logger.getLogger(RoutingService.class.getName());
    
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // Cache to avoid repeated API calls for the same route
    private static final int MAX_CACHE_SIZE = 100;
    private final java.util.LinkedHashMap<String, RouteInfo> routeCache = 
            new java.util.LinkedHashMap<String, RouteInfo>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, RouteInfo> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };
    
    private final RoutingApi api;
    
    /**
     * Route information with geometry and metrics.
     */
    public static final class RouteInfo {
        public final String geoJson;
        public final double lengthMeters;
        public final double durationSeconds;
        
        public RouteInfo(String geoJson, double lengthMeters, double durationSeconds) {
            this.geoJson = geoJson;
            this.lengthMeters = lengthMeters;
            this.durationSeconds = durationSeconds;
        }
    }
    
    /**
     * Position on a route.
     */
    public static final class Position {
        public final double lat;
        public final double lon;
        
        public Position(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }
    
    /**
     * Create a routing service connected to the local API.
     * 
     * @param apiBaseUrl the base URL of the API (e.g., "http://localhost:8081")
     */
    public RoutingService(String apiBaseUrl) {
        String normalized = apiBaseUrl.endsWith("/") ? apiBaseUrl : apiBaseUrl + "/";
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalized)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .client(HTTP)
                .build();
        this.api = retrofit.create(RoutingApi.class);
        
        LOG.log(Level.INFO, "[Routing] Initialized with API: {0}", normalized);
    }
    
    /**
     * Calculate a route between two points using pgRouting.
     * Results are cached for efficiency.
     * 
     * @param fromLat starting latitude
     * @param fromLon starting longitude
     * @param toLat destination latitude
     * @param toLon destination longitude
     * @return route information or null if routing fails
     */
    public RouteInfo calculateRoute(double fromLat, double fromLon, double toLat, double toLon) {
        String cacheKey = String.format("%.5f,%.5f->%.5f,%.5f", fromLat, fromLon, toLat, toLon);
        
        LOG.log(Level.INFO, "[Routing] Calculating route: ({0}, {1}) -> ({2}, {3})",
                new Object[]{fromLat, fromLon, toLat, toLon});
        
        // Check cache first
        synchronized (routeCache) {
            if (routeCache.containsKey(cacheKey)) {
                RouteInfo cached = routeCache.get(cacheKey);
                LOG.log(Level.INFO, "[Routing] Cache HIT: {0}m, {1}s",
                        new Object[]{String.format("%.0f", cached.lengthMeters), String.format("%.0f", cached.durationSeconds)});
                return cached;
            }
        }
        
        LOG.log(Level.INFO, "[Routing] Cache MISS, calling API...");
        
        try {
            CalculateRouteRequest request = new CalculateRouteRequest(fromLat, fromLon, toLat, toLon);
            Response<CalculateRouteResponse> response = api.calculateRoute(request).execute();
            
            if (!response.isSuccessful()) {
                String errorBody = extractErrorBody(response);
                LOG.log(Level.WARNING, "[Routing] API ERROR: status={0}, url={1}, error={2}",
                        new Object[]{response.code(), response.raw().request().url(), errorBody});
                return null;
            }
            
            CalculateRouteResponse body = response.body();
            if (body == null || body.routeGeoJson == null || body.routeGeoJson.isEmpty()) {
                LOG.log(Level.WARNING, "[Routing] No route found between points");
                return null;
            }
            
            RouteInfo routeInfo = new RouteInfo(body.routeGeoJson, body.routeLengthMeters, body.estimatedDurationSeconds);
            
            // Cache the result
            synchronized (routeCache) {
                routeCache.put(cacheKey, routeInfo);
            }
            
            LOG.log(Level.INFO, "[Routing] Route SUCCESS: {0}m ({1}km), {2}s ({3} min)", 
                    new Object[]{
                        String.format("%.0f", routeInfo.lengthMeters),
                        String.format("%.2f", routeInfo.lengthMeters / 1000),
                        String.format("%.0f", routeInfo.durationSeconds),
                        String.format("%.1f", routeInfo.durationSeconds / 60)
                    });
            
            return routeInfo;
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] EXCEPTION: {0}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract error body from response.
     */
    private String extractErrorBody(Response<CalculateRouteResponse> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            // Ignore error reading body - not critical
            LOG.log(Level.FINE, "Could not read error body: {0}", e.getMessage());
        }
        return "";
    }
    
    /**
     * Legacy method for backward compatibility.
     * Returns waypoints extracted from the GeoJSON route.
     * 
     * @deprecated Use calculateRoute() and saveUnitRoute() instead
     */
    @Deprecated
    public List<double[]> getRoute(double fromLat, double fromLon, double toLat, double toLon) {
        RouteInfo route = calculateRoute(fromLat, fromLon, toLat, toLon);
        if (route == null) {
            return new ArrayList<>();
        }
        return parseGeoJsonCoordinates(route.geoJson);
    }
    
    /**
     * Save a route for a unit in the backend.
     * 
     * @param unitId the unit ID
     * @param interventionId the intervention ID (optional)
     * @param route the route information
     * @return true if saved successfully
     */
    public boolean saveUnitRoute(String unitId, String interventionId, RouteInfo route) {
        if (unitId == null || route == null) {
            return false;
        }
        
        try {
            SaveRouteRequest request = new SaveRouteRequest(
                    interventionId, 
                    route.geoJson, 
                    route.lengthMeters, 
                    route.durationSeconds
            );
            Response<Void> response = api.saveUnitRoute(unitId, request).execute();
            
            if (!response.isSuccessful()) {
                LOG.log(Level.WARNING, "[Routing] Failed to save route for unit {0}: {1}", 
                        new Object[]{unitId, response.code()});
                return false;
            }
            
            LOG.log(Level.FINE, "[Routing] Saved route for unit {0}", unitId);
            return true;
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to save route for unit {0}: {1}", 
                    new Object[]{unitId, e.getMessage()});
            return false;
        }
    }
    
    /**
     * Update the progress percentage for a unit's route.
     * Returns the new interpolated position.
     * 
     * @param unitId the unit ID
     * @param progressPercent the progress percentage (0-100)
     * @return the new position or null if update fails
     */
    public Position updateProgress(String unitId, double progressPercent) {
        if (unitId == null) {
            return null;
        }
        
        try {
            UpdateProgressRequest request = new UpdateProgressRequest(progressPercent);
            Response<UpdateProgressResponse> response = api.updateProgress(unitId, request).execute();
            
            if (!response.isSuccessful()) {
                LOG.log(Level.WARNING, "[Routing] Failed to update progress for unit {0}: {1}", 
                        new Object[]{unitId, response.code()});
                return null;
            }
            
            UpdateProgressResponse body = response.body();
            if (body == null) {
                return null;
            }
            
            return new Position(body.currentLat, body.currentLon);
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to update progress for unit {0}: {1}", 
                    new Object[]{unitId, e.getMessage()});
            return null;
        }
    }
    
    /**
     * Get the interpolated position at a specific progress percentage.
     * 
     * @param unitId the unit ID
     * @param progressPercent the progress percentage (0-100)
     * @return the position or null if not found
     */
    public Position getPositionAtProgress(String unitId, double progressPercent) {
        if (unitId == null) {
            return null;
        }
        
        try {
            Response<PositionResponse> response = api.getPosition(unitId, progressPercent).execute();
            
            if (!response.isSuccessful()) {
                return null;
            }
            
            PositionResponse body = response.body();
            if (body == null) {
                return null;
            }
            
            return new Position(body.lat, body.lon);
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to get position for unit {0}: {1}", 
                    new Object[]{unitId, e.getMessage()});
            return null;
        }
    }
    
    /**
     * Delete a unit's route from the backend.
     * 
     * @param unitId the unit ID
     */
    public void deleteUnitRoute(String unitId) {
        if (unitId == null) {
            return;
        }
        
        try {
            Response<Void> response = api.deleteUnitRoute(unitId).execute();
            if (response.isSuccessful()) {
                LOG.log(Level.FINE, "[Routing] Deleted route for unit {0}", unitId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to delete route for unit {0}: {1}", 
                    new Object[]{unitId, e.getMessage()});
        }
    }
    
    /**
     * Parse GeoJSON LineString coordinates into lat/lon waypoints.
     */
    private List<double[]> parseGeoJsonCoordinates(String geoJson) {
        List<double[]> waypoints = new ArrayList<>();
        
        try {
            // Find the coordinates array in the GeoJSON
            int coordsStart = geoJson.indexOf("\"coordinates\":");
            if (coordsStart == -1) {
                return waypoints;
            }
            
            int arrayStart = geoJson.indexOf("[[", coordsStart);
            int arrayEnd = geoJson.indexOf("]]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) {
                return waypoints;
            }
            
            String coordsStr = geoJson.substring(arrayStart + 1, arrayEnd + 1);
            
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
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Routing] Failed to parse GeoJSON: {0}", e.getMessage());
        }
        
        return waypoints;
    }
    
    /**
     * Clear the route cache.
     */
    public void clearCache() {
        synchronized (routeCache) {
            routeCache.clear();
        }
    }
    
    // =========================================================================
    // Retrofit API Interface
    // =========================================================================
    
    private interface RoutingApi {
        @POST("/v1/routing/calculate")
        Call<CalculateRouteResponse> calculateRoute(@Body CalculateRouteRequest body);
        
        @POST("/v1/units/{unitId}/route")
        Call<Void> saveUnitRoute(@Path("unitId") String unitId, @Body SaveRouteRequest body);
        
        @GET("/v1/units/{unitId}/route/position")
        Call<PositionResponse> getPosition(@Path("unitId") String unitId, @Query("progress") double progress);
        
        @PATCH("/v1/units/{unitId}/route/progress")
        Call<UpdateProgressResponse> updateProgress(@Path("unitId") String unitId, @Body UpdateProgressRequest body);
        
        @DELETE("/v1/units/{unitId}/route")
        Call<Void> deleteUnitRoute(@Path("unitId") String unitId);
    }
    
    // =========================================================================
    // Request/Response DTOs
    // =========================================================================
    
    private static final class CalculateRouteRequest {
        @JsonProperty("from_lat")
        private final double fromLat;
        @JsonProperty("from_lon")
        private final double fromLon;
        @JsonProperty("to_lat")
        private final double toLat;
        @JsonProperty("to_lon")
        private final double toLon;
        
        CalculateRouteRequest(double fromLat, double fromLon, double toLat, double toLon) {
            this.fromLat = fromLat;
            this.fromLon = fromLon;
            this.toLat = toLat;
            this.toLon = toLon;
        }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class CalculateRouteResponse {
        @JsonProperty("route_geojson")
        private String routeGeoJson;
        @JsonProperty("route_length_meters")
        private double routeLengthMeters;
        @JsonProperty("estimated_duration_seconds")
        private double estimatedDurationSeconds;
    }
    
    private static final class SaveRouteRequest {
        @JsonProperty("intervention_id")
        private final String interventionId;
        @JsonProperty("route_geojson")
        private final String routeGeoJson;
        @JsonProperty("route_length_meters")
        private final double routeLengthMeters;
        @JsonProperty("estimated_duration_seconds")
        private final double estimatedDurationSeconds;
        
        SaveRouteRequest(String interventionId, String routeGeoJson, double routeLengthMeters, double estimatedDurationSeconds) {
            this.interventionId = interventionId;
            this.routeGeoJson = routeGeoJson;
            this.routeLengthMeters = routeLengthMeters;
            this.estimatedDurationSeconds = estimatedDurationSeconds;
        }
    }
    
    private static final class UpdateProgressRequest {
        @JsonProperty("progress_percent")
        private final double progressPercent;
        
        UpdateProgressRequest(double progressPercent) {
            this.progressPercent = progressPercent;
        }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class UpdateProgressResponse {
        @JsonProperty("progress_percent")
        private double progressPercent;
        @JsonProperty("current_lat")
        private double currentLat;
        @JsonProperty("current_lon")
        private double currentLon;
        @JsonProperty("remaining_meters")
        private double remainingMeters;
        @JsonProperty("remaining_seconds")
        private double remainingSeconds;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PositionResponse {
        @JsonProperty("lat")
        private double lat;
        @JsonProperty("lon")
        private double lon;
    }
}

package org.fastpinpon.simulation.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApiClient {
    private static final Logger LOG = Logger.getLogger(ApiClient.class.getName());
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private final Random random = new Random();
    private final List<String> eventTypeCodes = new ArrayList<>();
    public final List<String> unitTypeCodes = new ArrayList<>();
    private final ApiService api;

    public ApiClient(String baseUrlRaw) {
        String normalized = baseUrlRaw.endsWith("/") ? baseUrlRaw : baseUrlRaw + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalized)
            .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
            .client(HTTP)
                .build();
        this.api = retrofit.create(ApiService.class);

        loadEventTypes();
        loadUnitTypes();
    }

    public static final class UnitInfo {
        public final String id;
        public final String callSign;
        public final String homeBase;
        public final String unitTypeCode;
        public final String status;
        public final Double latitude;
        public final Double longitude;

        public UnitInfo(String id, String callSign, String homeBase, String unitTypeCode, String status, Double latitude, Double longitude) {
            this.id = id;
            this.callSign = callSign;
            this.homeBase = homeBase;
            this.unitTypeCode = unitTypeCode;
            this.status = status;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class UnitRouteInfo {
        public final String unitId;
        public final String interventionId;
        public final double routeLengthMeters;
        public final double estimatedDurationSeconds;
        public final double progressPercent;
        public final Double currentLat;
        public final Double currentLon;
        public final Integer severity;

        @com.fasterxml.jackson.annotation.JsonCreator
        public UnitRouteInfo(@JsonProperty("unit_id") String unitId, 
                             @JsonProperty("intervention_id") String interventionId, 
                             @JsonProperty("route_length_meters") double routeLengthMeters,
                             @JsonProperty("estimated_duration_seconds") double estimatedDurationSeconds, 
                             @JsonProperty("progress_percent") double progressPercent,
                             @JsonProperty("current_lat") Double currentLat, 
                             @JsonProperty("current_lon") Double currentLon,
                             @JsonProperty("severity") Integer severity) {
            this.unitId = unitId;
            this.interventionId = interventionId;
            this.routeLengthMeters = routeLengthMeters;
            this.estimatedDurationSeconds = estimatedDurationSeconds;
            this.progressPercent = progressPercent;
            this.currentLat = currentLat;
            this.currentLon = currentLon;
            this.severity = severity;
        }
    }

    public static final class ProgressUpdateResult {
        public final String unitId;
        public final double progressPercent;
        public final double currentLat;
        public final double currentLon;
        public final double remainingMeters;
        public final double remainingSeconds;

        public ProgressUpdateResult(String unitId, double progressPercent, double currentLat,
                                    double currentLon, double remainingMeters, double remainingSeconds) {
            this.unitId = unitId;
            this.progressPercent = progressPercent;
            this.currentLat = currentLat;
            this.currentLon = currentLon;
            this.remainingMeters = remainingMeters;
            this.remainingSeconds = remainingSeconds;
        }
    }

    public static final class AssignmentInfo {
        public final String id;
        public final String unitId;
        public final String status;

        public AssignmentInfo(String id, String unitId, String status) {
            this.id = id;
            this.unitId = unitId;
            this.status = status;
        }
    }

    public static final class BaseLocation {
        public final String name;
        public final double latitude;
        public final double longitude;

        public BaseLocation(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Check if the API is reachable.
     * @return true if the API responds successfully
     */
    public boolean isHealthy() {
        try {
            List<UnitDto> result = execute(api.getUnits(), "GET /v1/units (health check)");
            return result != null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[API] Health check failed: {0}", e.getMessage());
            return false;
        }
    }

    /**
     * Load all buildings (locations) from the API and return fire stations as base locations.
     */
    public List<BaseLocation> loadStations() {
        List<BaseLocation> res = new ArrayList<>();
        List<BuildingDto> dtos = execute(api.getBuildings(), "GET /v1/buildings");
        if (dtos == null) {
            return res;
        }
        for (BuildingDto b : dtos) {
            BaseLocation station = toBaseLocation(b);
            if (station == null) {
                continue;
            }
            res.add(station);
        }
        return res;
    }

    private BaseLocation toBaseLocation(BuildingDto building) {
        if (building == null || building.location == null) {
            return null;
        }
        if (building.type != null && !"station".equalsIgnoreCase(building.type)) {
            return null;
        }
        Double lat = building.location.getLatitude();
        Double lon = building.location.getLongitude();
        if (lat == null || lon == null) {
            return null;
        }
        return new BaseLocation(resolveStationName(building), lat, lon);
    }

    private String resolveStationName(BuildingDto building) {
        if (building.name != null) {
            return building.name;
        }
        if (building.id != null) {
            return building.id;
        }
        return "Station";
    }

    /**
     * Load all units from the API.
     * @return list of units (empty if call fails)
     */
    public List<UnitInfo> loadUnits() {
        List<UnitDto> dtos = execute(api.getUnits(), "GET /v1/units");
        List<UnitInfo> res = new ArrayList<>();
        if (dtos == null) {
            return res;
        }
        for (UnitDto dto : dtos) {
            if (dto.getId() == null) {
                continue;
            }
            Double lat = dto.getLatitude();
            if (lat == null && dto.getLocation() != null) {
                lat = dto.getLocation().getLatitude();
            }
            Double lon = dto.getLongitude();
            if (lon == null && dto.getLocation() != null) {
                lon = dto.getLocation().getLongitude();
            }
            res.add(new UnitInfo(
                    dto.getId(),
                    nvl(dto.getCallSign(), dto.getId()),
                    nvl(dto.getHomeBase(), ""),
                    nvl(dto.getUnitTypeCode(), ""),
                    nvl(dto.getStatus(), ""),
                    lat,
                    lon
            ));
        }
        return res;
    }

    /**
     * Update an assignment status.
     * @param assignmentId assignment identifier
     * @param status new status value
     */
    public void updateAssignmentStatus(String assignmentId, String status) {
        if (assignmentId == null || assignmentId.trim().isEmpty()) {
            return;
        }
        StatusRequest payload = new StatusRequest(status);
        executeVoid(api.updateAssignmentStatus(assignmentId, payload), "PATCH /v1/assignments/{id}/status", payload);
    }

    /**
     * Update an intervention status.
     * @param interventionId intervention identifier
     * @param status new status value
     */
    public void updateInterventionStatus(String interventionId, String status) {
        if (interventionId == null || interventionId.trim().isEmpty()) {
            return;
        }
        StatusRequest payload = new StatusRequest(status);
        executeVoid(api.updateInterventionStatus(interventionId, payload), "PATCH /v1/interventions/{id}/status", payload);
    }

    /**
     * Update a unit status.
     * @param unitId unit identifier
     * @param status new status value
     */
    public void updateUnitStatus(String unitId, String status) {
        if (unitId == null || unitId.trim().isEmpty()) {
            return;
        }
        StatusRequest payload = new StatusRequest(status);
        executeVoid(api.updateUnitStatus(unitId, payload), "PATCH /v1/units/{id}/status", payload);
    }

    /**
     * Trigger server-side route repair for a unit.
     * @param unitId unit identifier
     */
    public void triggerRouteRepair(String unitId) {
        if (unitId == null || unitId.trim().isEmpty()) {
            return;
        }
        LOG.log(Level.INFO, "[API] POST /v1/units/{0}/route/repair", unitId);
        executeVoid(api.repairUnitRoute(unitId), "POST /v1/units/{id}/route/repair");
    }

    /**
     * Get the stored route for a unit.
     * @param unitId unit identifier
     * @return route info or null if not found
     */
    public UnitRouteInfo getUnitRoute(String unitId) {
        if (unitId == null || unitId.trim().isEmpty()) {
            return null;
        }
        // 404 is expected when route hasn't been calculated yet
        UnitRouteDto dto = execute(api.getUnitRoute(unitId), "GET /v1/units/{id}/route", true);
        if (dto == null) {
            return null;
        }
        return new UnitRouteInfo(
                dto.unitId,
                dto.interventionId,
                dto.routeLengthMeters,
                dto.estimatedDurationSeconds,
                dto.progressPercent,
                dto.currentLat,
                dto.currentLon,
                dto.severity
        );
    }

    /**
     * Update route progress and get interpolated position.
     * @param unitId unit identifier
     * @param progressPercent new progress (0-100)
     * @return progress update result with new position
     */
    public ProgressUpdateResult updateRouteProgress(String unitId, double progressPercent) {
        if (unitId == null || unitId.trim().isEmpty()) {
            return null;
        }
        ProgressRequest payload = new ProgressRequest(progressPercent);
        ProgressResponseDto dto = execute(api.updateRouteProgress(unitId, payload), "PATCH /v1/units/{id}/route/progress");
        if (dto == null) {
            return null;
        }
        return new ProgressUpdateResult(
                dto.unitId,
                dto.progressPercent,
                dto.currentLat,
                dto.currentLon,
                dto.remainingMeters,
                dto.remainingSeconds
        );
    }

    /**
     * Get all assignments for an intervention.
     * @param interventionId intervention identifier
     * @return list of assignments
     */
    public List<AssignmentInfo> getInterventionAssignments(String interventionId) {
        if (interventionId == null || interventionId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        InterventionDetailDto dto = execute(api.getIntervention(interventionId), "GET /v1/interventions/{id}");
        if (dto == null || dto.assignments == null) {
            return new ArrayList<>();
        }
        List<AssignmentInfo> result = new ArrayList<>();
        for (AssignmentDto a : dto.assignments) {
            result.add(new AssignmentInfo(a.id, a.unitId, a.status));
        }
        return result;
    }

    /**
     * Update assignment status by finding the assignment for a unit in an intervention.
     * @param interventionId intervention identifier
     * @param unitId unit identifier
     * @param status new status
     */
    public void updateAssignmentStatusByUnit(String interventionId, String unitId, String status) {
        List<AssignmentInfo> assignments = getInterventionAssignments(interventionId);
        for (AssignmentInfo a : assignments) {
            if (unitId.equals(a.unitId)) {
                updateAssignmentStatus(a.id, status);
                return;
            }
        }
        LOG.log(Level.WARNING, "[API] No assignment found for unit {0} in intervention {1}",
                new Object[]{unitId, interventionId});
    }

    /**
     * Update a unit location.
     * @param unitId unit identifier
     * @param lat latitude
     * @param lon longitude
     * @param recordedAt timestamp in UTC
     */
    public void updateUnitLocation(String unitId, double lat, double lon, Instant recordedAt) {
        if (unitId == null || unitId.trim().isEmpty()) {
            return;
        }
        LocationRequest payload = new LocationRequest(lat, lon, ISO.format(recordedAt));
        executeVoid(api.updateUnitLocation(unitId, payload), "PATCH /v1/units/{id}/location", payload);
    }

    public String pickUnitType() {
        if (unitTypeCodes.isEmpty()) {
            return null;
        }
        return unitTypeCodes.get(random.nextInt(unitTypeCodes.size()));
    }



    // ---- helpers ----

    private void loadEventTypes() {
        List<CodeDto> codes = execute(api.getEventTypes(), "GET /v1/event-types");
        if (codes != null) {
            for (CodeDto c : codes) {
                if (c.getCode() != null) {
                    eventTypeCodes.add(c.getCode());
                }
            }
        }
        LOG.log(Level.INFO, "[API] Event types: {0}", eventTypeCodes);
    }

    private String pickEventType() {
        if (eventTypeCodes.isEmpty()) {
            return null;
        }
        return eventTypeCodes.get(random.nextInt(eventTypeCodes.size()));
    }

    private void loadUnitTypes() {
        List<CodeDto> codes = execute(api.getUnitTypes(), "GET /v1/unit-types");
        if (codes != null) {
            for (CodeDto c : codes) {
                if (c.getCode() != null && !unitTypeCodes.contains(c.getCode())) {
                    unitTypeCodes.add(c.getCode());
                }
            }
        }
        LOG.log(Level.INFO, "[API] Unit types: {0}", unitTypeCodes);
    }

    private <T> T execute(Call<T> call, String action) {
        return execute(call, action, false);
    }

    private <T> T execute(Call<T> call, String action, boolean expect404) {
        try {
            Response<T> resp = call.execute();
            if (!resp.isSuccessful()) {
                // Only log at SEVERE if it's not an expected 404
                if (resp.code() == 404 && expect404) {
                    LOG.log(Level.FINE, "[API] {0} -> 404 (expected, no data)", action);
                } else {
                    LOG.log(Level.SEVERE, "[API] {0} -> {1} body={2}", new Object[]{action, resp.code(), errorBody(resp)});
                }
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            if (wasInterrupted(e)) {
                return null;
            }
            LOG.log(Level.SEVERE, e, () -> "[API] " + action + " error");
            return null;
        }
    }

    private void executeVoid(Call<Void> call, String action) {
        executeVoid(call, action, null);
    }

    private void executeVoid(Call<Void> call, String action, Object payload) {
        try {
            Response<Void> resp = call.execute();
            if (!resp.isSuccessful()) {
                LOG.log(Level.SEVERE, "[API] {0} -> {1} body={2} payload={3}",
                        new Object[]{action, resp.code(), errorBody(resp), serializePayload(payload)});
            }
        } catch (Exception e) {
            if (wasInterrupted(e)) {
                return;
            }
            LOG.log(Level.SEVERE, e, () -> "[API] " + action + " error payload=" + serializePayload(payload));
        }
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ignored) {
            return payload.toString();
        }
    }

    private boolean wasInterrupted(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return true;
        }
        return false;
    }

    private String errorBody(Response<?> resp) {
        try {
            return resp.errorBody() == null ? "" : resp.errorBody().string();
        } catch (IOException ignored) {
            return "";
        }
    }

    private interface ApiService {
        @GET("/v1/units")
        Call<List<UnitDto>> getUnits();

        @GET("/v1/event-types")
        Call<List<CodeDto>> getEventTypes();

        @GET("/v1/unit-types")
        Call<List<CodeDto>> getUnitTypes();

        @GET("/v1/buildings")
        Call<List<BuildingDto>> getBuildings();

        @POST("/v1/events")
        Call<IdDto> createEvent(@Body CreateEventRequest body);

        @POST("/v1/interventions")
        Call<IdDto> createIntervention(@Body CreateInterventionRequest body);

        @POST("/v1/units")
        Call<IdDto> createUnit(@Body CreateUnitRequest body);

        @POST("/v1/interventions/{interventionId}/assignments")
        Call<IdDto> assignUnit(@Path("interventionId") String interventionId, @Body AssignUnitRequest body);

        @PATCH("/v1/assignments/{assignmentId}/status")
        Call<Void> updateAssignmentStatus(@Path("assignmentId") String assignmentId, @Body StatusRequest body);

        @PATCH("/v1/interventions/{interventionId}/status")
        Call<Void> updateInterventionStatus(@Path("interventionId") String interventionId, @Body StatusRequest body);

        @PATCH("/v1/units/{unitId}/status")
        Call<Void> updateUnitStatus(@Path("unitId") String unitId, @Body StatusRequest body);

        @PATCH("/v1/units/{unitId}/location")
        Call<Void> updateUnitLocation(@Path("unitId") String unitId, @Body LocationRequest body);

        @POST("/v1/events/{eventId}/logs")
        Call<Void> logEvent(@Path("eventId") String eventId, @Body HeartbeatRequest body);

        @GET("/v1/units/{unitID}/route")
        Call<UnitRouteDto> getUnitRoute(@Path("unitID") String unitId);

        @PATCH("/v1/units/{unitID}/route/progress")
        Call<ProgressResponseDto> updateRouteProgress(@Path("unitID") String unitId, @Body ProgressRequest body);

        @POST("/v1/units/{unitID}/route/repair")
        Call<Void> repairUnitRoute(@Path("unitID") String unitId);

        @GET("/v1/interventions/{interventionId}")
        Call<InterventionDetailDto> getIntervention(@Path("interventionId") String interventionId);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class UnitDto {
        @JsonProperty("id")
        private String id;
        @JsonProperty("call_sign")
        private String callSign;
        @JsonProperty("home_base")
        private String homeBase;
        @JsonProperty("unit_type_code")
        private String unitTypeCode;
        @JsonProperty("status")
        private String status;
        @JsonProperty("latitude")
        private Double latitude;
        @JsonProperty("longitude")
        private Double longitude;
        @JsonProperty("location")
        private LocationDto location;

        public String getId() {
            return id;
        }

        public String getCallSign() {
            return callSign;
        }

        public String getHomeBase() {
            return homeBase;
        }

        public String getUnitTypeCode() {
            return unitTypeCode;
        }

        public String getStatus() {
            return status;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public LocationDto getLocation() {
            return location;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class BuildingDto {
        @JsonProperty("id")
        private String id;
        @JsonProperty("name")
        private String name;
        @JsonProperty("type")
        private String type;
        @JsonProperty("location")
        private LocationDto location;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class CodeDto {
        @JsonProperty("code")
        private String code;
        @JsonProperty("name")
        private String name;

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class IdDto {
        @JsonProperty("id")
        private String id;

        public String getId() {
            return id;
        }
    }

    private static final class CreateEventRequest {
        @JsonProperty("title")
        private final String title;
        @JsonProperty("event_type_code")
        private final String eventTypeCode;
        @JsonProperty("latitude")
        private final double latitude;
        @JsonProperty("longitude")
        private final double longitude;
        @JsonProperty("severity")
        private final int severity;
        @JsonProperty("report_source")
        private final String reportSource;

        CreateEventRequest(String title, String eventTypeCode, double latitude, double longitude, int severity, String reportSource) {
            this.title = title;
            this.eventTypeCode = eventTypeCode;
            this.latitude = latitude;
            this.longitude = longitude;
            this.severity = severity;
            this.reportSource = reportSource;
        }
    }

    private static final class CreateInterventionRequest {
        @JsonProperty("event_id")
        private final String eventId;
        @JsonProperty("priority")
        private final int priority;
        @JsonProperty("decision_mode")
        private final String decisionMode;

        CreateInterventionRequest(String eventId, int priority, String decisionMode) {
            this.eventId = eventId;
            this.priority = priority;
            this.decisionMode = decisionMode;
        }
    }

    private static final class AssignUnitRequest {
        @JsonProperty("unit_id")
        private final String unitId;
        @JsonProperty("role")
        private final String role;

        AssignUnitRequest(String unitId, String role) {
            this.unitId = unitId;
            this.role = role;
        }
    }

    private static final class StatusRequest {
        @JsonProperty("status")
        private final String status;

        StatusRequest(String status) {
            this.status = status;
        }
    }

    private static final class CreateUnitRequest {
        @JsonProperty("call_sign")
        private final String callSign;
        @JsonProperty("unit_type_code")
        private final String unitTypeCode;
        @JsonProperty("home_base")
        private final String homeBase;
        @JsonProperty("status")
        private final String status;
        @JsonProperty("latitude")
        private final double latitude;
        @JsonProperty("longitude")
        private final double longitude;

        CreateUnitRequest(String callSign, String unitTypeCode, String homeBase, String status, double latitude, double longitude) {
            this.callSign = callSign;
            this.unitTypeCode = unitTypeCode;
            this.homeBase = homeBase;
            this.status = status;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static final class LocationRequest {
        @JsonProperty("latitude")
        private final double latitude;
        @JsonProperty("longitude")
        private final double longitude;
        @JsonProperty("recorded_at")
        private final String recordedAt;

        LocationRequest(double latitude, double longitude, String recordedAt) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.recordedAt = recordedAt;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class LocationDto {
        @JsonProperty("latitude")
        private Double latitude;
        @JsonProperty("longitude")
        private Double longitude;

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }
    }

    private static final class HeartbeatRequest {
        public final String actor;
        public final String code;
        public final List<Object> payload;

        HeartbeatRequest(String actor, String code) {
            this.actor = actor;
            this.code = code;
            this.payload = new ArrayList<>();
        }
    }

    private static final class ProgressRequest {
        @JsonProperty("progress_percent")
        public final double progressPercent;

        ProgressRequest(double progressPercent) {
            this.progressPercent = progressPercent;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class UnitRouteDto {
        @JsonProperty("unit_id")
        String unitId;
        @JsonProperty("intervention_id")
        String interventionId;
        @JsonProperty("route_length_meters")
        double routeLengthMeters;
        @JsonProperty("estimated_duration_seconds")
        double estimatedDurationSeconds;
        @JsonProperty("progress_percent")
        double progressPercent;
        @JsonProperty("current_lat")
        Double currentLat;
        @JsonProperty("current_lon")
        Double currentLon;
        @JsonProperty("severity")
        Integer severity;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ProgressResponseDto {
        @JsonProperty("unit_id")
        String unitId;
        @JsonProperty("progress_percent")
        double progressPercent;
        @JsonProperty("current_lat")
        double currentLat;
        @JsonProperty("current_lon")
        double currentLon;
        @JsonProperty("remaining_meters")
        double remainingMeters;
        @JsonProperty("remaining_seconds")
        double remainingSeconds;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class InterventionDetailDto {
        @JsonProperty("id")
        String id;
        @JsonProperty("status")
        String status;
        @JsonProperty("assignments")
        List<AssignmentDto> assignments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class AssignmentDto {
        @JsonProperty("id")
        String id;
        @JsonProperty("unit_id")
        String unitId;
        @JsonProperty("status")
        String status;
    }

    private static String nvl(String v, String d) {
        return v == null ? d : v;
    }
}

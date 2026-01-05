package org.fastpinpon.simulation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.fastpinpon.simulation.model.Incident;
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
import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApiClient {
    private static final Logger LOG = Logger.getLogger(ApiClient.class.getName());
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter iso = DateTimeFormatter.ISO_INSTANT;
    private final Random random = new Random();
    private final List<String> eventTypeCodes = new ArrayList<>();
    public final List<String> unitTypeCodes = new ArrayList<>();
    private final ApiService api;

    public ApiClient(String baseUrlRaw) {
        String normalized = baseUrlRaw.endsWith("/") ? baseUrlRaw : baseUrlRaw + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalized)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .client(http)
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
            if (dto.id == null) {
                continue;
            }
            res.add(new UnitInfo(
                    dto.id,
                    nvl(dto.call_sign, dto.id),
                    nvl(dto.home_base, ""),
                    nvl(dto.unit_type_code, ""),
                    nvl(dto.status, ""),
                    dto.latitude,
                    dto.longitude
            ));
        }
        return res;
    }

    /**
     * Create an event from an incident.
     * @param inc incident to transform into an event
     * @return created event id or null on failure
     */
    public String createEvent(Incident inc) {
        String typeCode = pickEventType();
        if (typeCode == null) {
            LOG.warning("[API] No event type code available; cannot create event.");
            return null;
        }
        String title = "SIM-" + inc.type + "-" + inc.id.toString().substring(0, 8);
        CreateEventRequest payload = new CreateEventRequest(title, typeCode, inc.lat, inc.lon, inc.gravite, "simulation");
        IdDto created = execute(api.createEvent(payload), "POST /v1/events");
        if (created != null && created.id != null) {
            LOG.log(Level.INFO, "[API] Event created (id={0})", created.id);
            return created.id;
        }
        return null;
    }

    /**
     * Create an intervention linked to an event.
     * @param eventId event identifier
     * @param priority intervention priority
     * @return created intervention id or null
     */
    public String createIntervention(String eventId, int priority) {
        IdDto created = execute(api.createIntervention(new CreateInterventionRequest(eventId, priority, "auto_suggested")), "POST /v1/interventions");
        return created == null ? null : created.id;
    }

    /**
     * Assign a unit to an intervention.
     * @param interventionId intervention identifier
     * @param unitId unit identifier
     * @param role assignment role
     * @return assignment id or null
     */
    public String assignUnit(String interventionId, String unitId, String role) {
        IdDto created = execute(api.assignUnit(interventionId, new AssignUnitRequest(unitId, role)), "POST /v1/interventions/{id}/assignments");
        return created == null ? null : created.id;
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
        executeVoid(api.updateAssignmentStatus(assignmentId, new StatusRequest(status)), "PATCH /v1/assignments/{id}/status");
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
        executeVoid(api.updateInterventionStatus(interventionId, new StatusRequest(status)), "PATCH /v1/interventions/{id}/status");
    }

    /**
     * Update an event status.
     * @param eventId event identifier
     * @param status new status value
     */
    public void updateEventStatus(String eventId, String status) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        executeVoid(api.updateEventStatus(eventId, new StatusRequest(status)), "PATCH /v1/events/{id}/status");
    }

    /**
     * Send a heartbeat log for an event.
     * @param eventId event identifier
     */
    public void logHeartbeat(String eventId) {
        if (eventId == null) {
            return;
        }
        HeartbeatRequest payload = new HeartbeatRequest("simulator", "heartbeat");
        executeVoid(api.logEvent(eventId, payload), "POST /v1/events/{id}/logs");
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
        executeVoid(api.updateUnitStatus(unitId, new StatusRequest(status)), "PATCH /v1/units/{id}/status");
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
        LocationRequest payload = new LocationRequest(lat, lon, iso.format(recordedAt));
        executeVoid(api.updateUnitLocation(unitId, payload), "PATCH /v1/units/{id}/location");
    }

    public String pickUnitType() {
        if (unitTypeCodes.isEmpty()) {
            return null;
        }
        return unitTypeCodes.get(random.nextInt(unitTypeCodes.size()));
    }

    /**
     * Create a unit.
     * @param callSign unit call sign
     * @param unitTypeCode unit type code
     * @param homeBase home base label
     * @param lat latitude
     * @param lon longitude
     * @return created unit info or null
     */
    public UnitInfo createUnit(String callSign, String unitTypeCode, String homeBase, double lat, double lon) {
        CreateUnitRequest payload = new CreateUnitRequest(callSign, unitTypeCode, homeBase, "available", lat, lon);
        IdDto created = execute(api.createUnit(payload), "POST /v1/units");
        if (created == null || created.id == null) {
            return null;
        }
        return new UnitInfo(created.id, callSign, homeBase, unitTypeCode, "available", lat, lon);
    }

    // ---- helpers ----

    private void loadEventTypes() {
        List<CodeDto> codes = execute(api.getEventTypes(), "GET /v1/event-types");
        if (codes != null) {
            for (CodeDto c : codes) {
                if (c.code != null) {
                    eventTypeCodes.add(c.code);
                }
            }
        }
        LOG.info("[API] Event types: " + eventTypeCodes);
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
                if (c.code != null && !unitTypeCodes.contains(c.code)) {
                    unitTypeCodes.add(c.code);
                }
            }
        }
        LOG.info("[API] Unit types: " + unitTypeCodes);
    }

    private <T> T execute(Call<T> call, String action) {
        try {
            Response<T> resp = call.execute();
            if (!resp.isSuccessful()) {
                LOG.log(Level.SEVERE, MessageFormat.format("[API] {0} -> {1} body={2}", action, resp.code(), errorBody(resp)));
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            if (wasInterrupted(e)) {
                return null;
            }
            LOG.log(Level.SEVERE, "[API] " + action + " error", e);
            return null;
        }
    }

    private void executeVoid(Call<Void> call, String action) {
        try {
            Response<Void> resp = call.execute();
            if (!resp.isSuccessful()) {
                LOG.log(Level.SEVERE, MessageFormat.format("[API] {0} -> {1} body={2}", action, resp.code(), errorBody(resp)));
            }
        } catch (Exception e) {
            if (wasInterrupted(e)) {
                return;
            }
            LOG.log(Level.SEVERE, "[API] " + action + " error", e);
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

        @POST("/v1/events")
        Call<IdDto> createEvent(@Body CreateEventRequest body);

        @POST("/v1/interventions")
        Call<IdDto> createIntervention(@Body CreateInterventionRequest body);

        @POST("/v1/interventions/{interventionId}/assignments")
        Call<IdDto> assignUnit(@Path("interventionId") String interventionId, @Body AssignUnitRequest body);

        @PATCH("/v1/assignments/{assignmentId}/status")
        Call<Void> updateAssignmentStatus(@Path("assignmentId") String assignmentId, @Body StatusRequest body);

        @PATCH("/v1/interventions/{interventionId}/status")
        Call<Void> updateInterventionStatus(@Path("interventionId") String interventionId, @Body StatusRequest body);

        @PATCH("/v1/events/{eventId}/status")
        Call<Void> updateEventStatus(@Path("eventId") String eventId, @Body StatusRequest body);

        @PATCH("/v1/units/{unitId}/status")
        Call<Void> updateUnitStatus(@Path("unitId") String unitId, @Body StatusRequest body);

        @PATCH("/v1/units/{unitId}/location")
        Call<Void> updateUnitLocation(@Path("unitId") String unitId, @Body LocationRequest body);

        @POST("/v1/events/{eventId}/logs")
        Call<Void> logEvent(@Path("eventId") String eventId, @Body HeartbeatRequest body);
    }

    private static final class UnitDto {
        public String id;
        public String call_sign;
        public String home_base;
        public String unit_type_code;
        public String status;
        public Double latitude;
        public Double longitude;
    }

    private static final class CodeDto {
        public String code;
    }

    private static final class IdDto {
        public String id;
    }

    private static final class CreateEventRequest {
        public final String title;
        public final String event_type_code;
        public final double latitude;
        public final double longitude;
        public final int severity;
        public final String report_source;

        CreateEventRequest(String title, String eventTypeCode, double latitude, double longitude, int severity, String reportSource) {
            this.title = title;
            this.event_type_code = eventTypeCode;
            this.latitude = latitude;
            this.longitude = longitude;
            this.severity = severity;
            this.report_source = reportSource;
        }
    }

    private static final class CreateInterventionRequest {
        public final String event_id;
        public final int priority;
        public final String decision_mode;

        CreateInterventionRequest(String eventId, int priority, String decisionMode) {
            this.event_id = eventId;
            this.priority = priority;
            this.decision_mode = decisionMode;
        }
    }

    private static final class AssignUnitRequest {
        public final String unit_id;
        public final String role;

        AssignUnitRequest(String unitId, String role) {
            this.unit_id = unitId;
            this.role = role;
        }
    }

    private static final class StatusRequest {
        public final String status;

        StatusRequest(String status) {
            this.status = status;
        }
    }

    private static final class LocationRequest {
        public final double latitude;
        public final double longitude;
        public final String recorded_at;

        LocationRequest(double latitude, double longitude, String recordedAt) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.recorded_at = recordedAt;
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

    private static String nvl(String v, String d) {
        return v == null ? d : v;
    }
}

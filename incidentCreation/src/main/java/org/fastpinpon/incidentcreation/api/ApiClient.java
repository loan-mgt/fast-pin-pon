package org.fastpinpon.incidentcreation.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.fastpinpon.incidentcreation.model.Incident;
import org.fastpinpon.incidentcreation.model.IncidentType;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API client for creating events in the Fast Pin Pon backend.
 * This is a simplified version focused only on incident creation.
 */
public final class ApiClient {
    private static final Logger LOG = Logger.getLogger(ApiClient.class.getName());
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Random random = new Random();
    private final List<String> eventTypeCodes = new ArrayList<>();
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
    }

    /**
     * Create an event from an Incident object.
     * 
     * @param incident the incident to create as an event
     * @return created event id or null on failure
     */
    public String createEvent(Incident incident) {
        return createEvent(
                incident.getType(),
                incident.getNumber(),
                incident.getLat(),
                incident.getLon(),
                incident.getGravite()
        );
    }

    /**
     * Create an event from raw parameters.
     * 
     * @param type incident type
     * @param number incident sequence number
     * @param lat latitude
     * @param lon longitude
     * @param severity gravity/severity level
     * @return created event id or null on failure
     */
    public String createEvent(IncidentType type, int number, double lat, double lon, int severity) {
        String typeCode = pickEventType();
        if (typeCode == null) {
            LOG.warning("[API] No event type code available; cannot create event.");
            return null;
        }
        String title = "SIM-" + type + "-" + number;
        CreateEventRequest payload = new CreateEventRequest(title, typeCode, lat, lon, severity, "incident-generator");
        IdDto created = execute(api.createEvent(payload, "true"), "POST /v1/events");
        if (created != null && created.getId() != null) {
            LOG.log(Level.INFO, "[API] Event created (id={0})", created.getId());
            return created.getId();
        }
        return null;
    }

    /**
     * Check if the API is reachable by loading event types.
     * 
     * @return true if event types were loaded successfully
     */
    public boolean isAvailable() {
        return !eventTypeCodes.isEmpty();
    }

    /**
     * Wait for API to become available with retries.
     * 
     * @param maxRetries maximum number of retries
     * @param retryDelayMs delay between retries in milliseconds
     * @return true if API became available
     */
    public boolean waitForApi(int maxRetries, long retryDelayMs) {
        for (int i = 0; i < maxRetries; i++) {
            loadEventTypes();
            if (isAvailable()) {
                return true;
            }
            LOG.log(Level.INFO, "[API] Waiting for API to become available... ({0}/{1})", new Object[]{i + 1, maxRetries});
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isAvailable();
    }

    // ---- helpers ----

    private void loadEventTypes() {
        eventTypeCodes.clear();
        List<CodeDto> codes = execute(api.getEventTypes(), "GET /v1/event-types");
        if (codes != null) {
            for (CodeDto c : codes) {
                if (c.getCode() != null) {
                    eventTypeCodes.add(c.getCode());
                }
            }
        }
        if (!eventTypeCodes.isEmpty()) {
            LOG.log(Level.INFO, "[API] Event types: {0}", eventTypeCodes);
        }
    }

    private String pickEventType() {
        if (eventTypeCodes.isEmpty()) {
            return null;
        }
        return eventTypeCodes.get(random.nextInt(eventTypeCodes.size()));
    }

    private <T> T execute(Call<T> call, String action) {
        try {
            Response<T> resp = call.execute();
            if (!resp.isSuccessful()) {
                LOG.log(Level.SEVERE, "[API] {0} -> {1} body={2}", new Object[]{action, resp.code(), errorBody(resp)});
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

    // ---- Retrofit service interface ----

    private interface ApiService {
        @GET("/v1/event-types")
        Call<List<CodeDto>> getEventTypes();

        @POST("/v1/events")
        Call<IdDto> createEvent(@Body CreateEventRequest body, @Query("auto_intervention") String autoIntervention);
    }

    // ---- DTOs ----

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
}

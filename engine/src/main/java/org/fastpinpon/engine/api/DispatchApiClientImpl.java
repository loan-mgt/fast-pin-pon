package org.fastpinpon.engine.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.fastpinpon.engine.api.dto.CandidatesResponseDto;
import org.fastpinpon.engine.api.dto.PendingInterventionsDto;
import org.fastpinpon.engine.api.dto.StaticDataDto;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrofit-based implementation of DispatchApiClient.
 */
public final class DispatchApiClientImpl implements DispatchApiClient {

    private static final Logger LOG = Logger.getLogger(DispatchApiClientImpl.class.getName());

    private final DispatchApiService api;

    public DispatchApiClientImpl(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        
        String normalizedUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
                .client(client)
                .build();

        this.api = retrofit.create(DispatchApiService.class);
    }

    @Override
    public StaticDataDto getStaticData() {
        return execute(api.getStaticData(), "GET /v1/dispatch/static");
    }

    @Override
    public CandidatesResponseDto getCandidates(String interventionId) {
        return execute(api.getCandidates(interventionId), "GET /v1/interventions/{id}/candidates");
    }

    @Override
    public PendingInterventionsDto getPendingInterventions() {
        return execute(api.getPendingInterventions(), "GET /v1/dispatch/pending");
    }

    @Override
    public String assignUnit(String interventionId, String unitId, String role) {
        AssignUnitRequest request = new AssignUnitRequest(unitId, role);
        IdResponse response = execute(api.assignUnit(interventionId, request), "POST /v1/interventions/{id}/assignments");
        return response != null ? response.getId() : null;
    }

    @Override
    public void releaseAssignment(String assignmentId) {
        StatusRequest request = new StatusRequest("released");
        executeVoid(api.updateAssignmentStatus(assignmentId, request), "PATCH /v1/assignments/{id}/status");
    }

    @Override
    public void updateUnitStatus(String unitId, String status) {
        StatusRequest request = new StatusRequest(status);
        executeVoid(api.updateUnitStatus(unitId, request), "PATCH /v1/units/{id}/status");
    }

    /**
     * Execute a Retrofit call and return the result.
     */
    private <T> T execute(Call<T> call, String description) {
        try {
            Response<T> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            LOG.warning(() -> String.format("[API] %s failed: %d %s",
                    description, response.code(), response.message()));
            return null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[API] " + description + " error", e);
            return null;
        }
    }

    /**
     * Execute a Retrofit call that returns void.
     */
    private void executeVoid(Call<Void> call, String description) {
        try {
            Response<Void> response = call.execute();
            if (!response.isSuccessful()) {
                LOG.warning(() -> String.format("[API] %s failed: %d %s",
                        description, response.code(), response.message()));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[API] " + description + " error", e);
        }
    }

    /**
     * Retrofit service interface for dispatch API.
     */
    interface DispatchApiService {
        @GET("v1/dispatch/static")
        Call<StaticDataDto> getStaticData();

        @GET("v1/interventions/{interventionId}/candidates")
        Call<CandidatesResponseDto> getCandidates(@Path("interventionId") String interventionId);

        @GET("v1/dispatch/pending")
        Call<PendingInterventionsDto> getPendingInterventions();

        @POST("v1/interventions/{interventionId}/assignments")
        Call<IdResponse> assignUnit(@Path("interventionId") String interventionId, @Body AssignUnitRequest request);

        @PATCH("v1/assignments/{assignmentId}/status")
        Call<Void> updateAssignmentStatus(@Path("assignmentId") String assignmentId, @Body StatusRequest request);

        @PATCH("v1/units/{unitId}/status")
        Call<Void> updateUnitStatus(@Path("unitId") String unitId, @Body StatusRequest request);
    }

    /**
     * Request DTO for assigning a unit.
     */
    static final class AssignUnitRequest {
        @JsonProperty("unit_id")
        private final String unitId;

        @JsonProperty("role")
        private final String role;

        AssignUnitRequest(String unitId, String role) {
            this.unitId = unitId;
            this.role = role;
        }

        public String getUnitId() {
            return unitId;
        }

        public String getRole() {
            return role;
        }
    }

    /**
     * Request DTO for status updates.
     */
    static final class StatusRequest {
        @JsonProperty("status")
        private final String status;

        StatusRequest(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }

    /**
     * Response DTO containing an ID.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class IdResponse {
        @JsonProperty("id")
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}

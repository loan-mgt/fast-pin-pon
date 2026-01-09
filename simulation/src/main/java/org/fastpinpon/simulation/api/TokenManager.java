package org.fastpinpon.simulation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages Keycloak authentication tokens.
 * Fetches and caches access tokens using client credentials flow.
 */
public class TokenManager {
    private static final Logger LOG = Logger.getLogger(TokenManager.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final OkHttpClient httpClient;

    private String accessToken;
    private long expiryTime;

    public TokenManager(String tokenUrl, String clientId, String clientSecret) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = new OkHttpClient();
    }

    /**
     * Returns a valid access token, refreshing it if necessary.
     */
    public synchronized String getAccessToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < expiryTime) {
            return accessToken;
        }
        return fetchToken();
    }

    private String fetchToken() throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();

        Request request = new Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown error";
                throw new IOException("Failed to fetch token: " + response.code() + " - " + errorBody);
            }

            if (response.body() == null) {
                throw new IOException("Empty response body from token endpoint");
            }

            JsonNode root = MAPPER.readTree(response.body().byteStream());
            this.accessToken = root.path("access_token").asText();
            int expiresIn = root.path("expires_in").asInt();
            
            // Set expiry time with a 10s buffer
            this.expiryTime = System.currentTimeMillis() + (expiresIn - 10) * 1000L;
            
            LOG.info("[TokenManager] Fetched new access token, expires in " + expiresIn + "s");
            return accessToken;
        }
    }
}

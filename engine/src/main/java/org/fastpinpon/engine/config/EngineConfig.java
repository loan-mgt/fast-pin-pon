package org.fastpinpon.engine.config;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Immutable configuration for the Decision Engine.
 * All values are read from environment variables with sensible defaults.
 */
public final class EngineConfig {
    
    private static final Logger LOG = Logger.getLogger(EngineConfig.class.getName());

    public static final String DEFAULT_API_URL = "http://localhost:8081";
    public static final int DEFAULT_CALLBACK_PORT = 8082;
    public static final int DEFAULT_DISPATCH_INTERVAL = 30;
    public static final String DEFAULT_LOG_FILE = "/app/logs/engine/engine.log";

    // API Configuration
    private final String apiBaseUrl;
    
    // Keycloak Configuration
    private final String keycloakUrl;
    private final String keycloakRealm;
    private final String clientId;
    private final String clientSecret;
    
    // Callback Server Configuration
    private final int callbackPort;
    
    // Scheduler Configuration
    private final int dispatchIntervalSeconds;
    private final boolean schedulerEnabled;
    
    // Logging Configuration
    private final String logFilePath;
    private final boolean fileLoggingEnabled;

    private EngineConfig(Builder builder) {
        this.apiBaseUrl = builder.apiBaseUrl;
        this.keycloakUrl = builder.keycloakUrl;
        this.keycloakRealm = builder.keycloakRealm;
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.callbackPort = builder.callbackPort;
        this.dispatchIntervalSeconds = builder.dispatchIntervalSeconds;
        this.schedulerEnabled = builder.schedulerEnabled;
        this.logFilePath = builder.logFilePath;
        this.fileLoggingEnabled = builder.fileLoggingEnabled;
    }

    /**
     * Creates configuration from environment variables.
     */
    public static EngineConfig fromEnvironment() {
        return new Builder()
                .apiBaseUrl(getEnv("API_BASE_URL", DEFAULT_API_URL))
                .keycloakUrl(getEnv("KEYCLOAK_URL", ""))
                .keycloakRealm(getEnv("KEYCLOAK_REALM", ""))
                .clientId(getEnv("KEYCLOAK_CLIENT_ID", ""))
                .clientSecret(getEnv("KEYCLOAK_CLIENT_SECRET", ""))
                .callbackPort(getEnvInt("ENGINE_CALLBACK_PORT", DEFAULT_CALLBACK_PORT))
                .dispatchIntervalSeconds(getEnvInt("DISPATCH_INTERVAL_SECONDS", DEFAULT_DISPATCH_INTERVAL))
                .schedulerEnabled(getEnvBoolean("DISPATCH_SCHEDULER_ENABLED", true))
                .logFilePath(getEnv("ENGINE_LOG_FILE", DEFAULT_LOG_FILE))
                .fileLoggingEnabled(getEnvBoolean("ENGINE_FILE_LOGGING_ENABLED", true))
                .build();
    }

    // Getters
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getKeycloakUrl() {
        return keycloakUrl;
    }

    public String getKeycloakRealm() {
        return keycloakRealm;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public int getCallbackPort() {
        return callbackPort;
    }

    public int getDispatchIntervalSeconds() {
        return dispatchIntervalSeconds;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public boolean isFileLoggingEnabled() {
        return fileLoggingEnabled;
    }

    // Environment variable helpers
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            LOG.fine(() -> String.format("Using default for %s: %s", key, defaultValue));
            return defaultValue;
        }
        return value.trim();
    }

    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warning(() -> String.format("Invalid integer for %s: %s, using default: %d", key, value, defaultValue));
            return defaultValue;
        }
    }

    private static boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    @Override
    public String toString() {
        return "EngineConfig{" +
                "apiBaseUrl='" + apiBaseUrl + '\'' +
                ", keycloakUrl='" + keycloakUrl + '\'' +
                ", callbackPort=" + callbackPort +
                ", dispatchIntervalSeconds=" + dispatchIntervalSeconds +
                ", schedulerEnabled=" + schedulerEnabled +
                ", fileLoggingEnabled=" + fileLoggingEnabled +
                '}';
    }

    /**
     * Builder for EngineConfig.
     */
    public static final class Builder {
        private String apiBaseUrl = DEFAULT_API_URL;
        private String keycloakUrl = "";
        private String keycloakRealm = "";
        private String clientId = "";
        private String clientSecret = "";
        private int callbackPort = DEFAULT_CALLBACK_PORT;
        private int dispatchIntervalSeconds = DEFAULT_DISPATCH_INTERVAL;
        private boolean schedulerEnabled = true;
        private String logFilePath = DEFAULT_LOG_FILE;
        private boolean fileLoggingEnabled = true;

        public Builder apiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
            return this;
        }

        public Builder keycloakUrl(String keycloakUrl) {
            this.keycloakUrl = keycloakUrl;
            return this;
        }

        public Builder keycloakRealm(String keycloakRealm) {
            this.keycloakRealm = keycloakRealm;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder callbackPort(int callbackPort) {
            if (callbackPort <= 0 || callbackPort > 65535) {
                throw new IllegalArgumentException("callbackPort must be between 1 and 65535");
            }
            this.callbackPort = callbackPort;
            return this;
        }

        public Builder dispatchIntervalSeconds(int dispatchIntervalSeconds) {
            if (dispatchIntervalSeconds < 1) {
                throw new IllegalArgumentException("dispatchIntervalSeconds must be at least 1");
            }
            this.dispatchIntervalSeconds = dispatchIntervalSeconds;
            return this;
        }

        public Builder schedulerEnabled(boolean schedulerEnabled) {
            this.schedulerEnabled = schedulerEnabled;
            return this;
        }

        public Builder logFilePath(String logFilePath) {
            this.logFilePath = Objects.requireNonNull(logFilePath, "logFilePath must not be null");
            return this;
        }

        public Builder fileLoggingEnabled(boolean fileLoggingEnabled) {
            this.fileLoggingEnabled = fileLoggingEnabled;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(this);
        }
    }
}

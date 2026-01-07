package org.fastpinpon.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for GET /v1/dispatch/static endpoint.
 * Contains all static data needed for engine initialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StaticDataDto {

    @JsonProperty("config")
    private List<ConfigItemDto> config;

    @JsonProperty("unit_types")
    private List<UnitTypeDto> unitTypes;

    @JsonProperty("event_types")
    private List<EventTypeDto> eventTypes;

    @JsonProperty("bases")
    private List<BaseDto> bases;

    public List<ConfigItemDto> getConfig() {
        return config;
    }

    public void setConfig(List<ConfigItemDto> config) {
        this.config = config;
    }

    public List<UnitTypeDto> getUnitTypes() {
        return unitTypes;
    }

    public void setUnitTypes(List<UnitTypeDto> unitTypes) {
        this.unitTypes = unitTypes;
    }

    public List<EventTypeDto> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<EventTypeDto> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public List<BaseDto> getBases() {
        return bases;
    }

    public void setBases(List<BaseDto> bases) {
        this.bases = bases;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ConfigItemDto {
        @JsonProperty("key")
        private String key;

        @JsonProperty("value")
        private double value;

        @JsonProperty("description")
        private String description;

        @JsonProperty("min_value")
        private Double minValue;

        @JsonProperty("max_value")
        private Double maxValue;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Double getMinValue() {
            return minValue;
        }

        public void setMinValue(Double minValue) {
            this.minValue = minValue;
        }

        public Double getMaxValue() {
            return maxValue;
        }

        public void setMaxValue(Double maxValue) {
            this.maxValue = maxValue;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class UnitTypeDto {
        @JsonProperty("code")
        private String code;

        @JsonProperty("name")
        private String name;

        @JsonProperty("capabilities")
        private String capabilities;

        @JsonProperty("speed_kmh")
        private Integer speedKmh;

        @JsonProperty("max_crew")
        private Integer maxCrew;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(String capabilities) {
            this.capabilities = capabilities;
        }

        public Integer getSpeedKmh() {
            return speedKmh;
        }

        public void setSpeedKmh(Integer speedKmh) {
            this.speedKmh = speedKmh;
        }

        public Integer getMaxCrew() {
            return maxCrew;
        }

        public void setMaxCrew(Integer maxCrew) {
            this.maxCrew = maxCrew;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class EventTypeDto {
        @JsonProperty("code")
        private String code;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("default_severity")
        private int defaultSeverity;

        @JsonProperty("recommended_unit_types")
        private List<String> recommendedUnitTypes;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getDefaultSeverity() {
            return defaultSeverity;
        }

        public void setDefaultSeverity(int defaultSeverity) {
            this.defaultSeverity = defaultSeverity;
        }

        public List<String> getRecommendedUnitTypes() {
            return recommendedUnitTypes;
        }

        public void setRecommendedUnitTypes(List<String> recommendedUnitTypes) {
            this.recommendedUnitTypes = recommendedUnitTypes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BaseDto {
        @JsonProperty("name")
        private String name;

        @JsonProperty("available_units")
        private long availableUnits;

        @JsonProperty("total_units")
        private long totalUnits;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getAvailableUnits() {
            return availableUnits;
        }

        public void setAvailableUnits(long availableUnits) {
            this.availableUnits = availableUnits;
        }

        public long getTotalUnits() {
            return totalUnits;
        }

        public void setTotalUnits(long totalUnits) {
            this.totalUnits = totalUnits;
        }
    }
}

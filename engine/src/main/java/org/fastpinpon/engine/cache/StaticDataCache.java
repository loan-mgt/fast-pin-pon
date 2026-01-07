package org.fastpinpon.engine.cache;

import org.fastpinpon.engine.api.dto.StaticDataDto;
import org.fastpinpon.engine.domain.model.DispatchConfig;

import java.util.List;
import java.util.Map;

/**
 * Interface for caching static data from the API.
 */
public interface StaticDataCache {

    /**
     * Load or refresh static data from the API.
     */
    void refresh();

    /**
     * Get the dispatch configuration.
     */
    DispatchConfig getConfig();

    /**
     * Get unit types by code.
     */
    Map<String, StaticDataDto.UnitTypeDto> getUnitTypes();

    /**
     * Get event types by code.
     */
    Map<String, StaticDataDto.EventTypeDto> getEventTypes();

    /**
     * Get base information by name.
     */
    Map<String, StaticDataDto.BaseDto> getBases();

    /**
     * Get recommended unit types for an event type.
     */
    List<String> getRecommendedUnitTypes(String eventTypeCode);

    /**
     * Check if the cache has been initialized.
     */
    boolean isInitialized();
}

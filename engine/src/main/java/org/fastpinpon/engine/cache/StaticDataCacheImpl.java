package org.fastpinpon.engine.cache;

import org.fastpinpon.engine.api.DispatchApiClient;
import org.fastpinpon.engine.api.dto.StaticDataDto;
import org.fastpinpon.engine.domain.model.DispatchConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe implementation of StaticDataCache.
 * Uses read-write lock for concurrent access with exclusive writes.
 */
public final class StaticDataCacheImpl implements StaticDataCache {

    private static final Logger LOG = Logger.getLogger(StaticDataCacheImpl.class.getName());

    private final DispatchApiClient apiClient;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile boolean initialized = false;
    private DispatchConfig config = DispatchConfig.defaults();
    private Map<String, StaticDataDto.UnitTypeDto> unitTypes = Collections.emptyMap();
    private Map<String, StaticDataDto.EventTypeDto> eventTypes = Collections.emptyMap();
    private Map<String, StaticDataDto.BaseDto> bases = Collections.emptyMap();

    public StaticDataCacheImpl(DispatchApiClient apiClient) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
    }

    @Override
    public void refresh() {
        LOG.info("Refreshing static data cache from API...");
        
        try {
            StaticDataDto data = apiClient.getStaticData();
            if (data == null) {
                LOG.warning("API returned null static data, keeping existing cache");
                return;
            }

            lock.writeLock().lock();
            try {
                // Parse config
                if (data.getConfig() != null) {
                    Map<String, Double> configMap = new HashMap<>();
                    for (StaticDataDto.ConfigItemDto item : data.getConfig()) {
                        configMap.put(item.getKey(), item.getValue());
                    }
                    this.config = DispatchConfig.fromMap(configMap);
                    LOG.info(() -> "Loaded " + configMap.size() + " config values");
                }

                // Parse unit types
                if (data.getUnitTypes() != null) {
                    Map<String, StaticDataDto.UnitTypeDto> utMap = new HashMap<>();
                    for (StaticDataDto.UnitTypeDto ut : data.getUnitTypes()) {
                        utMap.put(ut.getCode(), ut);
                    }
                    this.unitTypes = Collections.unmodifiableMap(utMap);
                    LOG.info(() -> "Loaded " + utMap.size() + " unit types");
                }

                // Parse event types
                if (data.getEventTypes() != null) {
                    Map<String, StaticDataDto.EventTypeDto> etMap = new HashMap<>();
                    for (StaticDataDto.EventTypeDto et : data.getEventTypes()) {
                        etMap.put(et.getCode(), et);
                    }
                    this.eventTypes = Collections.unmodifiableMap(etMap);
                    LOG.info(() -> "Loaded " + etMap.size() + " event types");
                }

                // Parse bases
                if (data.getBases() != null) {
                    Map<String, StaticDataDto.BaseDto> baseMap = new HashMap<>();
                    for (StaticDataDto.BaseDto base : data.getBases()) {
                        baseMap.put(base.getName(), base);
                    }
                    this.bases = Collections.unmodifiableMap(baseMap);
                    LOG.info(() -> "Loaded " + baseMap.size() + " bases");
                }

                this.initialized = true;
                LOG.info("Static data cache refresh complete");

            } finally {
                lock.writeLock().unlock();
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to refresh static data cache", e);
            // Keep existing data on failure
        }
    }

    @Override
    public DispatchConfig getConfig() {
        lock.readLock().lock();
        try {
            return config;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, StaticDataDto.UnitTypeDto> getUnitTypes() {
        lock.readLock().lock();
        try {
            return unitTypes;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, StaticDataDto.EventTypeDto> getEventTypes() {
        lock.readLock().lock();
        try {
            return eventTypes;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, StaticDataDto.BaseDto> getBases() {
        lock.readLock().lock();
        try {
            return bases;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<String> getRecommendedUnitTypes(String eventTypeCode) {
        lock.readLock().lock();
        try {
            StaticDataDto.EventTypeDto eventType = eventTypes.get(eventTypeCode);
            if (eventType == null || eventType.getRecommendedUnitTypes() == null) {
                return Collections.emptyList();
            }
            return eventType.getRecommendedUnitTypes();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}

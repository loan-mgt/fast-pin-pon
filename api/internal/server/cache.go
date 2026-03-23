package server

import (
	"context"
	"encoding/json"
	"sync"
	"time"

	"github.com/rs/zerolog"
)

// ----- Generic TTL cache -------------------------------------------------------

// cacheEntry stores a cached value alongside its expiration time.
type cacheEntry struct {
	data      []byte // pre-marshalled JSON
	expiresAt time.Time
}

// TTLCache is a goroutine-safe, expiry-aware, in-memory cache.
// It is designed for small, mostly-static datasets (event_types, unit_types,
// locations, dispatch_config) that rarely change but are read on every request.
type TTLCache struct {
	mu      sync.RWMutex
	entries map[string]cacheEntry
	ttl     time.Duration
	log     zerolog.Logger
}

// NewTTLCache creates a cache whose entries expire after ttl.
func NewTTLCache(ttl time.Duration, log zerolog.Logger) *TTLCache {
	return &TTLCache{
		entries: make(map[string]cacheEntry),
		ttl:     ttl,
		log:     log.With().Str("component", "cache").Logger(),
	}
}

// Get returns the cached JSON bytes for key, or nil if absent/expired.
func (c *TTLCache) Get(key string) []byte {
	c.mu.RLock()
	e, ok := c.entries[key]
	c.mu.RUnlock()
	if !ok || time.Now().After(e.expiresAt) {
		return nil
	}
	return e.data
}

// Set stores pre-marshalled JSON bytes under key.
func (c *TTLCache) Set(key string, data []byte) {
	c.mu.Lock()
	c.entries[key] = cacheEntry{data: data, expiresAt: time.Now().Add(c.ttl)}
	c.mu.Unlock()
	c.log.Debug().Str("key", key).Dur("ttl", c.ttl).Msg("cache set")
}

// Invalidate removes a single key from the cache.
func (c *TTLCache) Invalidate(key string) {
	c.mu.Lock()
	delete(c.entries, key)
	c.mu.Unlock()
	c.log.Debug().Str("key", key).Msg("cache invalidated")
}

// InvalidateAll clears every entry.
func (c *TTLCache) InvalidateAll() {
	c.mu.Lock()
	c.entries = make(map[string]cacheEntry)
	c.mu.Unlock()
	c.log.Debug().Msg("cache cleared")
}

// ----- Cache keys used throughout handlers ------------------------------------

const (
	CacheKeyEventTypes     = "event_types"
	CacheKeyUnitTypes      = "unit_types"
	CacheKeyLocations      = "locations"
	CacheKeyDispatchCfg    = "dispatch_config"
	CacheKeyDispatchStatic = "dispatch_static"
)

// ----- Helper: GetOrFetch pattern --------------------------------------------

// GetOrFetch returns cached JSON for key.  On miss it calls fetch, marshals the
// result, stores it, and returns the bytes.  The caller can then write the bytes
// directly to the response writer, skipping a redundant json.Encode round-trip.
func GetOrFetch[T any](c *TTLCache, key string, ctx context.Context, fetch func(context.Context) (T, error)) ([]byte, error) {
	if data := c.Get(key); data != nil {
		return data, nil
	}

	val, err := fetch(ctx)
	if err != nil {
		return nil, err
	}

	data, err := json.Marshal(val)
	if err != nil {
		return nil, err
	}

	c.Set(key, data)
	return data, nil
}

-- =============================================================================
-- Dispatch Configuration Queries
-- =============================================================================

-- name: ListDispatchConfig :many
SELECT 
    key, 
    value, 
    description, 
    min_value, 
    max_value, 
    updated_at
FROM dispatch_config
ORDER BY key;

-- name: GetDispatchConfigValue :one
SELECT 
    key, 
    value, 
    description, 
    min_value, 
    max_value, 
    updated_at
FROM dispatch_config
WHERE key = $1;

-- name: UpdateDispatchConfigValue :one
UPDATE dispatch_config
SET 
    value = $2,
    updated_at = NOW()
WHERE key = $1
RETURNING key, value, description, min_value, max_value, updated_at;

-- name: BatchUpdateDispatchConfig :exec
UPDATE dispatch_config
SET 
    value = updates.value,
    updated_at = NOW()
FROM (
    SELECT unnest($1::text[]) AS key, unnest($2::numeric[]) AS value
) AS updates
WHERE dispatch_config.key = updates.key;

-- =============================================================================
-- Static Data Queries (for engine startup)
-- =============================================================================

-- name: ListBases :many
SELECT DISTINCT 
    l.id,
    l.name,
    COUNT(*) FILTER (WHERE u.status = 'available') AS available_units,
    COUNT(*) AS total_units
FROM units u
JOIN locations l ON u.location_id = l.id
WHERE u.location_id IS NOT NULL
GROUP BY l.id, l.name
ORDER BY l.name;

-- name: GetBaseMinReserve :one
SELECT COALESCE(value, 1)::int AS minReserve
FROM dispatch_config
WHERE key = 'min_reserve_per_base';

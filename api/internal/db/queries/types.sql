-- name: ListEventTypes :many
SELECT
    code,
    name,
    description,
    default_severity,
    recommended_unit_types,
    created_at,
    updated_at
FROM event_types
ORDER BY default_severity DESC, name;

-- name: ListUnitTypes :many
SELECT
    code,
    name,
    capabilities,
    speed_kmh,
    max_crew,
    illustration,
    created_at,
    updated_at
FROM unit_types
ORDER BY name;

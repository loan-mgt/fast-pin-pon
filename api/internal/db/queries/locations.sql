-- name: ListLocations :many
SELECT
    id,
    name,
    type,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    created_at,
    updated_at
FROM locations
ORDER BY name;

-- name: ListStations :many
SELECT
    id,
    name,
    type,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    created_at,
    updated_at
FROM locations
WHERE type = 'station'
ORDER BY name;

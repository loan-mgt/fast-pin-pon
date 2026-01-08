-- =============================================================================
-- Routing Queries - pgRouting + Unit Route Storage
-- =============================================================================

-- name: SaveUnitRoute :one
-- Saves or updates a route for a unit (upsert)
INSERT INTO unit_routes (unit_id, intervention_id, route_geometry, route_length_meters, estimated_duration_seconds, progress_percent)
VALUES (
    sqlc.arg(unit_id), 
    sqlc.arg(intervention_id), 
    ST_GeomFromGeoJSON(sqlc.arg(route_geojson)), 
    sqlc.arg(route_length_meters), 
    sqlc.arg(estimated_duration_seconds),
    0
)
ON CONFLICT (unit_id) DO UPDATE SET
    intervention_id = EXCLUDED.intervention_id,
    route_geometry = EXCLUDED.route_geometry,
    route_length_meters = EXCLUDED.route_length_meters,
    estimated_duration_seconds = EXCLUDED.estimated_duration_seconds,
    progress_percent = 0,
    updated_at = NOW()
RETURNING 
    unit_id,
    intervention_id,
    ST_AsGeoJSON(route_geometry)::text AS route_geojson,
    route_length_meters,
    estimated_duration_seconds,
    progress_percent,
    created_at,
    updated_at;

-- name: GetUnitRoute :one
-- Gets a unit's route with current position interpolated from progress
SELECT 
    unit_id,
    intervention_id,
    ST_AsGeoJSON(route_geometry)::text AS route_geojson,
    route_length_meters,
    estimated_duration_seconds,
    progress_percent,
    -- Current position interpolated along the route
    ST_X(ST_LineInterpolatePoint(route_geometry, LEAST(progress_percent / 100.0, 1.0)))::double precision AS current_lon,
    ST_Y(ST_LineInterpolatePoint(route_geometry, LEAST(progress_percent / 100.0, 1.0)))::double precision AS current_lat,
    -- Remaining distance and time
    (route_length_meters * (1.0 - progress_percent / 100.0))::double precision AS remaining_meters,
    (estimated_duration_seconds * (1.0 - progress_percent / 100.0))::double precision AS remaining_seconds,
    created_at,
    updated_at
FROM unit_routes
WHERE unit_id = sqlc.arg(unit_id);

-- name: UpdateRouteProgress :one
-- Updates the progress percentage and returns the new interpolated position
UPDATE unit_routes
SET 
    progress_percent = sqlc.arg(progress_percent),
    updated_at = NOW()
WHERE unit_id = sqlc.arg(unit_id)
RETURNING 
    unit_id,
    progress_percent,
    ST_X(ST_LineInterpolatePoint(route_geometry, LEAST(progress_percent / 100.0, 1.0)))::double precision AS current_lon,
    ST_Y(ST_LineInterpolatePoint(route_geometry, LEAST(progress_percent / 100.0, 1.0)))::double precision AS current_lat,
    (route_length_meters * (1.0 - progress_percent / 100.0))::double precision AS remaining_meters,
    (estimated_duration_seconds * (1.0 - progress_percent / 100.0))::double precision AS remaining_seconds;

-- name: DeleteUnitRoute :exec
-- Deletes a unit's route (called when unit status changes)
DELETE FROM unit_routes WHERE unit_id = sqlc.arg(unit_id);

-- name: GetRoutePosition :one
-- Gets just the interpolated position for a given progress (for simulation)
SELECT
    ST_X(ST_LineInterpolatePoint(route_geometry, LEAST(sqlc.arg(progress_percent)::double precision / 100.0, 1.0)))::double precision AS lon,
    ST_Y(ST_LineInterpolatePoint(route_geometry, LEAST(sqlc.arg(progress_percent)::double precision / 100.0, 1.0)))::double precision AS lat
FROM unit_routes
WHERE unit_id = sqlc.arg(unit_id);

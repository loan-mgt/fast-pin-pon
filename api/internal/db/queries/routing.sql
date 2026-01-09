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
    ur.unit_id,
    ur.intervention_id,
    ST_AsGeoJSON(ur.route_geometry)::text AS route_geojson,
    ur.route_length_meters,
    ur.estimated_duration_seconds,
    ur.progress_percent,
    -- Current position interpolated along the route
    ST_X(ST_LineInterpolatePoint(ur.route_geometry, ur.progress_percent / 100.0))::float8 AS current_lon,
    ST_Y(ST_LineInterpolatePoint(ur.route_geometry, ur.progress_percent / 100.0))::float8 AS current_lat,
    -- Remaining distance and time
    (ur.route_length_meters * (1.0 - ur.progress_percent / 100.0))::float8 AS remaining_meters,
    (ur.estimated_duration_seconds * (1.0 - ur.progress_percent / 100.0))::float8 AS remaining_seconds,
    ur.created_at,
    ur.updated_at,
    e.severity
FROM unit_routes ur
LEFT JOIN interventions i ON ur.intervention_id = i.id
LEFT JOIN events e ON i.event_id = e.id
WHERE ur.unit_id = sqlc.arg(unit_id);

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
    ST_X(ST_LineInterpolatePoint(route_geometry, progress_percent / 100.0))::float8 AS current_lon,
    ST_Y(ST_LineInterpolatePoint(route_geometry, progress_percent / 100.0))::float8 AS current_lat,
    (route_length_meters * (1.0 - progress_percent / 100.0))::float8 AS remaining_meters,
    (estimated_duration_seconds * (1.0 - progress_percent / 100.0))::float8 AS remaining_seconds;

-- name: DeleteUnitRoute :exec
-- Deletes a unit's route (called when unit status changes)
DELETE FROM unit_routes WHERE unit_id = sqlc.arg(unit_id);

-- name: GetRoutePosition :one
-- Gets just the interpolated position for a given progress (for simulation)
-- Optimized: Removed redundant cast and LEAST call
SELECT
    ST_X(ST_LineInterpolatePoint(route_geometry, sqlc.arg(progress_percent) / 100.0))::float8 AS lon,
    ST_Y(ST_LineInterpolatePoint(route_geometry, sqlc.arg(progress_percent) / 100.0))::float8 AS lat
FROM unit_routes
WHERE unit_id = sqlc.arg(unit_id);

-- name: GetRouteCalculationData :one
-- Gets all data needed to calculate a route for an assignment (unit position + event destination)
SELECT
    u.id AS unit_id,
    COALESCE(ST_X(u.location::geometry), 0)::float8 AS unit_lon,
    COALESCE(ST_Y(u.location::geometry), 0)::float8 AS unit_lat,
    e.id AS event_id,
    ST_X(e.location::geometry)::float8 AS event_lon,
    ST_Y(e.location::geometry)::float8 AS event_lat
FROM interventions i
JOIN events e ON e.id = i.event_id
JOIN units u ON u.id = sqlc.arg(unit_id)
WHERE i.id = sqlc.arg(intervention_id);

-- NOTE: CalculateRoute is implemented as raw SQL in handlers_routing.go
-- because sqlc cannot parse pgr_connectedComponents and other pgRouting functions

-- name: GetUnitStationRouteData :one
-- Gets all data needed to calculate a route to the unit's home station
SELECT
    u.id AS unit_id,
    COALESCE(ST_X(u.location::geometry), 0)::float8 AS unit_lon,
    COALESCE(ST_Y(u.location::geometry), 0)::float8 AS unit_lat,
    l.id AS station_id,
    COALESCE(ST_X(l.location::geometry), 0)::float8 AS station_lon,
    COALESCE(ST_Y(l.location::geometry), 0)::float8 AS station_lat
FROM units u
JOIN locations l ON l.id = u.location_id
WHERE u.id = sqlc.arg(unit_id);

-- name: GetActiveRouteRepairData :one
-- Finds the latest active assignment for a unit to repair a missing route
-- Returns both the intervention id and coordinates needed for routing
SELECT
        ia.intervention_id,
        u.id AS unit_id,
        COALESCE(ST_X(u.location::geometry), 0)::float8 AS unit_lon,
        COALESCE(ST_Y(u.location::geometry), 0)::float8 AS unit_lat,
        e.id AS event_id,
        ST_X(e.location::geometry)::float8 AS event_lon,
        ST_Y(e.location::geometry)::float8 AS event_lat
FROM intervention_assignments ia
JOIN interventions i ON i.id = ia.intervention_id
JOIN events e ON e.id = i.event_id
JOIN units u ON u.id = ia.unit_id
WHERE ia.unit_id = sqlc.arg(unit_id)
    AND ia.released_at IS NULL
    AND ia.status IN ('dispatched', 'arrived')
ORDER BY ia.dispatched_at DESC
LIMIT 1;

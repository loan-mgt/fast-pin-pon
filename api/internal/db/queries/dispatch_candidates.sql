-- =============================================================================
-- Dispatch Candidate Queries
-- Optimized queries for finding and scoring candidate units for dispatch
-- =============================================================================

-- name: GetInterventionForDispatch :one
-- Fetches intervention details needed for dispatch, including event location
SELECT 
    i.id AS intervention_id,
    i.event_id,
    i.status AS intervention_status,
    i.priority,
    i.decision_mode,
    e.title AS event_title,
    e.severity AS event_severity,
    e.event_type_code,
    et.recommended_unit_types,
    (ST_X(e.location::geometry))::double precision AS longitude,
    (ST_Y(e.location::geometry))::double precision AS latitude
FROM interventions i
JOIN events e ON i.event_id = e.id
JOIN event_types et ON e.event_type_code = et.code
WHERE i.id = $1;

-- name: ListPendingInterventions :many
-- Lists interventions awaiting dispatch, ordered by severity and age
SELECT 
    i.id AS intervention_id,
    i.event_id,
    i.status AS intervention_status,
    i.priority,
    i.created_at,
    e.severity AS event_severity,
    e.event_type_code,
    et.recommended_unit_types,
    (ST_X(e.location::geometry))::double precision AS longitude,
    (ST_Y(e.location::geometry))::double precision AS latitude,
    (SELECT COUNT(*) FROM intervention_assignments ia WHERE ia.intervention_id = i.id AND ia.status IN ('dispatched', 'arrived'))::bigint AS assigned_units_count
FROM interventions i
JOIN events e ON i.event_id = e.id
JOIN event_types et ON e.event_type_code = et.code
WHERE i.status = 'created'
ORDER BY e.severity DESC, i.created_at ASC;

-- name: ListDispatchCandidates :many
-- Finds candidate units for dispatch using distance-based estimation
-- Returns units sorted by estimated travel time, includes current assignment info for preemption
-- Note: For precise routing, use the dedicated routing endpoint
SELECT 
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.home_base,
    u.status,
    (ST_X(u.location::geometry))::double precision AS longitude,
    (ST_Y(u.location::geometry))::double precision AS latitude,
    a.id AS current_assignment_id,
    a.intervention_id AS current_intervention_id,
    ce.severity AS current_intervention_severity,
    ci.priority AS current_intervention_priority,
    -- Estimate travel time from distance (assume 50 km/h = 13.89 m/s average speed)
    (ST_Distance(u.location, e.location) / 13.89)::double precision AS travel_time_seconds,
    ST_Distance(u.location, e.location)::double precision AS distance_meters,
    (SELECT COUNT(*) FROM units u2 
     WHERE u2.home_base = u.home_base 
       AND u2.status = 'available' 
       AND u2.id != u.id)::int AS other_units_at_base
FROM units u
CROSS JOIN (
    SELECT ev.location
    FROM interventions iv
    JOIN events ev ON iv.event_id = ev.id
    WHERE iv.id = sqlc.arg(intervention_id)
) e
LEFT JOIN intervention_assignments a 
    ON a.unit_id = u.id AND a.status = 'dispatched'
LEFT JOIN interventions ci 
    ON a.intervention_id = ci.id
LEFT JOIN events ce
    ON ci.event_id = ce.id
WHERE u.status IN ('available', 'under_way')
  AND u.location IS NOT NULL
  AND (sqlc.narg(unit_types)::text[] IS NULL OR u.unit_type_code = ANY(sqlc.narg(unit_types)::text[]))
ORDER BY ST_Distance(u.location, e.location) ASC
LIMIT sqlc.arg(max_candidates)::int;

-- name: GetUnitsAtBase :one
-- Count available units at a specific base (for coverage calculations)
SELECT 
    COUNT(*) FILTER (WHERE status = 'available')::bigint AS available_count,
    COUNT(*)::bigint AS total_count
FROM units
WHERE home_base = $1;

-- name: ReleaseAssignment :exec
-- Release a unit from its current assignment (for preemption)
UPDATE intervention_assignments
SET 
    status = 'released',
    released_at = NOW()
WHERE id = $1;

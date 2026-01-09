-- name: CreateIntervention :one
INSERT INTO interventions (
    event_id,
    status,
    priority,
    decision_mode,
    created_by,
    notes
) VALUES (
    sqlc.arg(event_id),
    COALESCE(sqlc.arg(status)::intervention_status, 'created'),
    COALESCE(sqlc.arg(priority)::int, 3),
    COALESCE(sqlc.arg(decision_mode)::decision_mode, 'manual'),
    sqlc.arg(created_by),
    sqlc.arg(notes)
) RETURNING
    id,
    event_id,
    status,
    priority,
    decision_mode,
    created_by,
    notes,
    created_at,
    started_at,
    completed_at;

-- name: ListInterventionsByEvent :many
SELECT
    id,
    event_id,
    status,
    priority,
    decision_mode,
    created_by,
    notes,
    created_at,
    started_at,
    completed_at
FROM interventions
WHERE event_id = $1
ORDER BY created_at DESC;

-- name: GetIntervention :one
SELECT
    id,
    event_id,
    status,
    priority,
    decision_mode,
    created_by,
    notes,
    created_at,
    started_at,
    completed_at
FROM interventions
WHERE id = $1;

-- name: UpdateInterventionStatus :one
UPDATE interventions
SET
    status = $2::intervention_status,
    started_at = CASE WHEN $2::intervention_status = 'on_site' AND started_at IS NULL THEN NOW() ELSE started_at END,
    completed_at = CASE WHEN $2::intervention_status = 'completed' THEN NOW() ELSE completed_at END
WHERE id = $1
RETURNING
    id,
    event_id,
    status,
    priority,
    decision_mode,
    created_by,
    notes,
    created_at,
    started_at,
    completed_at;

-- name: CreateAssignment :one
INSERT INTO intervention_assignments (
    intervention_id,
    unit_id,
    role,
    status
) VALUES (
    sqlc.arg(intervention_id),
    sqlc.arg(unit_id),
    sqlc.arg(role),
    COALESCE(sqlc.arg(status)::assignment_status, 'dispatched')
) RETURNING
    id,
    intervention_id,
    unit_id,
    role,
    status,
    dispatched_at,
    arrived_at,
    released_at;

-- name: UpdateAssignmentStatus :one
UPDATE intervention_assignments
SET
    status = $2::assignment_status,
    arrived_at = CASE WHEN $2::assignment_status = 'arrived' THEN NOW() ELSE arrived_at END,
    released_at = CASE WHEN $2::assignment_status = 'released' THEN NOW() ELSE released_at END
WHERE id = $1
RETURNING
    id,
    intervention_id,
    unit_id,
    role,
    status,
    dispatched_at,
    arrived_at,
    released_at;

-- name: ListAssignmentsByIntervention :many
SELECT
    ia.id,
    ia.intervention_id,
    ia.unit_id,
    ia.role,
    ia.status,
    ia.dispatched_at,
    ia.arrived_at,
    ia.released_at,
    u.call_sign,
    u.unit_type_code,
    u.status AS unit_status,
    u.home_base,
    u.microbit_id,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM intervention_assignments ia
JOIN units u ON u.id = ia.unit_id
WHERE ia.intervention_id = $1
ORDER BY ia.dispatched_at DESC;

-- name: ListUnitsAssignedToEvent :many
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.home_base,
    u.status,
    u.microbit_id,
    u.location_id,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM intervention_assignments ia
JOIN interventions i ON ia.intervention_id = i.id
JOIN units u ON ia.unit_id = u.id
WHERE i.event_id = $1 AND ia.released_at IS NULL
ORDER BY ia.dispatched_at DESC;

-- name: ReleaseUnitFromIntervention :one
UPDATE intervention_assignments
SET
    status = 'released',
    released_at = NOW()
WHERE intervention_id = $1 AND unit_id = $2 AND released_at IS NULL
RETURNING id;

-- name: GetAssignmentContext :one
SELECT
    ia.id,
    ia.intervention_id,
    ia.unit_id,
    ia.dispatched_at,
    ia.arrived_at,
    ia.released_at,
    u.call_sign,
    u.unit_type_code,
    i.event_id,
    e.event_type_code,
    e.severity
FROM intervention_assignments ia
JOIN interventions i ON i.id = ia.intervention_id
JOIN events e ON e.id = i.event_id
JOIN units u ON u.id = ia.unit_id
WHERE ia.id = $1;

-- name: GetInterventionEventContext :one
SELECT
    i.id,
    e.event_type_code,
    e.severity,
    e.reported_at,
    i.completed_at
FROM interventions i
JOIN events e ON e.id = i.event_id
WHERE i.id = $1;


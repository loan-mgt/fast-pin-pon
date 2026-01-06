-- name: CreateEvent :one
INSERT INTO events (
    title,
    description,
    report_source,
    address,
    location,
    severity,
    event_type_code
) VALUES (
    sqlc.arg(title),
    sqlc.arg(description),
    sqlc.arg(report_source),
    sqlc.arg(address),
    ST_SetSRID(
        ST_MakePoint(
            sqlc.arg(longitude)::double precision,
            sqlc.arg(latitude)::double precision
        ),
        4326
    )::geography,
    sqlc.arg(severity),
    sqlc.arg(event_type_code)
) RETURNING
    id,
    title,
    description,
    report_source,
    address,
    ST_X(location::geometry)::double precision AS longitude,
    ST_Y(location::geometry)::double precision AS latitude,
    severity,
    event_type_code,
    reported_at,
    updated_at,
    closed_at;

-- name: ListEvents :many
SELECT
    e.id,
    e.title,
    e.description,
    e.report_source,
    e.address,
    ST_X(e.location::geometry)::double precision AS longitude,
    ST_Y(e.location::geometry)::double precision AS latitude,
    e.severity,
    e.event_type_code,
    et.name AS event_type_name,
    et.default_severity,
    e.reported_at,
    e.updated_at,
    e.closed_at,
    i.id AS intervention_id,
    i.status AS intervention_status
FROM events e
JOIN event_types et ON et.code = e.event_type_code
LEFT JOIN interventions i ON i.event_id = e.id
ORDER BY e.reported_at DESC
LIMIT $1 OFFSET $2;

-- name: GetEvent :one
SELECT
    e.id,
    e.title,
    e.description,
    e.report_source,
    e.address,
    ST_X(e.location::geometry)::double precision AS longitude,
    ST_Y(e.location::geometry)::double precision AS latitude,
    e.severity,
    e.event_type_code,
    et.name AS event_type_name,
    et.default_severity,
    et.recommended_unit_types,
    e.reported_at,
    e.updated_at,
    e.closed_at,
    i.id AS intervention_id,
    i.status AS intervention_status
FROM events e
JOIN event_types et ON et.code = e.event_type_code
LEFT JOIN interventions i ON i.event_id = e.id
WHERE e.id = $1;


-- name: CreateEventLog :one
INSERT INTO event_logs (
    event_id,
    code,
    actor,
    payload
) VALUES (
    $1, $2, $3, $4
) RETURNING
    id,
    event_id,
    created_at,
    code,
    actor,
    payload;

-- name: ListEventLogs :many
SELECT
    id,
    event_id,
    created_at,
    code,
    actor,
    payload
FROM event_logs
WHERE event_id = $1
ORDER BY created_at DESC
LIMIT $2 OFFSET $3;

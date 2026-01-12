-- name: ListUnits :many
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.status,
    u.microbit_id,
    u.location_id,
    l.name AS home_base_name,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM units u
LEFT JOIN locations l ON u.location_id = l.id
ORDER BY u.call_sign;

-- name: ListUnitsByLocation :many
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.status,
    u.microbit_id,
    u.location_id,
    l.name AS home_base_name,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM units u
LEFT JOIN locations l ON u.location_id = l.id
WHERE u.location_id = $1
ORDER BY u.call_sign;

-- name: ListVisibleUnits :many
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.status,
    u.microbit_id,
    u.location_id,
    l.name AS home_base_name,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM units u
LEFT JOIN locations l ON u.location_id = l.id
WHERE u.status != 'available_hidden'
ORDER BY u.call_sign;

-- name: ListAvailableUnitsNearby :many
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.status,
    u.microbit_id,
    u.location_id,
    l.name AS home_base_name,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at,
    ST_Distance(u.location, ST_SetSRID(ST_MakePoint(sqlc.arg(longitude)::double precision, sqlc.arg(latitude)::double precision), 4326)::geography)::double precision AS distance
FROM units u
LEFT JOIN locations l ON u.location_id = l.id
WHERE (u.status = 'available' OR u.status = 'available_hidden')
AND (sqlc.narg(unit_types)::text[] IS NULL OR u.unit_type_code = ANY(sqlc.narg(unit_types)::text[]))
ORDER BY distance ASC;

-- name: CreateUnit :one
INSERT INTO units (
    call_sign,
    unit_type_code,
    status,
    location,
    location_id,
    last_contact_at,
    microbit_id
) VALUES (
    sqlc.arg(call_sign),
    sqlc.arg(unit_type_code),
    sqlc.arg(status),
    ST_SetSRID(
        ST_MakePoint(
            sqlc.arg(longitude)::double precision,
            sqlc.arg(latitude)::double precision
        ),
        4326
    )::geography,
    sqlc.narg(location_id),
    sqlc.arg(last_contact_at),
    -- Auto-generate next available microbit_id (MB001, MB002, ...)
    (SELECT 'MB' || LPAD(
        (COALESCE(MAX(CAST(SUBSTRING(microbit_id FROM 3) AS INTEGER)), 0) + 1)::TEXT,
        3,
        '0'
    ) FROM units WHERE microbit_id ~ '^MB[0-9]{3}$')
) RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: GetUnit :one
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.status,
    u.microbit_id,
    u.location_id,
    l.name AS home_base_name,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM units u
LEFT JOIN locations l ON u.location_id = l.id
WHERE u.id = $1;

-- name: DeleteUnitAssignments :exec
DELETE FROM intervention_assignments WHERE unit_id = $1;

-- name: DeleteUnitTelemetry :exec
DELETE FROM unit_telemetry WHERE unit_id = $1;

-- name: DeleteUnit :exec
DELETE FROM units WHERE id = $1;

-- name: UpdateUnitStatus :one
UPDATE units
SET
    status = $2,
    updated_at = NOW()
WHERE units.id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: UpdateUnitLocation :one
UPDATE units
SET
    location = ST_SetSRID(
        ST_MakePoint(
            sqlc.arg(longitude)::double precision,
            sqlc.arg(latitude)::double precision
        ),
        4326
    )::geography,
    last_contact_at = COALESCE(sqlc.arg(contact_time), NOW()),
    updated_at = NOW()
WHERE units.id = sqlc.arg(id)
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: AssignMicrobit :one
UPDATE units
SET
    microbit_id = $2,
    updated_at = NOW()
WHERE units.id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: UnassignMicrobit :one
UPDATE units
SET
    microbit_id = NULL,
    updated_at = NOW()
WHERE units.id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: GetUnitByMicrobitID :one
SELECT
    u.id,
    u.call_sign,
    u.unit_type_code,
    u.status,
    u.microbit_id,
    u.location_id,
    l.name AS home_base_name,
    (COALESCE(ST_X(u.location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(u.location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    u.last_contact_at,
    u.created_at,
    u.updated_at
FROM units u
LEFT JOIN locations l ON u.location_id = l.id
WHERE u.microbit_id = $1;

-- name: UpdateUnitStatusByMicrobitID :one
UPDATE units
SET
    status = $2,
    last_contact_at = NOW(),
    updated_at = NOW()
WHERE microbit_id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: UpdateUnitLocationByMicrobitID :one
UPDATE units
SET
    location = ST_SetSRID(
        ST_MakePoint(
            sqlc.arg(longitude)::double precision,
            sqlc.arg(latitude)::double precision
        ),
        4326
    )::geography,
    last_contact_at = NOW(),
    updated_at = NOW()
WHERE microbit_id = sqlc.arg(microbit_id)
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: InsertUnitTelemetry :one
INSERT INTO unit_telemetry (
    unit_id,
    location,
    heading,
    speed_kmh,
    status_snapshot
) VALUES (
    sqlc.arg(unit_id),
    ST_SetSRID(
        ST_MakePoint(
            sqlc.arg(longitude)::double precision,
            sqlc.arg(latitude)::double precision
        ),
        4326
    )::geography,
    sqlc.arg(heading),
    sqlc.arg(speed_kmh),
    sqlc.arg(status_snapshot)
) RETURNING
    id,
    unit_id,
    recorded_at,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    heading,
    speed_kmh,
    status_snapshot;

-- name: UpdateUnitStation :one
UPDATE units
SET
    location_id = sqlc.narg(location_id),
    updated_at = NOW()
WHERE units.id = sqlc.arg(id)
RETURNING
    id,
    call_sign,
    unit_type_code,
    status,
    microbit_id,
    location_id,
    (SELECT name FROM locations WHERE locations.id = units.location_id) AS home_base_name,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

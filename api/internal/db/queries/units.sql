-- name: ListUnits :many
SELECT
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at
FROM units
ORDER BY call_sign;

-- name: ListAvailableUnitsNearby :many
SELECT
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at,
    ST_Distance(location, ST_SetSRID(ST_MakePoint(sqlc.arg(longitude)::double precision, sqlc.arg(latitude)::double precision), 4326)::geography)::double precision AS distance
FROM units
WHERE status = 'available'
AND (sqlc.narg(unit_types)::text[] IS NULL OR unit_type_code = ANY(sqlc.narg(unit_types)::text[]))
ORDER BY distance ASC;

-- name: CreateUnit :one
INSERT INTO units (
    call_sign,
    unit_type_code,
    home_base,
    status,
    location,
    last_contact_at
) VALUES (
    sqlc.arg(call_sign),
    sqlc.arg(unit_type_code),
    sqlc.arg(home_base),
    sqlc.arg(status),
    ST_SetSRID(
        ST_MakePoint(
            sqlc.arg(longitude)::double precision,
            sqlc.arg(latitude)::double precision
        ),
        4326
    )::geography,
    sqlc.arg(last_contact_at)
) RETURNING
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: GetUnit :one
SELECT
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at
FROM units
WHERE id = $1;

-- name: UpdateUnitStatus :one
UPDATE units
SET
    status = $2,
    updated_at = NOW()
WHERE id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
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
WHERE id = sqlc.arg(id)
RETURNING
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
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
WHERE id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
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
WHERE id = $1
RETURNING
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at;

-- name: GetUnitByMicrobitID :one
SELECT
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    microbit_id,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at
FROM units
WHERE microbit_id = $1;

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
    home_base,
    status,
    microbit_id,
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
    home_base,
    status,
    microbit_id,
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

-- name: ListUnits :many
SELECT
    id,
    call_sign,
    unit_type_code,
    home_base,
    status,
    (COALESCE(ST_X(location::geometry)::double precision, 0::double precision))::double precision AS longitude,
    (COALESCE(ST_Y(location::geometry)::double precision, 0::double precision))::double precision AS latitude,
    last_contact_at,
    created_at,
    updated_at
FROM units
ORDER BY call_sign;

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

-- name: ListRecentActivityLogs :many
-- Fetch recent activity logs with enriched data (join with units and events for call_sign/title)
SELECT 
    al.id,
    al.activity_type,
    al.entity_type,
    al.entity_id,
    al.actor,
    al.old_value,
    al.new_value,
    al.created_at,
    al.metadata,
    u.call_sign AS unit_call_sign,
    e.title AS event_title,
    i.event_id
FROM activity_logs al
LEFT JOIN units u ON al.entity_type = 'unit' AND al.entity_id = u.id
LEFT JOIN interventions i ON al.entity_type = 'intervention' AND al.entity_id = i.id
LEFT JOIN events e ON i.event_id = e.id
WHERE (sqlc.narg('activity_type')::text IS NULL OR al.activity_type = sqlc.narg('activity_type'))
ORDER BY al.created_at DESC
LIMIT sqlc.arg('limit');

-- name: ListActivityLogsForEvent :many
SELECT 
    al.id,
    al.activity_type,
    al.entity_type,
    al.entity_id,
    al.actor,
    al.old_value,
    al.new_value,
    al.created_at,
    al.metadata
FROM activity_logs al
LEFT JOIN interventions i ON al.entity_type = 'intervention' AND al.entity_id = i.id
WHERE (al.entity_type = 'event' AND al.entity_id = sqlc.arg('event_id'))
   OR (al.entity_type = 'intervention' AND i.event_id = sqlc.arg('event_id'))
ORDER BY al.created_at DESC
LIMIT sqlc.arg('limit') OFFSET sqlc.arg('offset');

-- name: CreateActivityLog :one
-- Insert a new activity log entry
INSERT INTO activity_logs (
    activity_type,
    entity_type,
    entity_id,
    actor,
    old_value,
    new_value,
    metadata
) VALUES (
    sqlc.arg(activity_type),
    sqlc.narg(entity_type),
    sqlc.narg(entity_id),
    sqlc.narg(actor),
    sqlc.narg(old_value),
    sqlc.narg(new_value),
    COALESCE(sqlc.narg(metadata), '{}'::jsonb)
)
RETURNING id, activity_type, entity_type, entity_id, actor, old_value, new_value, created_at, metadata;

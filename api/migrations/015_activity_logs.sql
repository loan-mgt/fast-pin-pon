-- +migrate Up
-- =============================================================================
-- Activity Logs: Generic table for tracking any entity changes
-- =============================================================================

CREATE TABLE activity_logs (
    id BIGSERIAL PRIMARY KEY,
    activity_type TEXT NOT NULL,  -- e.g., 'status_change', 'assignment', 'creation', 'deletion'
    entity_type TEXT,              -- e.g., 'unit', 'intervention', 'event', NULL for system-wide
    entity_id UUID,                -- NULL for non-entity-specific activities
    actor TEXT,                    -- Who/what triggered this (user, system, microbit_id, etc.)
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::JSONB
);

-- Indexes for efficient querying
CREATE INDEX idx_activity_logs_type ON activity_logs(activity_type);
CREATE INDEX idx_activity_logs_entity ON activity_logs(entity_type, entity_id) WHERE entity_id IS NOT NULL;
CREATE INDEX idx_activity_logs_created_at ON activity_logs(created_at DESC);

-- Remove old event_logs table as it's replaced by the more generic activity_logs
DROP TABLE IF EXISTS event_logs;

-- +migrate Down
DROP TABLE IF EXISTS activity_logs;

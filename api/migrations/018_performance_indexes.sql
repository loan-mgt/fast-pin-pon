-- +migrate Up
-- Migration 018: Performance indexes
-- These indexes target the most frequent and expensive queries identified
-- during load testing analysis.

-- =============================================================================
-- unit_telemetry: High-volume inserts + lookups by unit_id ordered by time
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_telemetry_unit_recorded
    ON unit_telemetry (unit_id, recorded_at DESC);

-- =============================================================================
-- events: Pagination query orders by reported_at DESC (ListEvents)
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_events_reported_at
    ON events (reported_at DESC);

-- =============================================================================
-- events: Join on event_type_code in ListEvents, GetEvent, dispatch queries
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_events_type_code
    ON events (event_type_code);

-- =============================================================================
-- interventions: Foreign key lookup by event_id (very common join path)
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_interventions_event_id
    ON interventions (event_id);

-- =============================================================================
-- intervention_assignments: Lookup active assignments per unit (dispatch scoring)
-- Covers the subquery: WHERE unit_id = ? AND status IN ('dispatched','arrived')
-- =============================================================================
-- (Already exists as idx_assignments_unit_active, but let's ensure covering index)
CREATE INDEX IF NOT EXISTS idx_assignments_unit_intervention
    ON intervention_assignments (unit_id, intervention_id)
    WHERE status IN ('dispatched', 'arrived');

-- =============================================================================
-- activity_logs: Recent logs query with entity joins, ordered by created_at DESC
-- Already has idx_activity_logs_created_at but add composite for filtered queries
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_activity_logs_type_created
    ON activity_logs (activity_type, created_at DESC)
    WHERE entity_id IS NOT NULL;

-- =============================================================================
-- Analyze updated tables to refresh planner statistics
-- =============================================================================
ANALYZE unit_telemetry;
ANALYZE events;
ANALYZE interventions;
ANALYZE intervention_assignments;
ANALYZE activity_logs;

-- +migrate Down
DROP INDEX IF EXISTS idx_activity_logs_type_created;
DROP INDEX IF EXISTS idx_assignments_unit_intervention;
DROP INDEX IF EXISTS idx_interventions_event_id;
DROP INDEX IF EXISTS idx_events_type_code;
DROP INDEX IF EXISTS idx_events_reported_at;
DROP INDEX IF EXISTS idx_telemetry_unit_recorded;

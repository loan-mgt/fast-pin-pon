-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- =============================================================================
-- Dispatch Configuration Table
-- Stores tunable weights and thresholds for the decision engine
-- =============================================================================

CREATE TABLE dispatch_config (
    key         TEXT PRIMARY KEY,
    value       NUMERIC NOT NULL,
    description TEXT NOT NULL,
    min_value   NUMERIC,
    max_value   NUMERIC,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert default configuration values
INSERT INTO dispatch_config (key, value, description, min_value, max_value) VALUES
    ('weight_travel_time',            1.0,    'Weight for travel time in seconds (primary factor)', 0, 10),
    ('weight_coverage_penalty',       0.3,    'Penalty weight when base drops below minimum reserve', 0, 10),
    ('weight_capability_match',      -50.0,   'Bonus (negative = better score) for exact unit type match', -200, 0),
    ('weight_en_route_progress',      0.2,    'Credit factor for units already en route (partial progress)', 0, 5),
    ('weight_preemption_delta',     -100.0,   'Bonus per severity level delta when preempting (negative = better)', -500, 0),
    ('weight_reassignment_cost',      60.0,   'Penalty in equivalent seconds for reassigning a unit', 0, 300),
    ('min_reserve_per_base',          1.0,    'Minimum number of units to keep available at each base', 0, 10),
    ('preemption_severity_threshold', 2.0,    'Minimum severity delta required to allow preemption', 1, 5),
    ('max_candidates_per_dispatch',  10.0,    'Maximum number of candidate units to consider per dispatch', 1, 50);

-- =============================================================================
-- Performance Indexes for Dispatch Queries
-- =============================================================================

-- Spatial index for fast unit location lookups (may already exist, use IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_units_location_gist 
    ON units USING GIST (location);

-- Fast filtering by status + type (most common dispatch query pattern)
-- Covers: available units, under_way units that could be reassigned
CREATE INDEX IF NOT EXISTS idx_units_status_type 
    ON units (status, unit_type_code) 
    WHERE status IN ('available', 'under_way');

-- Index for finding units by home_base (coverage calculations)
CREATE INDEX IF NOT EXISTS idx_units_home_base 
    ON units (home_base, status);

-- Interventions: fast lookup of active interventions by priority for dispatch prioritization
CREATE INDEX IF NOT EXISTS idx_interventions_active_priority 
    ON interventions (priority DESC, created_at ASC) 
    WHERE status = 'created';

-- Interventions: lookup by status for pending dispatch
CREATE INDEX IF NOT EXISTS idx_interventions_status 
    ON interventions (status) 
    WHERE status = 'created';

-- Assignments: find units currently assigned (for preemption check)
CREATE INDEX IF NOT EXISTS idx_assignments_unit_active 
    ON intervention_assignments (unit_id, status) 
    WHERE status IN ('dispatched', 'arrived');

-- Assignments: lookup by intervention for fast assignment listing
CREATE INDEX IF NOT EXISTS idx_assignments_intervention 
    ON intervention_assignments (intervention_id, status);

-- =============================================================================
-- Routing Graph Indexes (if routing_ways table exists)
-- =============================================================================

-- These indexes are conditional - only created if the routing_ways table exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'routing_ways') THEN
        -- Source/target indexes for pgr_dijkstra
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_routing_ways_source ON routing_ways (source)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_routing_ways_target ON routing_ways (target)';
        
        -- Composite index for routing cost lookups
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_routing_ways_cost ON routing_ways (gid) INCLUDE (cost_s, reverse_cost_s, length_m) WHERE cost_s IS NOT NULL';
    END IF;
END $$;

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

DROP INDEX IF EXISTS idx_assignments_intervention;
DROP INDEX IF EXISTS idx_assignments_unit_active;
DROP INDEX IF EXISTS idx_interventions_status;
DROP INDEX IF EXISTS idx_interventions_active_priority;
DROP INDEX IF EXISTS idx_units_home_base;
DROP INDEX IF EXISTS idx_units_status_type;

DROP TABLE IF EXISTS dispatch_config;

COMMIT;
-- +migrate StatementEnd

-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- =============================================================================
-- Remove capability match weight and update default values
-- =============================================================================

-- Remove the capability match weight (w₃ - Availability Bonus)
DELETE FROM dispatch_config WHERE key = 'weight_capability_match';

-- Update default values for remaining weights
UPDATE dispatch_config SET value = 0.70 WHERE key = 'weight_travel_time';
UPDATE dispatch_config SET value = 1.50 WHERE key = 'weight_coverage_penalty';
UPDATE dispatch_config SET value = 5.0, min_value = -500, max_value = 500, 
       description = 'Weight per severity level delta when preempting'
       WHERE key = 'weight_preemption_delta';
UPDATE dispatch_config SET value = 85.0 WHERE key = 'weight_reassignment_cost';

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

-- Restore original values
UPDATE dispatch_config SET value = 1.0 WHERE key = 'weight_travel_time';
UPDATE dispatch_config SET value = 0.3 WHERE key = 'weight_coverage_penalty';
UPDATE dispatch_config SET value = -100.0, min_value = -500, max_value = 0,
       description = 'Bonus per severity level delta when preempting (negative = better)'
       WHERE key = 'weight_preemption_delta';
UPDATE dispatch_config SET value = 60.0 WHERE key = 'weight_reassignment_cost';

-- Re-add capability match weight
INSERT INTO dispatch_config (key, value, description, min_value, max_value) VALUES
    ('weight_capability_match', -50.0, 'Bonus (negative = better score) for exact unit type match', -200, 0);

COMMIT;
-- +migrate StatementEnd

-- +migrate Up
ALTER TABLE events ADD COLUMN auto_simulated BOOLEAN NOT NULL DEFAULT true;

-- Create index for filtering pending interventions
CREATE INDEX idx_events_auto_simulated ON events(auto_simulated) WHERE auto_simulated = true;

-- +migrate Down
DROP INDEX IF EXISTS idx_events_auto_simulated;
ALTER TABLE events DROP COLUMN IF EXISTS auto_simulated;

-- +migrate Up
-- Add microbit_id field to units table for micro:bit assignment

ALTER TABLE units ADD COLUMN microbit_id TEXT UNIQUE;

-- Create index for faster lookup by microbit_id
CREATE INDEX units_microbit_id_idx ON units(microbit_id) WHERE microbit_id IS NOT NULL;

-- +migrate Down
DROP INDEX IF EXISTS units_microbit_id_idx;
ALTER TABLE units DROP COLUMN IF EXISTS microbit_id;

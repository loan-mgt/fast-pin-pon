-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-------------------------
-- ADD available_hidden TO unit_status ENUM
-------------------------
-- Add the new value directly to the existing enum (PostgreSQL 9.1+)
ALTER TYPE unit_status ADD VALUE IF NOT EXISTS 'available_hidden' AFTER 'available';

-------------------------
-- ADD location_id (caserne) to units
-------------------------
ALTER TABLE units ADD COLUMN IF NOT EXISTS location_id UUID REFERENCES locations(id) ON DELETE SET NULL;

-- Create index for faster lookups by location
CREATE INDEX IF NOT EXISTS units_location_id_idx ON units(location_id);

-- Update existing units to link to their home_base if a matching location exists
UPDATE units u
SET location_id = l.id
FROM locations l
WHERE l.type = 'station' AND l.name = u.home_base AND u.location_id IS NULL;

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

-------------------------
-- REMOVE location_id from units
-------------------------
DROP INDEX IF EXISTS units_location_id_idx;
ALTER TABLE units DROP COLUMN IF EXISTS location_id;

-- Note: PostgreSQL doesn't support removing enum values directly.
-- The available_hidden value will remain in the enum but won't be used.
-- To fully remove it, you would need to recreate the enum type.

COMMIT;
-- +migrate StatementEnd

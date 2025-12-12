-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-------------------------
-- UNIT STATUS ENUM FIX
-------------------------
-- 1. Create new enum with the desired final values
CREATE TYPE unit_status_new AS ENUM ('available', 'under_way', 'on_site', 'unavailable', 'offline');

-- 2. Alter table columns to use new type (via cast)
ALTER TABLE units
    ALTER COLUMN status TYPE unit_status_new
    USING 
        CASE status
            WHEN 'en_route' THEN 'under_way'
            WHEN 'maintenance' THEN 'unavailable'
            ELSE status::text
        END::unit_status_new;

-- 3. Drop old enum
DROP TYPE unit_status;

-- 4. Rename new enum
ALTER TYPE unit_status_new RENAME TO unit_status;


-------------------------
-- INTERVENTION STATUS ENUM FIX
-------------------------
-- 1. Create new enum with only desired final values
CREATE TYPE intervention_status_new AS ENUM ('created', 'on_site', 'completed', 'cancelled');

-- 2. Alter table
ALTER TABLE interventions
    ALTER COLUMN status TYPE intervention_status_new
    USING 
        CASE status
            WHEN 'planned' THEN 'created'
            -- removed values are mapped? If not, choose one:
            WHEN 'en_route' THEN 'created'
            WHEN 'on_site' THEN 'on_site'
            ELSE status::text
        END::intervention_status_new;

-- 3. Drop old enum
DROP TYPE intervention_status;

-- 4. Rename
ALTER TYPE intervention_status_new RENAME TO intervention_status;


-------------------------
-- UPDATE DEFAULTS
-------------------------
ALTER TABLE units ALTER COLUMN status SET DEFAULT 'available';
ALTER TABLE interventions ALTER COLUMN status SET DEFAULT 'created';

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

-------------------------
-- RECREATE ORIGINAL unit_status
-------------------------
CREATE TYPE unit_status_old AS ENUM ('available', 'en_route', 'on_site', 'maintenance', 'offline');

ALTER TABLE units
    ALTER COLUMN status TYPE unit_status_old
    USING
        CASE status
            WHEN 'under_way' THEN 'en_route'
            WHEN 'unavailable' THEN 'maintenance'
            ELSE status::text
        END::unit_status_old;

DROP TYPE unit_status;
ALTER TYPE unit_status_old RENAME TO unit_status;

-------------------------
-- RECREATE ORIGINAL intervention_status
-------------------------
CREATE TYPE intervention_status_old AS ENUM ('planned', 'en_route', 'on_site', 'completed', 'cancelled');

ALTER TABLE interventions
    ALTER COLUMN status TYPE intervention_status_old
    USING
        CASE status
            WHEN 'created' THEN 'planned'
            ELSE status::text
        END::intervention_status_old;

DROP TYPE intervention_status;
ALTER TYPE intervention_status_old RENAME TO intervention_status;


-- Restore defaults
ALTER TABLE units ALTER COLUMN status SET DEFAULT 'available';
ALTER TABLE interventions ALTER COLUMN status SET DEFAULT 'planned';

COMMIT;
-- +migrate StatementEnd

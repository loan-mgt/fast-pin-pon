-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-------------------------
-- DROP home_base TEXT column (no longer needed, using location_id FK)
-------------------------
-- First drop the index that references home_base
DROP INDEX IF EXISTS idx_units_home_base;

-- Drop the home_base column
ALTER TABLE units DROP COLUMN IF EXISTS home_base;

-- Ensure FK constraint exists for location_id (add if missing)
-- The FK was added in migration 010, but let's make it explicit
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'units_location_id_fkey' 
        AND table_name = 'units'
    ) THEN
        ALTER TABLE units 
        ADD CONSTRAINT units_location_id_fkey 
        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Index already exists (units_location_id_idx) from migration 010

COMMIT;
-- +migrate StatementEnd

-- Separate transaction for data operations
-- +migrate StatementBegin
BEGIN;

-------------------------
-- TRUNCATE locations and INSERT new fire stations
-------------------------
-- First delete unit assignments to avoid FK violation
DELETE FROM intervention_assignments;

-- Delete all units first (they reference locations)
DELETE FROM units;

-- Clear all locations
TRUNCATE TABLE locations CASCADE;

-- Seed new fire stations with OSM IDs
INSERT INTO locations (name, type, location)
VALUES
    ('Caserne Lyon–Rochat', 'station', ST_SetSRID(ST_MakePoint(4.848282697041415, 45.749955810500644), 4326)::geography),
    ('Centre d''incendie et de secours de Feyzin', 'station', ST_SetSRID(ST_MakePoint(4.861987261768507, 45.683319625270244), 4326)::geography),
    ('Plateforme d''intervention des pompiers de Saint-Fons', 'station', ST_SetSRID(ST_MakePoint(4.849163607230181, 45.706162630653225), 4326)::geography),
    ('Caserne Villeurbanne – La Doua', 'station', ST_SetSRID(ST_MakePoint(4.877999650261909, 45.77898545552611), 4326)::geography),
    ('Caserne des pompiers (Tassin-la-Demi-Lune)', 'station', ST_SetSRID(ST_MakePoint(4.787765814175886, 45.73270191666873), 4326)::geography),
    ('Pompiers d''Écully', 'station', ST_SetSRID(ST_MakePoint(4.7782095520202255, 45.772702576019405), 4326)::geography),
    ('Caserne des pompiers (Caluire-et-Cuire)', 'station', ST_SetSRID(ST_MakePoint(4.7978170742269475, 45.79058154078469), 4326)::geography),
    ('Centre d''incendie et de secours de Saint-Priest', 'station', ST_SetSRID(ST_MakePoint(4.913180662216852, 45.71631912138708), 4326)::geography),
    ('Centre d''incendie et de secours de Poleymieux-au-Mont-d''Or', 'station', ST_SetSRID(ST_MakePoint(4.801802980634165, 45.860275756421814), 4326)::geography),
    ('Centre d''incendie et de secours Villeurbanne–Cusset', 'station', ST_SetSRID(ST_MakePoint(4.905507066154999, 45.76585934903441), 4326)::geography),
    ('Centre d''intervention de Lyon – Croix-Rousse', 'station', ST_SetSRID(ST_MakePoint(4.8215827640182525, 45.783999898132535), 4326)::geography),
    ('Sapeurs-Pompiers Pierre-Bénite', 'station', ST_SetSRID(ST_MakePoint(4.822364968793383, 45.70016988438265), 4326)::geography),
    ('Centre d''incendie et de secours de Tassin', 'station', ST_SetSRID(ST_MakePoint(4.7804604670841, 45.756556664857186), 4326)::geography),
    ('Future caserne de pompiers', 'station', ST_SetSRID(ST_MakePoint(4.917919063935118, 45.786116959059626), 4326)::geography);

COMMIT;
-- +migrate StatementEnd

-- Separate transaction for unit seeding
-- +migrate StatementBegin
BEGIN;

-------------------------
-- INSERT new units (2-4 per station)
-------------------------
WITH stations AS (
    SELECT id, name FROM locations WHERE type = 'station'
), base_units AS (
    SELECT * FROM (VALUES
        -- Caserne Lyon–Rochat (ROC)
        ('ROC-01', 'FPT', 'Caserne Lyon–Rochat'),
        ('ROC-02', 'VSAV', 'Caserne Lyon–Rochat'),
        ('ROC-03', 'EPA', 'Caserne Lyon–Rochat'),
        -- Centre de Feyzin (FEY)
        ('FEY-01', 'FPT', 'Centre d''incendie et de secours de Feyzin'),
        ('FEY-02', 'FPTL', 'Centre d''incendie et de secours de Feyzin'),
        ('FEY-03', 'VSAV', 'Centre d''incendie et de secours de Feyzin'),
        ('FEY-04', 'VLHR', 'Centre d''incendie et de secours de Feyzin'),
        -- Saint-Fons (SFO)
        ('SFO-01', 'FPT', 'Plateforme d''intervention des pompiers de Saint-Fons'),
        ('SFO-02', 'VSAV', 'Plateforme d''intervention des pompiers de Saint-Fons'),
        ('SFO-03', 'VER', 'Plateforme d''intervention des pompiers de Saint-Fons'),
        -- Villeurbanne La Doua (DOU)
        ('DOU-01', 'FPT', 'Caserne Villeurbanne – La Doua'),
        ('DOU-02', 'FPTL', 'Caserne Villeurbanne – La Doua'),
        ('DOU-03', 'VSAV', 'Caserne Villeurbanne – La Doua'),
        ('DOU-04', 'EPA', 'Caserne Villeurbanne – La Doua'),
        -- Tassin-la-Demi-Lune (TDL)
        ('TDL-01', 'FPT', 'Caserne des pompiers (Tassin-la-Demi-Lune)'),
        ('TDL-02', 'VSAV', 'Caserne des pompiers (Tassin-la-Demi-Lune)'),
        -- Écully (ECU)
        ('ECU-01', 'FPT', 'Pompiers d''Écully'),
        ('ECU-02', 'VSAV', 'Pompiers d''Écully'),
        ('ECU-03', 'VIM', 'Pompiers d''Écully'),
        -- Caluire-et-Cuire (CAL)
        ('CAL-01', 'FPT', 'Caserne des pompiers (Caluire-et-Cuire)'),
        ('CAL-02', 'VSAV', 'Caserne des pompiers (Caluire-et-Cuire)'),
        ('CAL-03', 'EPA', 'Caserne des pompiers (Caluire-et-Cuire)'),
        -- Saint-Priest (SPR)
        ('SPR-01', 'FPT', 'Centre d''incendie et de secours de Saint-Priest'),
        ('SPR-02', 'FPTL', 'Centre d''incendie et de secours de Saint-Priest'),
        ('SPR-03', 'VSAV', 'Centre d''incendie et de secours de Saint-Priest'),
        ('SPR-04', 'VLHR', 'Centre d''incendie et de secours de Saint-Priest'),
        -- Poleymieux-au-Mont-d'Or (POL)
        ('POL-01', 'FPT', 'Centre d''incendie et de secours de Poleymieux-au-Mont-d''Or'),
        ('POL-02', 'VSAV', 'Centre d''incendie et de secours de Poleymieux-au-Mont-d''Or'),
        -- Villeurbanne–Cusset (CUS)
        ('CUS-01', 'FPT', 'Centre d''incendie et de secours Villeurbanne–Cusset'),
        ('CUS-02', 'VSAV', 'Centre d''incendie et de secours Villeurbanne–Cusset'),
        ('CUS-03', 'VER', 'Centre d''incendie et de secours Villeurbanne–Cusset'),
        -- Lyon Croix-Rousse (CRX)
        ('CRX-01', 'FPT', 'Centre d''intervention de Lyon – Croix-Rousse'),
        ('CRX-02', 'FPTL', 'Centre d''intervention de Lyon – Croix-Rousse'),
        ('CRX-03', 'VSAV', 'Centre d''intervention de Lyon – Croix-Rousse'),
        ('CRX-04', 'EPA', 'Centre d''intervention de Lyon – Croix-Rousse'),
        -- Pierre-Bénite (PIB)
        ('PIB-01', 'FPT', 'Sapeurs-Pompiers Pierre-Bénite'),
        ('PIB-02', 'VSAV', 'Sapeurs-Pompiers Pierre-Bénite'),
        ('PIB-03', 'VLHR', 'Sapeurs-Pompiers Pierre-Bénite'),
        -- Tassin (TAS)
        ('TAS-01', 'FPT', 'Centre d''incendie et de secours de Tassin'),
        ('TAS-02', 'VSAV', 'Centre d''incendie et de secours de Tassin'),
        ('TAS-03', 'VIM', 'Centre d''incendie et de secours de Tassin'),
        -- Future caserne (FUT)
        ('FUT-01', 'FPT', 'Future caserne de pompiers'),
        ('FUT-02', 'VSAV', 'Future caserne de pompiers')
    ) AS t(call_sign, unit_type_code, station_name)
)
INSERT INTO units (call_sign, unit_type_code, status, location_id, location)
SELECT 
    bu.call_sign, 
    bu.unit_type_code, 
    'available_hidden', 
    s.id,
    l.location
FROM base_units bu
JOIN stations s ON s.name = bu.station_name
JOIN locations l ON l.id = s.id;

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

-- Delete all new units
DELETE FROM units;

-- Re-add home_base column
ALTER TABLE units ADD COLUMN IF NOT EXISTS home_base TEXT;

-- Re-create the index
CREATE INDEX IF NOT EXISTS idx_units_home_base ON units (home_base, status);

COMMIT;
-- +migrate StatementEnd

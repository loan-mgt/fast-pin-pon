-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- FPT (Fourgon Pompe Tonne) - 2 units
INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'VIL-50', 'FPT', 'Villeurbanne', 'available_hidden', id FROM locations WHERE name = 'Villeurbanne'
ON CONFLICT (call_sign) DO NOTHING;

INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'CON-50', 'FPT', 'Lyon Confluence', 'available_hidden', id FROM locations WHERE name = 'Lyon Confluence'
ON CONFLICT (call_sign) DO NOTHING;

-- FPTL (Fourgon Pompe Tonne Léger) - 2 units
INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'PDI-50', 'FPTL', 'Lyon Part-Dieu', 'available_hidden', id FROM locations WHERE name = 'Lyon Part-Dieu'
ON CONFLICT (call_sign) DO NOTHING;

INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'CUS-50', 'FPTL', 'Cusset', 'available_hidden', id FROM locations WHERE name = 'Cusset'
ON CONFLICT (call_sign) DO NOTHING;

-- EPA (Échelle Pivotante Automatique) - 2 units
INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'VIL-51', 'EPA', 'Villeurbanne', 'available_hidden', id FROM locations WHERE name = 'Villeurbanne'
ON CONFLICT (call_sign) DO NOTHING;

INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'CON-51', 'EPA', 'Lyon Confluence', 'available_hidden', id FROM locations WHERE name = 'Lyon Confluence'
ON CONFLICT (call_sign) DO NOTHING;

-- VSAV (Véhicule de Secours et d'Assistance aux Victimes) - 2 units
INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'PDI-51', 'VSAV', 'Lyon Part-Dieu', 'available_hidden', id FROM locations WHERE name = 'Lyon Part-Dieu'
ON CONFLICT (call_sign) DO NOTHING;

INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'CUS-51', 'VSAV', 'Cusset', 'available_hidden', id FROM locations WHERE name = 'Cusset'
ON CONFLICT (call_sign) DO NOTHING;

-- VLHR (Véhicule Léger Hors Route) - 2 units
INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'VIL-52', 'VLHR', 'Villeurbanne', 'available_hidden', id FROM locations WHERE name = 'Villeurbanne'
ON CONFLICT (call_sign) DO NOTHING;

INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT 'CON-52', 'VLHR', 'Lyon Confluence', 'available_hidden', id FROM locations WHERE name = 'Lyon Confluence'
ON CONFLICT (call_sign) DO NOTHING;

-- Ensure location geometry is set correctly based on the base location
UPDATE units u
SET location = l.location
FROM locations l
WHERE u.location_id = l.id AND u.location IS NULL;

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

DELETE FROM units WHERE call_sign IN (
    'VIL-50', 'CON-50',
    'PDI-50', 'CUS-50',
    'VIL-51', 'CON-51',
    'PDI-51', 'CUS-51',
    'VIL-52', 'CON-52'
);

COMMIT;
-- +migrate StatementEnd

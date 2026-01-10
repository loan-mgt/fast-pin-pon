-- +migrate Up
-- +migrate StatementBegin
BEGIN;

WITH stations AS (
    SELECT name, id FROM locations WHERE name IN ('Villeurbanne', 'Lyon Confluence', 'Lyon Part-Dieu', 'Cusset')
), base_units AS (
    SELECT * FROM (VALUES
        -- Villeurbanne
        ('VIL-01', 'FPT', 'Villeurbanne'),
        ('VIL-02', 'FPTL', 'Villeurbanne'),
        ('VIL-03', 'VER', 'Villeurbanne'),
        ('VIL-04', 'VIM', 'Villeurbanne'),
        ('VIL-05', 'VSAV', 'Villeurbanne'),
        ('VIL-06', 'VSAV', 'Villeurbanne'),
        ('VIL-07', 'VLHR', 'Villeurbanne'),
        ('VIL-08', 'EPA', 'Villeurbanne'),
        -- Lyon Confluence
        ('CON-01', 'FPT', 'Lyon Confluence'),
        ('CON-02', 'FPTL', 'Lyon Confluence'),
        ('CON-03', 'VER', 'Lyon Confluence'),
        ('CON-04', 'VIM', 'Lyon Confluence'),
        ('CON-05', 'VSAV', 'Lyon Confluence'),
        ('CON-06', 'VLHR', 'Lyon Confluence'),
        ('CON-07', 'EPA', 'Lyon Confluence'),
        -- Lyon Part-Dieu
        ('PDI-01', 'FPT', 'Lyon Part-Dieu'),
        ('PDI-02', 'FPTL', 'Lyon Part-Dieu'),
        ('PDI-03', 'VIA', 'Lyon Part-Dieu'),
        ('PDI-04', 'VSAV', 'Lyon Part-Dieu'),
        ('PDI-05', 'VSAV', 'Lyon Part-Dieu'),
        ('PDI-06', 'VLHR', 'Lyon Part-Dieu'),
        ('PDI-07', 'EPA', 'Lyon Part-Dieu'),
        -- Cusset
        ('CUS-01', 'FPT', 'Cusset'),
        ('CUS-02', 'VER', 'Cusset'),
        ('CUS-03', 'VSAV', 'Cusset')
    ) AS t(call_sign, unit_type_code, home_base)
)
INSERT INTO units (call_sign, unit_type_code, home_base, status, location_id)
SELECT bu.call_sign, bu.unit_type_code, bu.home_base, 'available_hidden', l.id
FROM base_units bu
JOIN stations l ON l.name = bu.home_base
ON CONFLICT (call_sign) DO UPDATE
SET unit_type_code = EXCLUDED.unit_type_code,
    home_base = EXCLUDED.home_base,
    status = EXCLUDED.status,
    location_id = EXCLUDED.location_id;

-- Ensure location geometry mirrors the station for units missing a point
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
    'VIL-01', 'VIL-02', 'VIL-03', 'VIL-04', 'VIL-05', 'VIL-06', 'VIL-07', 'VIL-08',
    'CON-01', 'CON-02', 'CON-03', 'CON-04', 'CON-05', 'CON-06', 'CON-07',
    'PDI-01', 'PDI-02', 'PDI-03', 'PDI-04', 'PDI-05', 'PDI-06', 'PDI-07',
    'CUS-01', 'CUS-02', 'CUS-03'
);

COMMIT;
-- +migrate StatementEnd

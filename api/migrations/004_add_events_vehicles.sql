-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- =========================
-- Event types (incidents)
-- =========================
INSERT INTO event_types (code, name, description, default_severity, recommended_unit_types)
VALUES
  ('CRASH',          'Accident routier',    'Incident impliquant des vehicules', 5, ARRAY['VER']),
  ('AQUATIC_RESCUE', 'Incidents aquatique', 'Sauvetage maritime',                 5, ARRAY['VER', 'VIM']),
  ('OTHER',          'Autres incidents',    'Incident classique',                 5, ARRAY['VSAV'])
ON CONFLICT (code) DO UPDATE
SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  default_severity = EXCLUDED.default_severity,
  recommended_unit_types = EXCLUDED.recommended_unit_types,
  updated_at = NOW();

-- =========================
-- Unit types (vehicules)
-- =========================
INSERT INTO unit_types (code, name, capabilities, speed_kmh, max_crew, illustration)
VALUES
  ('VIM', 'Véhicule Intervention Maritime', 'Intervention aquatique', 30, 2, NULL),
  ('VIA', 'Véhicule Intervention Aérienne', 'Intervention dans des lieux inaccessibles par des vehicules terrains', 240, 2, NULL),
  ('VER', 'Véhicule Extration Routière',    'Accidents routiers', 85, 2, NULL)
ON CONFLICT (code) DO UPDATE
SET
  name = EXCLUDED.name,
  capabilities = EXCLUDED.capabilities,
  speed_kmh = EXCLUDED.speed_kmh,
  max_crew = EXCLUDED.max_crew,
  illustration = EXCLUDED.illustration,
  updated_at = NOW();

COMMIT;
-- +migrate StatementEnd


-- +migrate Down
-- +migrate StatementBegin
BEGIN;

DELETE FROM event_types et
WHERE et.code IN ('CRASH', 'AQUATIC_RESCUE', 'OTHER')
  AND NOT EXISTS (
    SELECT 1 FROM events e WHERE e.event_type_code = et.code
  );

DELETE FROM unit_types ut
WHERE ut.code IN ('VIM', 'VIA', 'VER')
  AND NOT EXISTS (
    SELECT 1 FROM units u WHERE u.unit_type_code = ut.code
  );

COMMIT;
-- +migrate StatementEnd

-- +migrate Up
-- +migrate StatementBegin
BEGIN;

CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS locations_location_idx ON locations USING GIST (location);

-- Seed initial fire stations (casernes)
INSERT INTO locations (name, type, location)
VALUES
    ('Villeurbanne', 'station', ST_SetSRID(ST_MakePoint(4.878770, 45.766180), 4326)::geography),
    ('Lyon Confluence', 'station', ST_SetSRID(ST_MakePoint(4.823733, 45.741054), 4326)::geography),
    ('Lyon Part-Dieu', 'station', ST_SetSRID(ST_MakePoint(4.861700, 45.760540), 4326)::geography),
    ('Cusset', 'station', ST_SetSRID(ST_MakePoint(4.895340, 45.766230), 4326)::geography);

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;
DROP TABLE IF EXISTS locations;
COMMIT;
-- +migrate StatementEnd
-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- Locations table to store static buildings (e.g., fire stations)
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    type TEXT NOT NULL, -- e.g., 'station'
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS locations_type_idx ON locations(type);
CREATE INDEX IF NOT EXISTS locations_location_idx ON locations USING GIST (location);

-- Seed main fire stations (casernes)
-- Coordinates sourced from existing simulation defaults
INSERT INTO locations (name, type, location)
VALUES
    ('Villeurbanne', 'station', ST_SetSRID(ST_MakePoint(4.878770, 45.766180), 4326)::geography),
    ('Lyon Confluence', 'station', ST_SetSRID(ST_MakePoint(4.823733, 45.741054), 4326)::geography),
    ('Lyon Part-Dieu', 'station', ST_SetSRID(ST_MakePoint(4.861700, 45.760540), 4326)::geography),
    ('Cusset', 'station', ST_SetSRID(ST_MakePoint(4.895340, 45.766230), 4326)::geography)
ON CONFLICT DO NOTHING;

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;
DROP TABLE IF EXISTS locations;
COMMIT;
-- +migrate StatementEnd
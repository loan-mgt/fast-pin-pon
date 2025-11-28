-- +migrate Up
-- +migrate StatementBegin
BEGIN;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TYPE event_status AS ENUM ('open', 'acknowledged', 'contained', 'closed');
CREATE TYPE unit_status AS ENUM ('available', 'en_route', 'on_site', 'maintenance', 'offline');
CREATE TYPE intervention_status AS ENUM ('planned', 'en_route', 'on_site', 'completed', 'cancelled');
CREATE TYPE assignment_status AS ENUM ('dispatched', 'arrived', 'released', 'cancelled');
CREATE TYPE decision_mode AS ENUM ('auto_suggested', 'manual');

CREATE TABLE event_types (
    code TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    default_severity INT NOT NULL CHECK (default_severity BETWEEN 1 AND 5),
    recommended_unit_types TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE unit_types (
    code TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    capabilities TEXT NOT NULL,
    speed_kmh INT,
    max_crew INT,
    illustration TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title TEXT NOT NULL,
    description TEXT,
    report_source TEXT,
    address TEXT,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    severity INT NOT NULL CHECK (severity BETWEEN 1 AND 5),
    status event_status NOT NULL DEFAULT 'open',
    event_type_code TEXT NOT NULL REFERENCES event_types(code),
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);

CREATE INDEX events_location_idx ON events USING GIST (location);
CREATE INDEX events_status_idx ON events(status);

CREATE TABLE event_logs (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    code TEXT NOT NULL,
    actor TEXT,
    payload JSONB DEFAULT '{}'::JSONB
);

CREATE TABLE interventions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    status intervention_status NOT NULL DEFAULT 'planned',
    priority INT NOT NULL DEFAULT 3 CHECK (priority BETWEEN 1 AND 5),
    decision_mode decision_mode NOT NULL DEFAULT 'manual',
    created_by TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_sign TEXT NOT NULL UNIQUE,
    unit_type_code TEXT NOT NULL REFERENCES unit_types(code),
    home_base TEXT,
    status unit_status NOT NULL DEFAULT 'available',
    location GEOGRAPHY(POINT, 4326),
    last_contact_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX units_location_idx ON units USING GIST (location);
CREATE INDEX units_status_idx ON units(status);

CREATE TABLE intervention_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    intervention_id UUID NOT NULL REFERENCES interventions(id) ON DELETE CASCADE,
    unit_id UUID NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    role TEXT,
    status assignment_status NOT NULL DEFAULT 'dispatched',
    dispatched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    arrived_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ
);

CREATE TABLE unit_telemetry (
    id BIGSERIAL PRIMARY KEY,
    unit_id UUID NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    location GEOGRAPHY(POINT, 4326),
    heading INT,
    speed_kmh DOUBLE PRECISION,
    status_snapshot JSONB DEFAULT '{}'::JSONB
);

CREATE TABLE personnel (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name TEXT NOT NULL,
    rank TEXT,
    status TEXT NOT NULL DEFAULT 'available',
    home_base TEXT,
    contact TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE intervention_crew (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    intervention_id UUID NOT NULL REFERENCES interventions(id) ON DELETE CASCADE,
    personnel_id UUID NOT NULL REFERENCES personnel(id) ON DELETE RESTRICT,
    role TEXT,
    status TEXT NOT NULL DEFAULT 'assigned',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ
);

INSERT INTO event_types (code, name, description, default_severity, recommended_unit_types)
VALUES
    ('FIRE_URBAN', 'Incendie urbain', 'Feu en zone urbaine dense', 4, ARRAY['FPT', 'EPA']),
    ('FIRE_INDUSTRIAL', 'Incendie industriel', 'Feu localisé dans une zone industrielle', 5, ARRAY['FPT', 'FPTL', 'EPA']),
    ('RESCUE_MEDICAL', 'Secours médical', 'Assistance médicale urgente', 3, ARRAY['VSAV']),
    ('HAZMAT', 'Matières dangereuses', 'Incident impliquant des substances dangereuses', 5, ARRAY['FPTL', 'VLHR']);

INSERT INTO unit_types (code, name, capabilities, speed_kmh, max_crew, illustration)
VALUES
    ('FPT', 'Fourgon Pompe Tonne', 'Lutte incendie polyvalente', 70, 6, NULL),
    ('FPTL', 'Fourgon Pompe Tonne Léger', 'Interventions incendie en milieu restreint', 80, 4, NULL),
    ('EPA', 'Échelle pivotante automatique', 'Accès en hauteur, sauvetage', 65, 3, NULL),
    ('VSAV', 'Véhicule de secours et d''assistance aux victimes', 'Secours médicalisé', 90, 4, NULL),
    ('VLHR', 'Véhicule léger haute-résistance', 'Risques technologiques / hazmat', 85, 3, NULL);
COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;
DROP TABLE IF EXISTS intervention_crew;
DROP TABLE IF EXISTS personnel;
DROP TABLE IF EXISTS unit_telemetry;
DROP TABLE IF EXISTS intervention_assignments;
DROP TABLE IF EXISTS interventions;
DROP TABLE IF EXISTS event_logs;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS units;
DROP TABLE IF EXISTS unit_types;
DROP TABLE IF EXISTS event_types;

DROP TYPE IF EXISTS decision_mode;
DROP TYPE IF EXISTS assignment_status;
DROP TYPE IF EXISTS intervention_status;
DROP TYPE IF EXISTS unit_status;
DROP TYPE IF EXISTS event_status;
COMMIT;
-- +migrate StatementEnd

-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- Table to store calculated routes for units in transit
-- Uses LINESTRING for continuous interpolation via ST_LineInterpolatePoint
CREATE TABLE IF NOT EXISTS unit_routes (
    unit_id UUID PRIMARY KEY REFERENCES units(id) ON DELETE CASCADE,
    intervention_id UUID REFERENCES interventions(id) ON DELETE SET NULL,
    route_geometry GEOMETRY(LINESTRING, 4326) NOT NULL,
    route_length_meters DOUBLE PRECISION NOT NULL,
    estimated_duration_seconds DOUBLE PRECISION NOT NULL,
    progress_percent DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (progress_percent >= 0 AND progress_percent <= 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Spatial index for route geometries
CREATE INDEX IF NOT EXISTS unit_routes_geometry_idx ON unit_routes USING GIST (route_geometry);

-- Index for finding routes by intervention
CREATE INDEX IF NOT EXISTS unit_routes_intervention_idx ON unit_routes(intervention_id);

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;
DROP TABLE IF EXISTS unit_routes;
COMMIT;
-- +migrate StatementEnd

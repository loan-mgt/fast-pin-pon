-- +migrate Up
-- +migrate StatementBegin
BEGIN;

-- 1. Add column to store the component ID
ALTER TABLE public.routing_ways_vertices_pgr 
ADD COLUMN IF NOT EXISTS component_id integer;

-- 2. Pre-calculate connected components and update the column
-- This might take a moment but is done only once.
WITH components AS (
    SELECT node, component
    FROM pgr_connectedComponents(
        'SELECT gid AS id, source, target, cost_s AS cost FROM routing_ways WHERE cost_s > 0'
    )
)
UPDATE public.routing_ways_vertices_pgr v
SET component_id = c.component
FROM components c
WHERE v.id = c.node;

-- 3. Create an index for fast lookup
CREATE INDEX IF NOT EXISTS idx_routing_ways_vertices_pgr_component_id 
ON public.routing_ways_vertices_pgr (component_id);

-- Optional: Analzye table to update stats immediately
ANALYZE public.routing_ways_vertices_pgr;

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;
DROP INDEX IF EXISTS idx_routing_ways_vertices_pgr_component_id;
ALTER TABLE public.routing_ways_vertices_pgr DROP COLUMN IF EXISTS component_id;
COMMIT;
-- +migrate StatementEnd

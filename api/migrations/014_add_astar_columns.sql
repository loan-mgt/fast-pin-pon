-- +migrate Up
-- =============================================================================
-- A* Routing Schema: Adds heuristic coordinates and optimization indexes.
-- =============================================================================

-- 1. Ensure A* heuristic coordinate columns exist in the edge table
ALTER TABLE public.routing_ways ADD COLUMN IF NOT EXISTS x1 float8;
ALTER TABLE public.routing_ways ADD COLUMN IF NOT EXISTS y1 float8;
ALTER TABLE public.routing_ways ADD COLUMN IF NOT EXISTS x2 float8;
ALTER TABLE public.routing_ways ADD COLUMN IF NOT EXISTS y2 float8;

-- 2. Optimization Indexes
-- We use a composite index for the A* heuristic coordinates
CREATE INDEX IF NOT EXISTS idx_routing_ways_astar_coords ON public.routing_ways (x1, y1, x2, y2);

-- Ensure source/target are indexed (often used in lookup)
CREATE INDEX IF NOT EXISTS idx_routing_ways_source_idx ON public.routing_ways (source);
CREATE INDEX IF NOT EXISTS idx_routing_ways_target_idx ON public.routing_ways (target);

-- +migrate Down
-- Dropping indexes
DROP INDEX IF EXISTS idx_routing_ways_astar_coords;
DROP INDEX IF EXISTS idx_routing_ways_source_idx;
DROP INDEX IF EXISTS idx_routing_ways_target_idx;

-- Dropping columns
ALTER TABLE public.routing_ways DROP COLUMN IF EXISTS x1;
ALTER TABLE public.routing_ways DROP COLUMN IF EXISTS y1;
ALTER TABLE public.routing_ways DROP COLUMN IF EXISTS x2;
ALTER TABLE public.routing_ways DROP COLUMN IF EXISTS y2;

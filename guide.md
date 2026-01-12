# Road Data Import Guide (`lyon.mbtiles`)

## Developer Setup Workflow

To set up the routing engine, developers follow two distinct phases:

1.  **Schema Migration (Automated)**: Run the API migrations. This creates all tables (`routing_ways`, `unit_routes`, etc.) and the necessary A* columns.
2.  **Data Import (Manual)**: Follow the steps below to import geographical data and build the routing topology.

**Purpose**

This guide explains how to import and prepare a road network for the Fast Pin Pon routing engine (A*), **specifically for emergency vehicles**.

Emergency vehicles:

* May drive **against one‑way restrictions**
* Use **different speeds depending on road type**
* Require accurate mid‑road snapping (not just intersections)

As a result, **one‑way logic is completely dropped** and the routing graph is built as fully bidirectional.

---

## Prerequisites

* **Podman / Docker**
* **PostgreSQL + PostGIS + pgRouting**
* **lyon.mbtiles** located at `maptiler/maps/lyon.mbtiles`

---

## Step 1: Extract Raw Road Data

Extract the `transportation` layer from the MBTiles file into a staging table.

```bash
podman run --rm --network fast-pin-pon_internal \
  -e PGPASSWORD=fastpinpon \
  -v ./maptiler/maps:/data \
  ghcr.io/osgeo/gdal:alpine-small-latest \
  ogr2ogr -f PostgreSQL "PG:host=fast-pin-pon-postgres dbname=fastpinpon user=fastpinpon" \
  /data/lyon.mbtiles transportation \
  -nln import_staging_ways \
  -nlt PROMOTE_TO_MULTI \
  -overwrite
```

---

## Step 2: Clean, Segment, and Prepare the Network

This SQL pipeline performs:

1. Geometry cleanup
2. **Metric segmentation (50 m)**
3. Emergency‑specific cost modeling
4. Routing topology creation
5. Graph pruning

### 2.1 Reset Target Table

```sql
TRUNCATE TABLE routing_ways RESTART IDENTITY CASCADE;
```

---

### 2.2 Geometry Cleaning & Metric Segmentation

**Important concept: CRS choice**

* EPSG:4326 (lat/lon) uses **degrees**, not meters
* Segmentation and length calculations should always be done in a **metric CRS**
* For Lyon, we use **EPSG:2154 (RGF93 / Lambert‑93)**

**Workflow:**

1. Transform to EPSG:2154
2. Segment every geometry into max 50 m pieces
3. Transform back to EPSG:4326 for storage and routing

```sql
INSERT INTO routing_ways (geom, class, length_m, cost_s, reverse_cost_s)
WITH cleaned AS (
    SELECT
        ST_Transform(
            ST_Segmentize(
                ST_Transform(
                    (ST_Dump(
                        CASE
                            WHEN ST_GeometryType(wkb_geometry) LIKE '%Polygon%'
                            THEN ST_Boundary(wkb_geometry)
                            ELSE wkb_geometry
                        END
                    )).geom,
                    2154
                ),
                50
            ),
            4326
        ) AS geom,
        class
    FROM import_staging_ways
    WHERE class IN (
        'motorway', 'trunk', 'primary', 'secondary',
        'tertiary', 'residential', 'service', 'minor'
    )
)
SELECT
    ST_MakeLine(ST_PointN(geom, n), ST_PointN(geom, n + 1)) AS geom,
    class,
    0, 0, 0
FROM cleaned
CROSS JOIN LATERAL generate_series(1, ST_NPoints(geom) - 1) AS n;
```

---

### 2.3 Length Calculation (Meters)

```sql
UPDATE routing_ways
SET length_m = ST_Length(geom::geography);
```

---

### 2.4 Build Routing Topology

A slightly larger snapping tolerance is used to avoid micro‑disconnections.

```sql
SELECT pgr_createTopology(
    'routing_ways',
    0.00005,
    'geom',
    'gid'
);
```

```sql
CREATE INDEX IF NOT EXISTS idx_routing_ways_geom
ON routing_ways USING GIST (geom);

CREATE INDEX IF NOT EXISTS idx_routing_ways_vertices_geom
ON routing_ways_vertices_pgr USING GIST (the_geom);
```

---

### 2.5 A* Heuristic Coordinates

```sql
UPDATE routing_ways SET
    x1 = ST_X(ST_StartPoint(geom)),
    y1 = ST_Y(ST_StartPoint(geom)),
    x2 = ST_X(ST_EndPoint(geom)),
    y2 = ST_Y(ST_EndPoint(geom));
```

---

### 2.6 Emergency Speed Model (by Road Type)

Emergency vehicles ignore traffic rules but **still move faster on higher‑class roads**.

| Road class      | Speed (km/h) | Speed (m/s) |
| --------------- | ------------ | ----------- |
| motorway        | 90           | 25.0        |
| trunk / primary | 70           | 19.4        |
| secondary       | 60           | 16.7        |
| tertiary        | 50           | 13.9        |
| residential     | 40           | 11.1        |
| service / other | 30           | 8.3         |

```sql
UPDATE routing_ways SET
    cost_s = length_m /
        CASE
            WHEN class = 'motorway' THEN 25.0
            WHEN class IN ('trunk','primary') THEN 19.4
            WHEN class = 'secondary' THEN 16.7
            WHEN class = 'tertiary' THEN 13.9
            WHEN class = 'residential' THEN 11.1
            ELSE 8.3
        END,
    reverse_cost_s = cost_s;
```

**Note:**

* `reverse_cost_s = cost_s` makes the graph fully bidirectional
* One‑way restrictions are intentionally ignored for emergency routing

---

### 2.7 Remove Disconnected Graph Islands

Keep only the largest connected component to avoid unreachable routes.

```sql
UPDATE routing_ways_vertices_pgr v
SET component_id = c.component
FROM pgr_connectedComponents(
    'SELECT gid AS id, source, target, cost_s AS cost, reverse_cost_s AS reverse_cost FROM routing_ways'
) c
WHERE v.id = c.node;

WITH main_component AS (
    SELECT component_id
    FROM routing_ways_vertices_pgr
    GROUP BY component_id
    ORDER BY COUNT(*) DESC
    LIMIT 1
)
DELETE FROM routing_ways
WHERE source NOT IN (
    SELECT id
    FROM routing_ways_vertices_pgr
    WHERE component_id = (SELECT component_id FROM main_component)
);
```

---

## Why Segmentation Matters

Routing normally happens **between graph vertices**. Without segmentation, long roads may only have vertices at intersections, causing:

* Poor snapping accuracy
* Unrealistic entry/exit points
* Visual mismatch between unit position and route

**50 m segmentation**:

* Increases vertex density
* Allows mid‑road access
* Produces visually accurate emergency paths

---

## Summary

✔ Fully bidirectional emergency graph
✔ Metric‑correct segmentation
✔ Road‑class‑based emergency speeds
✔ Robust topology snapping
✔ Optimized for A* routing

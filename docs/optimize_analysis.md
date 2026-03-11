# Tile Rendering Performance Analysis

## Context

- `features` table: **~5M rows**
- Observed tile creation times: **4–5 minutes** per tile
- Target: **sub-second** most tiles, **≤2 seconds** worst case

---

## Root Cause Analysis

### 1. `ST_Intersection` in SELECT is the primary killer (`ChartDao.kt:66`)

```sql
SELECT st_asbinary(st_intersection(geom, (table include))), ...
FROM features
WHERE chart_id = $2 AND $3 <@ z_range AND st_intersects(geom, ...)
```

`ST_Intersection` clips every matching geometry to the tile envelope. For complex nautical
chart polygons (coastlines, depth areas), each call can take milliseconds. If 1,000 features
match per chart × 5 charts × even 5ms each = 25 seconds minimum. For busier areas it's far
worse.

### 2. Missing composite index for the dominant filter (`DbMigrations.kt:56-61`)

The three WHERE conditions hit **three separate indexes**. PostgreSQL picks one — almost
certainly `features_chart_id_idx` — then does sequential scan on ~tens-of-thousands of rows
per chart to check `z_range` containment and `st_intersects`. With 4.8M rows across hundreds
of charts, individual chart scans are still large.

The `z_range INT4RANGE` with GIST can't be combined with a B-tree `chart_id` index into a
composite index, so both conditions can never be filtered together efficiently.

### 3. N+1 query pattern (`TileEncoder.kt:76-115`)

One query to find charts covering the tile, then **one separate `findChartFeaturesAsync4326`
call per chart**. Each opens a new DB connection (`PgDataSource` creates a fresh connection
every call).

### 4. O(n) layer lookup per feature (`LayerFactory.preTileEncode`)

Linear search through 100+ layer instances for every single feature in the tile.

---

## Recommendations (Priority Order)

### Priority 1 — Replace `z_range` with `z_min`/`z_max` + composite index

This is the single highest-impact DB change. Split `INT4RANGE` into two `INTEGER` columns and
add a composite B-tree index:

```sql
ALTER TABLE features ADD COLUMN z_min INTEGER NOT NULL DEFAULT 0;
ALTER TABLE features ADD COLUMN z_max INTEGER NOT NULL DEFAULT 22;
UPDATE features SET z_min = lower(z_range), z_max = upper(z_range) - 1;
CREATE INDEX features_chart_zoom_idx ON features (chart_id, z_min, z_max);
DROP INDEX features_chart_id_idx;
DROP INDEX features_zoom_idx;
```

Query becomes:
```sql
WHERE chart_id = $2 AND $3 >= z_min AND $3 <= z_max AND st_intersects(geom, ...)
```

Now PostgreSQL uses one composite index scan to narrow by `chart_id` + zoom, then applies the
GIST spatial filter on a much smaller candidate set. This avoids the expensive range
containment scan across all chart rows.

### Priority 2 — Use `ST_ClipByBox2D` for simple tile envelopes

`ST_ClipByBox2D` is 3-10x faster than `ST_Intersection` for clipping to a bounding box. The
`include` polygon starts as a simple rectangle (the tile envelope) and only becomes complex
after `difference()` operations. For the majority case (first chart, or charts that don't
overlap), this would be a huge win:

```sql
SELECT st_asbinary(ST_ClipByBox2D(geom, ST_Envelope(st_geomfromwkb($1, 4326)))), props, layer
```

For cases where `include` is not a simple box (subsequent charts after `difference()`), you'd
still need `ST_Intersection`. Could add a flag or check geometry type.

### Priority 3 — Batch chart feature queries

Replace the N+1 pattern with a single query using `ANY`:

```sql
WITH tile AS (VALUES (st_geomfromwkb($1, 4326)))
SELECT st_asbinary(st_intersection(f.geom, (table tile))), f.props, f.layer, f.chart_id
FROM features f
WHERE f.chart_id = ANY($2)          -- array of chart IDs
  AND $3 >= f.z_min AND $3 <= f.z_max
  AND st_intersects(f.geom, (table tile))
ORDER BY f.chart_id;
```

This eliminates N-1 DB round-trips and N-1 connection establishments.

### Priority 4 — HashMap in LayerFactory

Change from linear sequence search to `Map<String, Layerable>`:

```kotlin
// Instead of: layerables.forEach { if (feature.layer == it.key) ... }
// Use: layerablesMap[feature.layer]?.preTileEncode(feature)
```

O(1) per feature instead of O(100+).

---

## Expected Impact

| Fix | Estimated Speedup |
|-----|-------------------|
| z_min/z_max composite index | 5-20x (eliminates large table scans) |
| ST_ClipByBox2D | 3-10x (on geometry operations) |
| Batch queries | 2-5x (fewer round-trips) |
| HashMap layer lookup | 1.1-2x (CPU overhead) |
| **Combined** | **30-200x** → ~1-10s per tile |
| Pre-computation | 100-1000x → <100ms per tile |

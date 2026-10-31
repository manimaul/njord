# Faster base map tiles: subdivided Natural Earth polygons

_Written 2026-10-31. A follow-up to the Rust port ([RUST_PORT_PLAN.md §11](RUST_PORT_PLAN.md#11-follow-ups-after-the-port)); not part of it._

## Problem

From zoom 5 up, every tile reads its Natural Earth base map with one query (`njord-db`
`find_base_features`, the same SQL as Kotlin's `BaseFeatureDao.findFeaturesAsync`):

```sql
WITH include AS (VALUES (st_geomfromwkb($1, 4326)))
SELECT st_asbinary(st_intersection(geom, (table include))), props, layer
FROM base_features
WHERE scale = $2
  AND st_intersects(geom, (table include))
ORDER BY id;
```

`include` is the part of the tile that no chart covers. The GiST index finds the candidate rows
quickly, but Natural Earth stores some layers as a few huge polygons, and `st_intersection` has
to process the whole polygon for every tile, however little of it lands there. The 1:10m data
(`scale = 10000000`) in a local database with all of Natural Earth loaded:

| File | Rows | Points | Largest row |
|---|---:|---:|---:|
| `ne_10m_ocean.shp` | 1 | 446,794 | 446,794 |
| `ne_10m_land.shp` | 10 | 446,170 | 270,426 |
| `ne_10m_coastline.shp` | 4,133 | 410,957 | 16,538 |
| `ne_10m_glaciated_areas.shp` | 1,879 | 238,414 | 22,540 |
| `ne_10m_lakes.shp` | 1,346 | 160,991 | 24,279 |

A z10 tile over Puget Sound takes 1.2–1.6 s on its own in the dev PostGIS, and returns three
features with 18 points between them. When the map loads many tiles at once they compete for the
database, and the Rust server logs `slow statement` warnings of 3–5.5 s (sqlx warns above 1 s by
default; Kotlin never logged them, but runs the same query). The tile cache (`useTileCache`)
hides this after a tile's first render, but not for the first render itself, the region export
or `walk_tiles`.

## Proposal

Store every base-map **polygon** cut into small pieces with `ST_Subdivide`, intersect only the
pieces that touch the tile, and merge each feature's pieces back with `ST_Union` before
returning them.

The merge is required. `land` and `lakes` (`Lndare`, `Lakare`) draw a `CSTLN` line layer over
their polygons (`eq_type_line_string_or_polygon`), so each piece's outline would be drawn, and
unmerged pieces would show coastline-coloured seams across land. `ST_Union` dissolves the shared
edges. `ocean` is fill-only and would not need the merge, but one query for every layer is
simpler.

### Schema

A table beside `base_features`, so the rows ingest writes and the properties tiles read stay where
they are:

```sql
CREATE TABLE IF NOT EXISTS base_feature_parts
(
    feature_id BIGINT                   NOT NULL REFERENCES base_features (id) ON DELETE CASCADE,
    scale      INTEGER                  NOT NULL,
    geom       GEOMETRY(GEOMETRY, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS base_feature_parts_gist ON base_feature_parts USING GIST (geom);
CREATE INDEX IF NOT EXISTS base_feature_parts_scale_idx ON base_feature_parts (scale);
```

`ON DELETE CASCADE` keeps the pieces in step with `delete_base_features` (a re-uploaded file), and
`nuke`'s `TRUNCATE … base_features RESTART IDENTITY CASCADE` empties the new table with it.

### Ingest

After each `insert_base_feature` in `ingest/natural_earth.rs`, write its pieces. Lines and points
get one row with the whole geometry: the line layers have no single huge row (the largest
coastline is 16,538 points), and merging subdivided lines back would need `ST_LineMerge`, which
can reorder or split them.

```sql
INSERT INTO base_feature_parts (feature_id, scale, geom)
SELECT id, scale,
       CASE WHEN st_dimension(geom) = 2 AND st_npoints(geom) > 256
            THEN st_subdivide(geom, 256) ELSE geom END
FROM base_features WHERE id = $1;
```

### Query

```sql
WITH include AS (VALUES (st_geomfromwkb($1, 4326))),
     cut AS (SELECT feature_id, st_union(st_intersection(geom, (table include))) AS geom
             FROM base_feature_parts
             WHERE scale = $2
               AND st_intersects(geom, (table include))
             GROUP BY feature_id)
SELECT st_asbinary(cut.geom), f.props, f.layer
FROM cut JOIN base_features f ON f.id = cut.feature_id
ORDER BY f.id;
```

Same columns and order as today, so `TileEncoder` and `render_base_map` do not change.

### Existing databases

A migration backfills the table from `base_features`: the `INSERT … SELECT` above without the
`WHERE`. It took 2 min 40 s – 3 min for the 1:10m data on the dev PostGIS, so it runs once,
under the migration lock, and the server should log that it is running.

## Measurements

Dev PostGIS (Podman on macOS), the 1:10m data above, `ST_Subdivide(geom, 256)` on every row into
a temporary table (36,929 pieces, at most 256 points each), each query run alone:

| Tile | Today | Pieces, not merged | Pieces, merged (proposal) |
|---|---:|---:|---:|
| z10 / 164 / 357 (Puget Sound) | 1,156 ms | 8 ms | 195 ms |
| z5 / 5 / 11 (US west coast) | 1,630 ms | 148 ms | 350 ms |
| z7 / 10 / 48 (open Pacific) | 1,371 ms | 5 ms | 5 ms |

On the z5 tile the merged result matched today's for all 85 features: none missing, no area
difference above 1e-12 and no outline length difference above 1e-9 (no seams). It has slightly
more points (8,250 against 8,167): the vertices `ST_Subdivide` adds where its cuts cross a
polygon's edge. They are collinear, and the tile's pixel rounding and simplification should drop
most of them. Not yet compared: the decoded tiles themselves.

## Why after the port

- **The schema is frozen during the port** (RUST_PORT_PLAN.md §8): while a Kotlin ingest can
  still write `base_features`, it would leave `base_feature_parts` stale. After Phase 7 only the
  Rust ingest writes it.
- **The goldens are unaffected.** The test databases hold only the Natural Earth 1:110m
  coastline extract (lines, used below zoom 3), so P-TE-5, the conformance tiles and the region
  archives do not change. Check that again when the change is made.
- **The tile output changes slightly** (the extra seam vertices above), so compare a sample of
  decoded base-map tiles before and after, P-TE-5 style, on a database with all of Natural Earth.

## Other options considered

- **Subdivide on the fly in the query.** No: subdividing the ocean polygon alone takes minutes.
- **`ST_ClipByBox2D` instead of `ST_Intersection`.** Faster per vertex, but it still walks every
  vertex of the huge polygons, only works when `include` is a rectangle (it is not once a chart
  covers part of the tile), and can return invalid geometry. Not measured.
- **Store the pieces in `base_features` itself.** Fewer tables, but the pieces then have to be
  merged for anything else that reads base features, and every row's properties are repeated per
  piece.
- **Only quiet the log** (raise sqlx's slow-statement threshold, or `RUST_LOG=…,sqlx=error`).
  Worth doing anyway for production logs, but the latency remains.

# Offline Marine Chart Tiles: Strategy Document

## 1. Problem

An app displays marine charts from the Njord server using MapLibre Native. 
We need offline support for large coastal regions (e.g. the
Washington and Oregon coast) across zoom levels 0–18, while keeping
download size, download time, and server load manageable.

MapLibre's built-in `OfflineManager` downloads and stores one tile per
`(z, x, y)` for every zoom level in the requested range and bounding box.
Tile count grows roughly 4x per zoom level, so a full z0–18 offline region
over a large coastline produces tens of millions of tile requests — most of
them over open ocean or land with no charted features. This is expensive
in storage, transfer time, and server-side tile generation load.

Njord already exposes chart and feature data for a region as a compact
SQLite export. The goal is to use that source data more efficiently than a
brute-force tile pyramid.

## 2. Chosen Strategy: Sparse Region MBTiles

Rather than downloading every tile in a bounding box, generate an
**MBTiles file per region containing only the tiles that actually
intersect chart feature data**. Open ocean, uncharted land, and any area
with nothing to draw simply produces no tile rows — so the file size
scales with charted detail, not with bounding-box area × zoom depth.

### 2.1 Deriving zoom range from chart scale

Marine chart features carry a min/max scale (e.g. S-57/S-101 usage bands:
Overview, General, Coastal, Approach, Harbour, Berthing). Each feature's
scale range converts to an approximate zoom range, either via the
standard web-mercator scale-per-zoom approximation
(`zoom ≈ log2(559082264 / scale_denominator)`) or a fixed lookup table per
usage band:

| Usage band | Approx. scale     | Zoom range |
|---|---|---|
| Overview   | 1:3,000,000+       | z0–6  |
| General    | ~1:1,500,000       | z6–10 |
| Coastal    | ~1:300,000         | z10–13 |
| Approach   | ~1:75,000          | z13–16 |
| Harbour / Berthing | ~1:20,000 and finer | z16–18 |

Each feature is only emitted into tiles within its computed zoom range —
the same per-feature minzoom/maxzoom mechanism used by tools like
tippecanoe.

### 2.2 Generation

MBTiles generation is a **batch, one-time process per region version**,
not a per-request operation. Njord generates the sparse MBTiles
server-side and ships the `.mbtiles` file as the downloadable artifact,
which moves all clipping, simplification, and topology repair off the
device and guarantees every client gets the same validated tile set.

This is implemented in
`server/src/nativeMain/kotlin/io/madrona/njord/ingest/RegionExporter.kt`:

- Candidate `(z, x, y)` coordinates are compiled per feature from that
  feature's `MINZ`/`MAXZ` (S-57 `SCAMIN`/`SCAMAX`) capped at its chart's
  own compiled-scale zoom, so an overview chart yields a shallow pyramid
  and a harbour chart a deep one — the table above describes the effect,
  not a hardcoded config.
- Each candidate tile is rendered by the same `TileEncoder` used for live
  tile serving; **tiles with no content are skipped entirely**, leaving no
  row in the `tiles` table. This is what makes the dataset sparse.
- Non-empty tiles are gzip-compressed and inserted with standard MBTiles
  TMS row order (`tile_row = (2^z - 1) - y`), alongside a `metadata` table
  carrying `format=pbf`, `minzoom`/`maxzoom` (the actual written range,
  not the requested one), `bounds`, and `center`.
- The `WORLD` region is the one exception: it has no charts to derive a
  sparse set from, so it renders the full quad tree to `WORLD_MAX_ZOOM` as
  an always-available coarse base layer.

The expensive work therefore happens once per region version, not once per
tile per device.

### 2.3 Artifacts per region version

Each successful export produces exactly two files in the region directory,
keyed to the same immutable `${REGION}_${timestamp}` stem:

| File | Contents |
|---|---|
| `${REGION}_${ts}.mbtiles` | The sparse tile archive (§2.2) **plus the chart catalog tables** (§5) |
| `${REGION}_${ts}.mbtiles.sha256` | `sha256sum`-format checksum of the archive |

The chart catalog is not a separate sidecar. An MBTiles file is a SQLite
database, and MapLibre reads only its `metadata` and `tiles` tables, so the
catalog tables ride inside the same file — one download, one checksum, one
atomic rename, and no way to end up holding a catalog and an archive from
different exports. §5 covers the schema and the trade-off.

## 3. MapLibre Native Fork: Oversampling Sparse Tile Sets

A sparse tile set only works if the renderer can tell the difference
between *"this tile is present and legitimately empty"* and *"this tile
was never generated."* Stock MapLibre Native cannot, which breaks the
strategy above at exactly the point it matters. Our fork fixes this:

**[manimaul/maplibre-native @ `vector_source_oversample`](https://github.com/manimaul/maplibre-native/tree/vector_source_oversample)**
(commit `d83c308`, branched from upstream `main`).

** Note: An alternative approach considered is to always render the deepest zoom for each chart. However, this would not support overzoom display as undersampling is already supported in vector sources.

### 3.1 The upstream problem

`MBTilesFileSource` (and `PMTilesFileSource`) answered a tile query with
no matching row by returning `noContent = true` and **no error** — the
same response as a row whose `tile_data` is empty. `TileLoader` passed
`nullptr` to `Tile::setData()`, and `GeometryTile::onLayout()`
unconditionally set `renderable = true`.

The consequence: every gap in a sparse archive is a valid, renderable
blank tile. `updateRenderables` sees a renderable ideal tile, is satisfied,
and never ascends to a coarser ancestor. So panning into an area that has
only overview-scale coverage, or zooming past a region's deepest generated
zoom, draws *nothing* rather than stretching the coarser chart data that
is sitting right there in the same archive. The style-level `maxzoom`
overzoom trick doesn't help either, since a single source's maxzoom is one
number for the whole region while our per-feature zoom depth varies by
chart scale across it.

### 3.2 What the fork changes

1. **Distinguish absent from empty at the file source.**
   `MBTilesFileSource` tracks whether the `SELECT` returned any row at all;
   if not, it attaches `Response::Error::Reason::NotFound` to the (still
   `noContent`) response. `PMTilesFileSource` does the same when the
   directory lookup resolves to no tile entry.
2. **Propagate it through the tile loader.** `TileLoader::loadedData()`
   derives `available = !(error && reason == NotFound)` and passes it as a
   new second argument to `Tile::setData(data, available)`. `NotFound` is
   still not surfaced as a hard tile error.
3. **Make missing tiles non-renderable.** `GeometryTile` stores the flag
   and `onLayout()` now computes
   `renderable = dataAvailable || !oversampleMissingTiles`, so a genuinely
   missing tile is treated as unavailable and `updateRenderables` walks up
   to the nearest real ancestor and stretches it. A present-but-empty tile
   still renders blank, as it must.
4. **Opt-in per source, defaulting to upstream behavior.** New
   `Source::setOversampleMissingTiles(bool)` / `getOversampleMissingTiles()`
   on `mbgl::style::Source`, stored on `Source::Impl`, carried to tiles via
   a new `TileUpdateParameters::oversampleMissingTiles` field set by
   `TilePyramid::update()`. Exposed on Android as
   `Source.setOversampleMissingTiles(Boolean)` /
   `isOversampleMissingTiles()` with the matching JNI bindings. **Defaults
   to `false`** — with the flag off, behavior is byte-for-byte the upstream
   behavior, which keeps the fork cheap to rebase.
5. **Don't re-request the same ancestor N times.** For an ideal tile
   overscaled past its canonical zoom, `OverscaledTileID::scaledTo(z)`
   returns the *same* canonical tile for every `z` in
   `[canonical.z, overscaledZ)`. The ascent loop in
   `algorithm::updateRenderables` stepped through all of them, creating and
   requesting several `Tile` objects that resolve to one identical
   resource — which becomes very visible once gaps actually trigger the
   ascent. It now jumps straight to `canonical.z`, unless a configured
   `maxParentOverscaleFactor` would have capped the ascent earlier.

Raster tiles are unaffected: `RasterTile`/`RasterDEMTile` accept and ignore
the `available` argument, since a missing raster tile is already correctly
non-renderable via the absence of a parsed bucket.

Coverage added upstream-style in `test/`: `update_renderables` ascent
skipping, `MBTiles`/`PMTiles` non-existent-tile responses, the source
property's set/get/observer-notification semantics, and the three
`VectorTile` renderability cases (missing-by-default, missing with
oversampling on, present-but-empty with oversampling on).

## 4. Serving Tiles to MapLibre

### 4.1 Do not use MapLibre's OfflineManager region download

`OfflineManager` enumerates and requests every `(z, x, y)` in a bbox ×
zoom range regardless of whether data exists there, which reintroduces
the exact problem being avoided. The archive is a plain file download.

### 4.2 Preferred: point a vector source straight at the `.mbtiles` file

With the fork in place, no on-device import or tile-serving shim is needed
at all:

1. Download the region's `.mbtiles` file (verify against its `.sha256`
   sidecar) into app storage.
2. Add a vector source whose URL is `mbtiles:///abs/path/REGION_15_<ts>.mbtiles`.
   MapLibre reads the archive's `metadata` table as TileJSON — picking up
   `format=pbf`, the real `minzoom`/`maxzoom`, and `bounds` — synthesizes
   its own tile URL template, converts XYZ `y` to the TMS `tile_row`
   itself, and gunzips tile blobs. Row lookup, the TMS flip, and
   decompression are all handled internally — there is no interceptor or
   tile-serving shim to write — and the catalog tables sharing the file (§5)
   are never queried.
3. Call `setOversampleMissingTiles(true)` on that source. Without this the
   archive still loads, but every sparse gap renders blank (§3.1).

**Important note:** the downloaded `.mbtiles` file is not what the source
ultimately points at. §6 merges every installed region into a single device-side
store with the same schema, so tiles can be reference-counted across regions and
the archive can be discarded once installed. That store is read by the same
`mbtiles://` mechanism described here — the merge changes which file the URL
names, not how it is served.

### 4.3 How gaps render

- Where a coarser ancestor tile exists in the archive, the gap is filled by
  stretching it — detail degrades gracefully instead of vanishing.
- Where nothing coarser exists either, nothing is drawn for that
  source-layer, which reads as "no chart data here" rather than a
  rendering failure — provided the `WORLD` base-map archive (§2.2) is
  layered underneath at all zooms.

## 5. The Chart Catalog Tables

The tile archive answers "what does this pixel look like." It cannot answer
"which charts am I looking at," "what does this chart's `.TXT` notice say,"
"which edition do I have," or — critically for storage management — "which
tiles exist only because of this chart."

An MBTiles file is just a SQLite database with two required tables, and
MapLibre's `MBTilesFileSource` only ever reads those two: it queries
`metadata` once to build TileJSON, then `tiles` per tile request. Any other
table in the file is invisible to it. (This holds for upstream; it is still
unverified against the fork build in use, and §6.1 makes it an explicit gate.)
So the catalog goes **inside the `.mbtiles` archive** rather than beside it:

- One file to download, one checksum to verify, one atomic rename, one
  prune — no way for a catalog and an archive to get out of sync or to be
  shipped as a mismatched pair.
- The chart→tile edges can be pruned against `tiles` with a plain
  same-database `DELETE` (§5.2), no `ATTACH` and no cross-file join.
- The device's merged store (§6) ends up the same shape as the archive, so
  install is `ATTACH` + `INSERT … SELECT` rather than a bespoke reader.

The cost is that the archive is no longer a *minimal* MBTiles file. It
remains a valid one — the spec doesn't forbid additional tables, and
generic tools (`mbutil`, tileserver-gl, `mbtiles` inspectors) ignore what
they don't recognize — but anything that round-trips the archive by
exporting and re-packing tiles will silently drop the catalog.

### 5.1 Catalog schema

Created by `RegionExporter` in the same `SqliteDb.open(path)` block that
writes `metadata` and `tiles`:

```sql
-- One row per chart included in this region's archive. Mirrors the
-- server's `charts` table (server/src/nativeMain/kotlin/io/madrona/njord/db/DbMigrations.kt),
-- with PostGIS/JSONB types flattened to TEXT.
--
-- `name` (DSID_DSNM) is the primary key, and the server's surrogate
-- `charts.id` is deliberately not carried into the archive at all. That id is
-- meaningless on the device: the same cell can come back under a different id
-- after a re-ingest, and the device merges rows from archives exported at
-- different times from different regions. Keying on the cell name makes "the
-- same chart" mean the same thing in every archive and lets the install merge
-- dedupe with a plain UPSERT (§6.3).
CREATE TABLE charts (
    name        TEXT    PRIMARY KEY,   -- DSID_DSNM — the stable identity
    scale       INTEGER NOT NULL,      -- DSPM_CSCL
    file_name   TEXT    NOT NULL,
    updated     TEXT    NOT NULL,      -- DSID_UADT
    issued      TEXT    NOT NULL,      -- DSID_ISDT
    zoom        INTEGER NOT NULL,      -- best display zoom, derived at ingest
    covr        TEXT    NOT NULL,      -- GeoJSON geometry, NOT WKB/WKT
    dsid_props  TEXT    NOT NULL,      -- JSON object, verbatim jsonb::text
    chart_txt   TEXT    NOT NULL,      -- JSON object: { "US5WA22A.TXT": "…" }
    ingested_at TEXT    NOT NULL       -- ISO-8601 UTC, server charts.ingested_at
);

-- Every tile this chart contributed content to. The join key is the tile
-- coordinate itself, in the same column names and the same TMS row convention
-- as the `tiles` table alongside it (tile_row = (2^z - 1) - y).
--
-- The naming is load-bearing, not cosmetic: the device's uninstall sweep
-- (§6.5) correlates this table against `tiles` by bare column name, and
-- install is an INSERT … SELECT out of here into a store of the same shape.
-- Naming these z/x/y instead would cost an alias in every downstream statement
-- and hand every one of them a chance to flip a coordinate.
CREATE TABLE chart_tiles (
    chart_name  TEXT    NOT NULL REFERENCES charts(name) ON DELETE CASCADE,
    zoom_level  INTEGER NOT NULL,
    tile_column INTEGER NOT NULL,
    tile_row    INTEGER NOT NULL,
    PRIMARY KEY (chart_name, zoom_level, tile_column, tile_row)
) WITHOUT ROWID;

-- The reverse lookup: "which charts require this tile." This index is what
-- makes the uninstall sweep in §6.5 a lookup rather than a table scan, and
-- it is not optional.
CREATE INDEX chart_tiles_tile_idx
    ON chart_tiles (zoom_level, tile_column, tile_row);
```

There is no second `metadata` table — the archive already has one, and it
is the natural place for the catalog's own bookkeeping. MapLibre turns
every row in it into a TileJSON member, and its `Tileset` converter looks
up only the members it knows (`tiles`, `scheme`, `minzoom`, `maxzoom`,
`bounds`, `attribution`, `encoding`), ignoring the rest — so extra rows are
inert as long as they don't collide with those names. Prefix them:

| `metadata.name` | Value |
|---|---|
| `njord:schema_version` | Catalog schema version, so a client can reject a file it doesn't understand |
| `njord:region` | Region name, e.g. `REGION_15` |
| `njord:chart_count` | Rows in `charts` |
| `njord:tile_count` | Rows in `tiles` |
| `njord:generated_at` | Export timestamp, ISO-8601 UTC |

Two of these are consumed, not merely informational, and their values are
therefore a contract:

- **`njord:region`** is what the device writes into `tiles.rendered_from_region`
  at install (§6.7). It must match `RegionManifestEntry.name` exactly.
- **`njord:generated_at`** is what the device stores as
  `installed_regions.created_at` and compares against the manifest's
  `RegionManifestEntry.createdAt` to decide `UPDATE_AVAILABLE` (§6.8). **It must
  be the same instant as that manifest field**, which `buildManifest()` derives
  by parsing the archive's own filename stem via `parseArchiveTimestamp()` —
  *not* a separately sampled `Clock.System.now()`. `currentTimestamp()` writes
  that stem in server-local time with no offset marker, so a second sampling, a
  different zone, or a DST boundary between the two writes produces two values
  that differ by a whole offset and silently break the status comparison.
  Derive the metadata row from the same stem instant and serialize it as
  ISO-8601 UTC.

`chart_tiles` is the largest table by row count — it is the per-chart
expansion of a set `tiles` only stores unioned, so overlapping charts
multiply rows. It is also the narrowest, and `WITHOUT ROWID` makes the primary
key *be* the storage. Keying it on `charts.name` rather than a surrogate
`INTEGER` costs roughly 8 bytes per row over a numeric key; if it becomes a size
problem on the densest regions, give tiles a surrogate key and reduce the edge —
but measure on a real dense region first, don't pre-normalize.

### 5.2 Pruning orphaned edges

`compileTileCoordinates()` yields *candidate* coordinates: every tile touched by
the envelope of every feature in a chart, expanded across that feature's zoom
range. `renderAndWriteTiles()` then drops the candidates the encoder found no
real content for — that skipping is exactly what makes the archive sparse
(§2.2). So the per-chart edges are a superset of the tiles actually written, and
the difference is not small.

Left unpruned, those edges inflate the largest table in the file with rows
pointing at tiles that do not exist, and — worse — the device's refcount then
believes tiles are required that were never installed, so the uninstall sweep of
§6.5 keeps nothing alive but the sweep itself gets slower. Prune inside the
export, after the tiles are written and **before** the index is created:

```sql
DELETE FROM chart_tiles WHERE NOT EXISTS (
    SELECT 1 FROM tiles t
    WHERE t.zoom_level  = chart_tiles.zoom_level
      AND t.tile_column = chart_tiles.tile_column
      AND t.tile_row    = chart_tiles.tile_row);
```

`njord:chart_count` and `njord:tile_count` are counted after this pass.

### 5.3 Why the edge is chart-level

Tiles are claimed by *charts*, not by regions. A tile exists because some chart
contributed features to it, and one chart can belong to two regions — which is
why the device models the relationship as `region_charts` → `chart_tiles`
(§6.2) rather than storing a region → tile junction directly.

A direct `region_tiles(region_name, …)` edge cannot express a chart-level change
*inside* a region. An update replaces individual cells, not whole regions, so
region-granular edges force a full teardown of every tile the region touches
just to replace one cell's worth of them.

The chart-level edge also earns its keep away from storage management: it is
what lets the app answer "which charts cover this position, what edition is it,
and what does its `.TXT` notice say" while offline, which it cannot do today at
all.

### 5.4 Server-side changes

- `RegionDao.findChartsInRegion()` — add `ingested_at::text` to the select. The
  column already exists on the server (`DbMigrations.kt`, `ingested_at
  TIMESTAMPTZ NOT NULL DEFAULT now()`), but `RegionChart` has no field for it,
  so this is a data-class change as well as a SQL one. The server's `id` stays
  on `RegionChart` — `findFeaturesForChart(chart.id)` needs it — it just never
  reaches the archive (§5.1).
  `covr` needs no SQL change: the exporter already holds `covrWkb` and can
  produce both the GeoJSON string and the envelope via
  `OgrGeometry.fromWkb4326(covrWkb)` (`geoJson()` / `envelope()`), the same
  helpers `wktToGeojson()` already uses.
- `RegionExporter.compileTileCoordinates()` — currently returns a single
  merged `Set<TileCoord>`, unioning per-chart coordinates and discarding
  exactly the breakdown `chart_tiles` needs. It has to expose the per-chart
  view as well (either returning `Map<String, Set<TileCoord>>` keyed by chart
  name and unioning for the render, or invoking a callback per chart so edges
  stream out instead of being held in memory for a dense region).
- `RegionExporter.writeMbtilesArchive()` — this is the only place that
  changes structurally. New order inside the existing `SqliteDb.open(path)`
  block, all before the atomic rename that already exists:
  1. `db.exec` the two existing DDL statements plus `CREATE TABLE charts`
     and `CREATE TABLE chart_tiles`.
  2. `renderAndWriteTiles(...)` as today.
  3. Insert `charts` rows and stream `chart_tiles` edges.
  4. Prune orphaned edges (§5.2), then create `chart_tiles_tile_idx`.
  5. `writeMetadata(...)`, extended with the `njord:*` rows — with
     `njord:generated_at` taken from the archive stem instant, not resampled
     (§5.1).
- The `WORLD` region gets the same treatment structurally, but it has no charts,
  so its `charts` and `chart_tiles` come out empty. That is fine and expected:
  the device seeds its `installed_regions` row from `njord:generated_at`
  regardless, which is what finally retires the client's `WORLD` special case
  (§6.8).
- Nothing else moves. `pruneOldArchives()`, `writeChecksumSidecar()`,
  `archivesForRegion()`, `buildManifest()` and `RegionManifestEntry` are
  all untouched, because the number of files per region version is
  unchanged — which is the main reason to put the catalog inside the
  archive rather than beside it.

## 6. On-Device Storage and Region Uninstall

### 6.1 The model: one database, pending verification

§4.2 pointed a source straight at a downloaded `region.mbtiles`. That works for
one region, but every installed region has to be visible to the same map, and a
tile at a shared border belongs to more than one of them. So install merges each
archive into a single device-side store, and the downloaded `.mbtiles` is
discarded once verified.

That store has the same schema as the archive — `metadata` and `tiles` exactly
as MapLibre requires them, plus the catalog of §5.1 and the two bookkeeping
tables of §6.2. This is the whole point of putting the catalog inside the
archive: install is `ATTACH` + `INSERT … SELECT`, not a bespoke reader.

Tile blobs live in that database's `tiles` table rather than as loose
`{z}/{x}/{y}.pbf` files on disk. The client-side weighing of that choice — with
measurements — is in `mxmariner/docs/OFFLINE_TILES.md`. The two arguments that
decided it are structural rather than performance:

- Only a single database gives install, update, and uninstall one transaction
  spanning the catalog *and* the blobs. Split across two media there is no
  shared transaction, so a crash or process kill mid-install leaves the two
  disagreeing, and every reader forever after has to tolerate "the catalog says
  this tile exists, the file is gone."
- `rendered_from_region` (§6.7) is a column on `tiles`. Without that table the
  loose-file layout has to maintain a shadow row per tile anyway — the same row
  count, minus the blob, minus the atomicity, minus the compression.

**This is contingent on a verification that has not been done.** Before building
against it, confirm against the `manimaul/maplibre-native` fork build actually
in use (§3) that:

1. `MBTilesFileSource` reads a store carrying the extra catalog tables and
   ignores them. §5 asserts it queries only `metadata` and `tiles`; that is true
   of upstream and unverified on the fork.
2. `setOversampleMissingTiles(true)` produces the same ancestor-stretching
   fallback through an `mbtiles://` source that it demonstrably does through
   `file://` today. This is the property the entire sparse strategy rests on
   (§3.1) — verify it, do not assume it survives the source change.

If either fails, the fallback is **not** the loose-file layout that ships today.
It is catalog and `chart_tiles` in SQLite, blobs on disk, and an explicit
startup reconciliation pass that treats the filesystem as a cache the catalog is
authoritative over. That is a different trade-off with different permanent
obligations, and it needs writing down as its own decision rather than being
slid into as a default.

### 6.2 Device-side tables

The archive carries `metadata`, `tiles`, `charts`, `chart_tiles`. The store adds
two tables and one column the archive has no use for — an archive is one region
by construction, so it has nothing to record about which region a row came from.

```sql
CREATE TABLE installed_regions (
    name       TEXT PRIMARY KEY,   -- matches metadata 'njord:region'
    created_at TEXT NOT NULL       -- metadata 'njord:generated_at', ISO-8601 UTC
);

CREATE TABLE region_charts (
    region_name TEXT NOT NULL REFERENCES installed_regions(name) ON DELETE CASCADE,
    chart_name  TEXT NOT NULL REFERENCES charts(name)            ON DELETE CASCADE,
    PRIMARY KEY (region_name, chart_name)
) WITHOUT ROWID;
```

and `tiles` gains a provenance column:

```sql
CREATE TABLE tiles (
    zoom_level  INTEGER NOT NULL,
    tile_column INTEGER NOT NULL,
    tile_row    INTEGER NOT NULL,   -- TMS: (2^z - 1) - y
    tile_data   BLOB    NOT NULL,   -- stays gzipped; the source decompresses
    -- Which region's archive produced this blob (§6.7). SET NULL, not the
    -- default RESTRICT: a tile kept alive by a surviving neighbour still points
    -- at the region being uninstalled, and RESTRICT makes that DELETE fail.
    rendered_from_region TEXT REFERENCES installed_regions(name) ON DELETE SET NULL,
    PRIMARY KEY (zoom_level, tile_column, tile_row)
);
```

Region → tile is derived, never stored (§5.3). Where it is wanted, it is a view:

```sql
CREATE VIEW region_tiles AS
SELECT rc.region_name, ct.zoom_level, ct.tile_column, ct.tile_row
FROM region_charts rc
JOIN chart_tiles ct ON ct.chart_name = rc.chart_name;
```

`metadata` on the store serves the same purpose it does in the archive, with two
device-specific uses: `minzoom`/`maxzoom` must be seeded or the source runs
`SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles` on every style load, and
`attribution` — a TileJSON member the source already reads — is where the
client's tile-generation counter lives, replacing the `TileSet.attribution` hack
it uses today.

### 6.3 Install

`ATTACH` the downloaded archive after its SHA-256 has been verified, then merge.
All of this is one logical unit; see §6.6 on why step 4 must still be batched.

```sql
ATTACH ? AS pkg;

-- 1. Bookkeeping, from the archive's own metadata (§5.1).
INSERT OR REPLACE INTO installed_regions (name, created_at)
SELECT (SELECT value FROM pkg.metadata WHERE name = 'njord:region'),
       (SELECT value FROM pkg.metadata WHERE name = 'njord:generated_at');

-- 2. Charts, deduped on name. §6.9 decides who wins a collision.
INSERT INTO charts (name, scale, file_name, updated, issued, zoom,
                    covr, dsid_props, chart_txt, ingested_at)
SELECT name, scale, file_name, updated, issued, zoom,
       covr, dsid_props, chart_txt, ingested_at
FROM pkg.charts
WHERE true
ON CONFLICT(name) DO NOTHING;

-- 3. Claims and edges.
INSERT OR IGNORE INTO region_charts (region_name, chart_name)
SELECT :region, name FROM pkg.charts;

INSERT OR IGNORE INTO chart_tiles (chart_name, zoom_level, tile_column, tile_row)
SELECT chart_name, zoom_level, tile_column, tile_row FROM pkg.chart_tiles;

-- 4. Blobs. §6.7 governs the boundary-tile conflict.
INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data, rendered_from_region)
SELECT zoom_level, tile_column, tile_row, tile_data, :region FROM pkg.tiles
WHERE true
ON CONFLICT(zoom_level, tile_column, tile_row) DO NOTHING;
```

The `WHERE true` before `ON CONFLICT` is not decoration. In an
`INSERT … SELECT`, SQLite cannot tell an upsert's `ON CONFLICT` from a join's
`ON` clause; a `WHERE` clause resolves the ambiguity, and this is the documented
workaround.

Blobs copy verbatim — they stay gzipped exactly as the archive shipped them,
because `MBTilesFileSource` gunzips on read. There is no inflate step, which is
what makes install a copy rather than a decompress-and-scatter.

### 6.4 Update

Delete the region's row, run the orphan sweep, then install (§6.3). Deleting
`installed_regions` cascades `region_charts`, which is what lets the sweep see
which charts and tiles the region was the last claimant of.

Do not shortcut this by re-running install over the top. A cell *withdrawn* from
the region between two exports would keep its `charts` row, its edges, and its
tiles forever, while `installStatus()` reported `UP_TO_DATE` over stale data —
the failure is silent and permanent. Per-cell edition conflicts are §6.9.

### 6.5 Uninstall

One transaction, three statements — three because deleting the region cascades
`region_charts`, and deleting a chart cascades `chart_tiles`, so each statement
is reading the wreckage of the one before it:

```sql
BEGIN;
DELETE FROM installed_regions WHERE name = ?;
DELETE FROM charts WHERE name NOT IN (SELECT chart_name FROM region_charts);
DELETE FROM tiles WHERE NOT EXISTS (
    SELECT 1 FROM chart_tiles ct
    WHERE ct.zoom_level  = tiles.zoom_level
      AND ct.tile_column = tiles.tile_column
      AND ct.tile_row    = tiles.tile_row);
COMMIT;
PRAGMA incremental_vacuum;
```

Charts and tiles shared with a still-installed neighbour survive, which is the
entire reason for the refcount. `chart_tiles_tile_idx` is what keeps the third
statement a lookup instead of a full scan of the largest table in the database.

Both cascades are inert without `PRAGMA foreign_keys = ON` (§6.6). Without it
this sweep silently deletes one row and leaves everything else behind.

### 6.6 Pragmas and concurrency

Three pragmas, each load-bearing, each with a different lifetime:

```sql
PRAGMA auto_vacuum = INCREMENTAL;  -- MUST run before the first CREATE TABLE
PRAGMA journal_mode = WAL;         -- persistent; set once at creation
PRAGMA foreign_keys = ON;          -- per-connection; set on EVERY connection
```

- **`auto_vacuum = INCREMENTAL`** only takes effect if set before the first table
  is created. Miss it and `incremental_vacuum` frees nothing the user can see;
  retrofitting requires a full `VACUUM` rewrite of the whole file.
- **`foreign_keys`** defaults to *off* and is per-connection, not persistent.
  Every `ON DELETE CASCADE` in §6.2 and §5.1 is inert without it.
- **`journal_mode = WAL`** is persistent, and it is not a tuning knob.
  `MBTilesFileSource` opens its own `sqlite::ReadOnly` connection and holds it
  for the life of the source, so during an install the map is reading the same
  file the installer is writing — a situation the loose-file layout never had.
  Measured on the client against a ~79 MB single-transaction install with a
  reader polling the tile query throughout: **24 of 36 reads failed** in the
  default `delete` journal mode, because the writer takes an EXCLUSIVE lock once
  the page cache spills and holds it through commit. In WAL mode, **0 of 61**
  failed. Without WAL the map visibly breaks every time a region installs.

WAL has a consequence for §6.3: keep installs off one giant transaction. Batch
step 4 by tile count — iterate zoom levels, or window an ordered select — so the
WAL is checkpointed periodically instead of growing to the size of the region.

### 6.7 Boundary tiles

`chart_tiles` tracks *lifetime*, not *content*. Adjacent regions' coverage
polygons may only touch along a line, but the tile grid does not respect polygon
boundaries: any tile crossing a shared border appears in both archives, at every
zoom that covers it, with different blobs. This is the normal state at every
shared border, not an edge case (§7).

After merging, `chart_tiles` holds the union of both regions' claims while the
stored blob holds only one region's render. `rendered_from_region` is what makes
resolving that a pure-SQL test: replace the stored blob when the incoming
chart set for that coordinate is a superset of what the stored blob was rendered
from. No decode, no re-encode. A `NULL` there means provenance was lost to an
earlier uninstall — treat it as replaceable.

Do not build anything more elaborate before running §7.5. If the blobs turn out
byte-identical because the exporter already produces seamless overlap, a content
hash replaces this entire question and `rendered_from_region` can go away.

### 6.8 Install status

`installStatus()` reads the store instead of the client's `regions`
SharedPreferences file: no `installed_regions` row → `NOT_INSTALLED`; row
`created_at` older than the manifest's `RegionManifestEntry.createdAt` →
`UPDATE_AVAILABLE`; otherwise `UP_TO_DATE`.

Compare **parsed** timestamps, never lexically. The client accepts two ISO
patterns (with and without milliseconds), so string ordering is unsafe across
them — and the two values only line up at all if the exporter honors the
`njord:generated_at` contract in §5.1.

This also retires the client's `WORLD` special case. `WORLD` currently reports
`UPDATE_AVAILABLE` permanently, because its tiles ship in the APK and there is
no install record to compare. Seeding its `installed_regions` row at extraction
time from the bundled archive's own `njord:generated_at` gives it a real status
like any other region.

### 6.9 Chart editions across regions

Two installed regions can carry different editions of the same cell if their
archives were exported at different times. The `ON CONFLICT … DO NOTHING` in
§6.3 step 2 keeps whichever edition arrived first, which is not always the right
one, so it needs a companion pass over the charts that already existed: compare
`DSID_UPDN` — the update number the server already uses for its NOAA diff —
preferring the higher, and falling back to `updated` (`DSID_UADT`).

SQLite's JSON1 extension does this in the same attached-database transaction via
`json_extract(dsid_props, '$.DSID_UPDN')`, so it stays a SQL pass rather than a
read-modify-write loop. When the incoming edition wins, replace the `charts` row
and its `chart_tiles` edges wholesale, and let §6.7's superset test decide the
blobs.

### 6.10 Other readers of the tile blobs

MapLibre is not the only consumer. The client's `DepareTileStore` reads
DEPARE/DRGARE/SWPARE depth-area polygons straight out of the tile blobs for
route depth checking, today as `File.exists()` probes and direct `.pbf` reads.
Under this model it needs a SQLite read path plus gunzip, and its
`deepestZoomCovering()` becomes up to 12 zooms × N positions of indexed queries
instead of `stat()` calls.

This is device-side work, but it is called out here because it is the largest
unbudgeted piece of the migration, and because it is the reason the store's
`tiles` table has to be genuinely queryable rather than merely readable by
MapLibre.

## 7. Handling Overlapping Regions

This section covers merging tile *content* at shared borders; §6 covers the
storage lifecycle that surrounds it. Under the merged-store model the
catalog makes the cheap resolution possible: because `chart_tiles` records
which charts contributed to a tile, an incoming boundary tile whose
contributing chart set is a superset of the stored tile's can simply
replace it, with no decode required. §6.7 is the operative description; what
follows is why the alternatives were rejected and what still needs measuring.

Adjacent regions' polygons may only touch along a boundary line, but the
tile grid doesn't respect polygon boundaries — any tile intersecting a
shared border will appear in both regions' MBTiles at every zoom that
covers it. This is the normal state at every shared border, not an edge
case.

Overlapping tiles may be **byte-identical** (if Njord already produces
deliberately overlapping, seamless chart mosaics) or **genuinely
conflicting** (different chart editions, survey dates, or independent
tiling runs) — these require different handling, so tiles need to be
compared, not blindly overwritten.

### 7.1 Alternative considered: keep tiles keyed by region

The obvious alternative to the merged store of §6 is to never merge at all —
key tiles by region and resolve at serve time:

```sql
CREATE TABLE region_tiles (
  region_id   INTEGER,
  zoom_level  INTEGER,
  tile_column INTEGER,
  tile_row    INTEGER,
  tile_data   BLOB,
  PRIMARY KEY (region_id, zoom_level, tile_column, tile_row)
);
```

This avoids forcing a decode/merge decision at import time and makes
uninstall trivial (delete by `region_id`, no refcount needed). It was
rejected because the table is no longer an MBTiles `tiles` table, so the
`mbtiles://` source of §4.2 can't read it — every tile would have to go back
through an on-device tile-serving shim, and the common single-region tile then
pays a serve-time lookup cost forever to save a one-time import cost. The
merged store in §6 keeps the fast path fast and confines the complexity
to install and uninstall, which happen rarely and can take their time.

### 7.2 Rejected with it: serve-time decode and merge

The region-keyed model above needs a serve-time resolution step, and that step
is the real reason it was rejected — it is worth recording what it would have
cost, because it is the work §6.7's superset test avoids:

- **Single region covers the tile** (the common case): return that region's blob
  directly.
- **Multiple regions cover the tile**: decode both blobs back into features,
  dedupe, re-encode into a merged blob, and cache the result keyed by region-set
  + z/x/y so the decode/merge cost is paid once per tile rather than once per
  request.

### 7.3 Dedup and conflict resolution, had it been needed

The dedup step §7.2 would have required, recorded because it is the work
§6.7's superset test avoids:

- Prefer deduping on a stable feature identifier if Njord features carry one
  (S-57/S-101-style object IDs typically do).
- Fall back to a geometry + attribute hash as a dedup key if stable IDs aren't
  guaranteed.
- For features that genuinely differ between regions, prefer the more
  authoritative source available in metadata — newer edition/survey date, or the
  finer native scale for that zoom band.

None of §7.2 or §7.3 is implemented, and none of it should be unless §7.5 shows
the superset test is insufficient. They record the fallback shape, not a plan.

### 7.4 Region removal

Under the rejected §7.1 model, removal is a single delete by `region_id`.
Under the merged store it is the chart-refcounted sweep of §6.5, and the
resolution made at install time (§6.7) is what determines whether a surviving
boundary tile is correct afterwards: a tile replaced by a superset render
survives an uninstall cleanly, while one left holding a departing region's
render is the case `rendered_from_region` exists to identify.

### 7.5 Validation step before building merge logic

Before implementing anything in this section, diff the tiles in a real overlap
zone between two adjacent Njord regions. If they turn out to be byte-identical
in practice — because Njord already produces seamless overlap — then a content
hash at insert time replaces the whole merge question: §7.2 and §7.3 are moot,
and `rendered_from_region` (§6.7) can be dropped from the device schema
entirely.

This is cheap to run against two existing archives and it settles the most
speculative part of the design, so it should happen before the exporter changes
in §5.4 rather than after.

## 8. Summary of the End-to-End Flow

1. Njord's `RegionExporter` generates one file per region version: a sparse
   MBTiles archive (per-feature zoom ranges from chart scale, empty tiles
   omitted) that also carries the region's chart rows and chart→tile index
   in the same SQLite database, plus its `.sha256` (§2.3, §5).
2. User downloads it directly (not via `OfflineManager`) and verifies the
   checksum from the manifest.
3. Install `ATTACH`es the archive and merges it into the device's store —
   which has the same schema plus `installed_regions` and `region_charts` —
   with `INSERT … SELECT`: tile blobs into `tiles`, chart rows deduped on
   `name`, and the `installed_regions → region_charts → charts → chart_tiles`
   chain that records who requires what (§6.2, §6.3). The archive is then
   discarded.
4. The app points one `mbtiles://` vector source at that store and enables
   `setOversampleMissingTiles(true)`. The catalog tables are invisible to
   MapLibre, so no interceptor or tile-serving shim is needed — **pending the
   verification in §6.1**, which is the one open gate on this whole model.
5. Sparse gaps are reported as `NotFound` rather than as valid blank
   tiles, so MapLibre ascends to the nearest real ancestor tile and
   stretches it; detail degrades gracefully instead of vanishing. Where no
   ancestor exists either, the always-dense `WORLD` base-map archive
   underneath keeps the map coherent.
6. The catalog also answers the non-rendering questions offline: which
   charts cover this position, their editions, and their `.TXT` notices —
   and gives `installStatus()` a real answer for `WORLD` for the first time
   (§6.8).
7. Uninstalling a region drops its `region_charts` claims, cascades away
   any chart no other installed region claims, and then deletes exactly
   those tiles no surviving chart requires — followed by an incremental
   vacuum. Charts and tiles shared with an adjacent installed region
   survive (§6.5).
8. Throughout, WAL is mandatory and `foreign_keys` must be on for every
   connection: the map holds a read-only connection open against the same file
   the installer writes, and every cascade the lifecycle depends on is inert
   without the pragma (§6.6).

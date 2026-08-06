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
   itself, and gunzips tile blobs. All of the manual plumbing described in
   §4.3 is handled internally, and the catalog tables sharing the file
   (§5) are never queried.
3. Call `setOversampleMissingTiles(true)` on that source. Without this the
   archive still loads, but every sparse gap renders blank (§3.1).

**Important note:** The downloaded .mbtiles file is not what the source ultimately points at, though:
§6.1 merges each installed region tiles into one directory LocalFileSource store so
tiles can be reference-counted across regions. That store is read by the
same `file://` mechanism described here — the merge changes which file
the URL names, not how it is served.

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
table in the file is invisible to it. So the catalog goes **inside the
`.mbtiles` archive** rather than beside it:

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
CREATE TABLE charts (
    id          INTEGER PRIMARY KEY,   -- server charts.id; see §5.3 on stability
    name        TEXT    NOT NULL UNIQUE, -- DSID_DSNM — the stable identity
    scale       INTEGER NOT NULL,      -- DSPM_CSCL
    file_name   TEXT    NOT NULL,
    updated     TEXT    NOT NULL,      -- DSID_UADT
    issued      TEXT    NOT NULL,      -- DSID_ISDT
    zoom        INTEGER NOT NULL,      -- best display zoom, derived at ingest
    covr        TEXT    NOT NULL,      -- GeoJSON geometry, NOT WKB/WKT
    dsid_props  TEXT    NOT NULL,      -- JSON object, verbatim jsonb::text
    chart_txt   TEXT    NOT NULL,      -- JSON object: { "US5WA22A.TXT": "…" }
    ingested_at TEXT    NOT NULL       -- ISO-8601 UTC
);

-- Every tile this chart contributed content to. The join key is the tile
-- coordinate itself, in the same column names and the same TMS row
-- convention as the `tiles` table alongside it (tile_row = (2^z - 1) - y),
-- so no coordinate flipping is needed anywhere downstream.
CREATE TABLE chart_tiles (
    chart_id INTEGER NOT NULL REFERENCES charts(id) ON DELETE CASCADE,
    z        INTEGER NOT NULL,
    x        INTEGER NOT NULL,
    y        INTEGER NOT NULL,
    PRIMARY KEY (chart_id, z, x, y)
) WITHOUT ROWID;

-- The reverse lookup: "which charts require this tile." This index is what
-- makes the uninstall sweep in §6.3 a lookup rather than a table scan, and
-- it is not optional.
CREATE INDEX chart_tiles_tile_idx
    ON chart_tiles (z, x, y);
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

`chart_tiles` is the largest table by row count — it is the per-chart
expansion of a set `tiles` only stores unioned, so overlapping charts
multiply rows. It is also the narrowest: four small integers, `WITHOUT
ROWID` so the primary key *is* the storage. If it becomes a size problem
for the densest regions, give tiles a surrogate key and reduce the edge to
two columns — but measure first, don't pre-normalize.

### 5.4 Server-side changes

- `RegionDao.findChartsInRegion()` — add `ingested_at::text` to the select.
  `covr` needs no SQL change: the exporter already holds `covrWkb` and can
  produce both the GeoJSON string and the envelope via
  `OgrGeometry.fromWkb4326(covrWkb)` (`geoJson()` / `envelope()`), the same
  helpers `wktToGeojson()` already uses.
- `RegionExporter.compileTileCoordinates()` — currently returns a single
  merged `Set<TileCoord>`. It needs to expose the per-chart breakdown as
  well (either returning `Map<Long, Set<TileCoord>>` and unioning for the
  render, or invoking a callback per chart so edges can stream out).
- `RegionExporter.writeMbtilesArchive()` — this is the only place that
  changes structurally. New order inside the existing `SqliteDb.open(path)`
  block, all before the atomic rename that already exists:
  1. `db.exec` the two existing DDL statements plus `CREATE TABLE charts`
     and `CREATE TABLE chart_tiles`.
  2. `renderAndWriteTiles(...)` as today.
  3. Insert `charts` rows and stream `chart_tiles` edges.
  4. Prune orphaned edges (§5.2), then create the two indices.
  5. `writeMetadata(...)`, extended with the `njord:*` rows.
- Nothing else moves. `pruneOldArchives()`, `writeChecksumSidecar()`,
  `archivesForRegion()`, `buildManifest()` and `RegionManifestEntry` are
  all untouched, because the number of files per region version is
  unchanged — which is the main reason to put the catalog inside the
  archive rather than beside it.

## 6. On-Device Storage and Region Uninstall

### 6.1 Install and uninstall

§4.2 pointed a source straight at a downloaded `region.mbtiles` file. That works
for a single region, but we need all downloaded regions chart tiles to be accessible.

The resolution is that downloaded region tiles are extraced to a directory. and the `catalog` tables 
(`charts`, `chart_tiles`, `chart_tiles_tile_idx1`) are merged into the mobile apps central `catalog` sglite database. 
The downloaded `region.mtiles` files are then discarded.

Installation of a single region:

All tiles are extracted from the `region.mbtiles` into the mobile device's tile directory. Tiles are overwritten
only when the region being installed's date is GTEQ the latest chart table ingested_at claiming the existing tile.

Update of a single region:

When a region is updated all the charts in the catalog for that region are deleted first and their corresponding tiles. 
However, tiles are only removed from the filesystem if no other charts claim the tile. The installation process above follow. 

### 6.5 Chart editions across regions

Two installed regions can carry different editions of the same cell if
their archives were exported at different times. The `INSERT OR IGNORE` in
§6.3 step 2 keeps whichever edition arrived first, which is not always the
right one, so it needs a companion pass over the charts that already
existed: compare `dsid_props->>'DSID_UPDN'` (the update number the server
already uses for its NOAA diff), preferring the higher one and falling back
to `updated` (`DSID_UADT`).

SQLite's JSON1 extension can do the comparison in the same attached-database
transaction — `json_extract(sc.dsid_props, '$.DSID_UPDN')` — so this stays a
SQL pass rather than a read-modify-write loop. When the incoming edition
wins, replace the `charts` row and its `chart_tiles` edges wholesale; the
older region's tiles for that cell remain and get the same `stale`
treatment as §6.4.

## 7. Handling Overlapping Regions

This section covers merging tile *content* at shared borders; §6 covers the
storage lifecycle that surrounds it. Under the merged-store model the
catalog makes the cheap resolution possible: because `chart_tiles` records
which charts contributed to a tile, an incoming boundary tile whose
contributing chart set is a superset of the stored tile's can simply
replace it, with no decode required.

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

The obvious alternative to §6.2 is to never merge at all — key tiles by
region and resolve at serve time:

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
`mbtiles://` source of §4.2 can't read it — every tile then has to go back
through the OkHttp interceptor of §4.3, and the common single-region tile
pays serve-time lookup cost forever to save one-time import cost. The
merged store in §6.2 keeps the fast path fast and confines the complexity
to install/uninstall, which happen rarely and can take their time.

The subsections below apply to whichever model is chosen; under §6.2 they
describe what happens at install time (step 4), not at serve time.

### 7.2 Serve-time resolution

- **Single region covers the tile** (the common case): return that
  region's blob directly — same cost as the non-overlapping lookup path.
- **Multiple regions cover the tile**: decode both blobs back into
  features, dedupe, re-encode into a merged blob, and cache the result
  (keyed by the set of region IDs + z/x/y) so the decode/merge cost is
  paid once per tile, not once per request.

### 7.3 Dedup and conflict resolution

- Prefer deduping on a stable feature identifier if Njord features carry
  one (S-57/S-101-style object IDs typically do).
- Fall back to a geometry + attribute hash as a dedup key if stable IDs
  aren't guaranteed.
- For features that genuinely differ between regions, prefer the more
  authoritative source available in metadata — newer edition/survey date,
  or the finer native scale for that zoom band.

### 7.4 Region removal

Under the rejected §7.1 model, removal is a single delete by `region_id`.
Under the merged store it is the chart-refcounted sweep in §6.3, and the
merge decision made at install time is what determines whether a surviving
boundary tile needs the `stale` treatment of §6.4 — a tile merged from both
regions' content, or replaced by a superset render, is the case that
survives an uninstall cleanly.

### 7.5 Validation step before building merge logic

Before implementing the decode/merge path, diff the tiles in a real
overlap zone between two adjacent Njord regions. If they turn out to be
byte-identical in practice (because Njord already produces seamless
overlap), the merge step can be replaced with a much cheaper
content-hash dedup at insert time, and full decode/merge logic can be
deferred or skipped.

## 8. Summary of the End-to-End Flow

1. Njord's `RegionExporter` generates one file per region version: a sparse
   MBTiles archive (per-feature zoom ranges from chart scale, empty tiles
   omitted) that also carries the region's chart rows and chart→tile index
   in the same SQLite database, plus its `.sha256` (§2.3, §5).
2. User downloads it directly (not via `OfflineManager`) and verifies the
   checksum from the manifest.
3. Install `ATTACH`es the archive and merges it into the device's store —
   which has the same schema — with `INSERT … SELECT`: tile blobs into
   `tiles`, chart rows deduped on `name`, and the
   `regions → region_charts → charts → chart_tiles` chain that records who
   requires what (§6.2, §6.3).
4. The app points one `mbtiles://` vector source at that store and enables
   `setOversampleMissingTiles(true)`. The catalog tables are invisible to
   MapLibre, so no interceptor or tile-serving shim is needed.
5. Sparse gaps are reported as `NotFound` rather than as valid blank
   tiles, so MapLibre ascends to the nearest real ancestor tile and
   stretches it; detail degrades gracefully instead of vanishing. Where no
   ancestor exists either, the always-dense `WORLD` base-map archive
   underneath keeps the map coherent.
6. The catalog also answers the non-rendering questions offline: which
   charts cover this position, their editions, and their `.TXT` notices.
7. Uninstalling a region drops its `region_charts` claims, cascades away
   any chart no other installed region claims, and then deletes exactly
   those tiles no surviving chart requires — followed by an incremental
   vacuum. Charts and tiles shared with an adjacent installed region
   survive (§6.3).

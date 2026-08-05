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

## 3. MapLibre Native Fork: Oversampling Sparse Tile Sets

A sparse tile set only works if the renderer can tell the difference
between *"this tile is present and legitimately empty"* and *"this tile
was never generated."* Stock MapLibre Native cannot, which breaks the
strategy above at exactly the point it matters. Our fork fixes this:

**[manimaul/maplibre-native @ `vector_source_oversample`](https://github.com/manimaul/maplibre-native/tree/vector_source_oversample)**
(commit `d83c308`, branched from upstream `main`).

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
   §4.3 is handled internally.
3. Call `setOversampleMissingTiles(true)` on that source. Without this the
   archive still loads, but every sparse gap renders blank (§3.1).

### 4.3 Fallback: OkHttp interceptor over an imported tile store

If tiles must instead be imported into the app's own database (e.g. to
merge overlapping regions as in §5, or to keep one store across many
archives), serve them to MapLibre via a custom `okhttp3.Interceptor`
registered through
`org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient()`.
For a tile request matching the style's tile URL template:

- Parse `z/x/y` and convert to TMS row order with
  `tile_row = (2^z - 1) - y`.
- If found, return its bytes as a `200 OK` response with the appropriate
  content type.
- If not found, return `404`/`204` so the fork's `NotFound` path engages
  and the renderer substitutes an ancestor tile; returning an empty `200`
  body reproduces the upstream "valid blank tile" bug. Set `Cache-Control`
  either way so MapLibre caches the result instead of re-querying on every
  pan/zoom.
- If the tile isn't covered by any downloaded region at all, fall back to
  `chain.proceed()` for a live network fetch.

### 4.4 How gaps render

- Where a coarser ancestor tile exists in the archive, the gap is filled by
  stretching it — detail degrades gracefully instead of vanishing.
- Where nothing coarser exists either, nothing is drawn for that
  source-layer, which reads as "no chart data here" rather than a
  rendering failure — provided the `WORLD` base-map archive (§2.2) is
  layered underneath at all zooms.

## 5. Handling Overlapping Regions

This section applies to the imported-tile-store model (§4.3). Under the
preferred model (§4.2) each region is simply its own MapLibre source and
layer set, so overlapping boundary tiles overdraw each other rather than
needing to be merged — worth confirming visually at a shared border before
taking on any of the machinery below.

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

### 5.1 Storage model: per-region, not merged-on-import

Keep imported tiles keyed by region rather than collapsing everything
into one shared `(z, x, y) → blob` table. This avoids forcing a
decode/merge decision on every tile at import time, when the vast
majority of tiles belong to exactly one region.

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

### 5.2 Serve-time resolution

- **Single region covers the tile** (the common case): return that
  region's blob directly — same cost as the non-overlapping lookup path.
- **Multiple regions cover the tile**: decode both blobs back into
  features, dedupe, re-encode into a merged blob, and cache the result
  (keyed by the set of region IDs + z/x/y) so the decode/merge cost is
  paid once per tile, not once per request.

### 5.3 Dedup and conflict resolution

- Prefer deduping on a stable feature identifier if Njord features carry
  one (S-57/S-101-style object IDs typically do).
- Fall back to a geometry + attribute hash as a dedup key if stable IDs
  aren't guaranteed.
- For features that genuinely differ between regions, prefer the more
  authoritative source available in metadata — newer edition/survey date,
  or the finer native scale for that zoom band.

### 5.4 Region removal

Because canonical per-region data stays intact under this model, deleting
a region is simple: drop its rows and invalidate any merged-cache entries
that referenced it. The remaining region's tiles continue to serve
directly with no recomputation needed for untouched interior tiles.

### 5.5 Validation step before building merge logic

Before implementing the decode/merge path, diff the tiles in a real
overlap zone between two adjacent Njord regions. If they turn out to be
byte-identical in practice (because Njord already produces seamless
overlap), the merge step can be replaced with a much cheaper
content-hash dedup at insert time, and full decode/merge logic can be
deferred or skipped.

## 6. Summary of the End-to-End Flow

1. Njord's `RegionExporter` generates a sparse MBTiles file per region,
   with per-feature zoom ranges derived from chart scale and empty tiles
   omitted entirely.
2. User downloads the region's MBTiles file directly (not via
   `OfflineManager`), verifying the `.sha256` sidecar.
3. The app adds it as an `mbtiles://` vector source and enables
   `setOversampleMissingTiles(true)` — no import or tile-serving shim
   needed, given the `vector_source_oversample` fork.
4. Sparse gaps are reported as `NotFound` rather than as valid blank
   tiles, so MapLibre ascends to the nearest real ancestor tile and
   stretches it; detail degrades gracefully instead of vanishing.
5. Where no ancestor exists either, the always-dense `WORLD` base-map
   archive underneath keeps the map coherent.
6. Only if tiles are instead merged into a single on-device store (§4.3,
   §5) do the interceptor and boundary-merge paths come into play.

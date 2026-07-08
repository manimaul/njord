# Offline Marine Chart Tiles: Strategy Document

## 1. Problem

An app displays marine charts from the Njord server using MapLibre Native
(Android). We need offline support for large coastal regions (e.g. the
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
not a per-request operation:

- Preferred: Njord generates the sparse MBTiles server-side (e.g. via
  tippecanoe or tegola) from the chart/feature source data, and ships the
  `.mbtiles` file as the downloadable artifact. This moves all clipping,
  simplification, and topology repair off the device, and guarantees
  every client gets the same validated tile set.
- Alternative: perform the same process on-device from the existing
  Njord SQLite export (pre-simplify geometry into zoom tiers, clip with
  JTS, encode with a vector tile library), writing the result to a local
  MBTiles file at import time instead of serving tiles synthesized on
  demand. This trades server effort for device CPU time during import.

Either approach avoids the tile-explosion problem because the expensive
work happens once per region version, not once per tile per device.

## 3. Serving Tiles to MapLibre

### 3.1 Do not use MapLibre's OfflineManager region download

`OfflineManager` enumerates and requests every `(z, x, y)` in a bbox ×
zoom range regardless of whether data exists there, which reintroduces
the exact problem being avoided. Instead:

1. Download the region's MBTiles file as a plain file transfer.
2. Import (or reference) its tile rows in an on-device store.
3. Serve tiles to MapLibre via a custom `okhttp3.Interceptor` registered
   through `org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient()`.

### 3.2 Interceptor behavior

For a tile request matching the style's tile URL template:

- Parse `z/x/y`. Note MBTiles rows are stored in TMS row order — convert
  with `tile_row = (2^z - 1) - y` if the source uses standard XYZ.
- Look up the tile in the on-device store.
- If found, return its bytes as a `200 OK` response with the appropriate
  content type.
- If not found (no charted data at this location/zoom), return an empty
  body with `200`/`204` rather than an error, and set `Cache-Control` so
  MapLibre caches the "nothing here" result instead of re-querying on
  every pan/zoom.
- If the tile isn't covered by any downloaded region at all, fall back to
  `chain.proceed()` for a live network fetch.

### 3.3 How gaps render

A missing vector tile is not an error to MapLibre — it simply means no
features are drawn for that tile/source-layer:

- Sparse high-zoom gaps read as "no detailed chart data here," not as a
  rendering failure, as long as a persistent base layer (ocean fill,
  coarse always-available coastline/land layer) sits underneath at all
  zooms.
- Setting the vector source's `maxzoom` in the style lets MapLibre
  overzoom (stretch) the last available tile instead of requesting
  nonexistent higher-zoom tiles, so detail degrades gracefully past its
  native resolution rather than vanishing abruptly.

## 4. Handling Overlapping Regions

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

### 4.1 Storage model: per-region, not merged-on-import

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

### 4.2 Serve-time resolution

- **Single region covers the tile** (the common case): return that
  region's blob directly — same cost as the non-overlapping lookup path.
- **Multiple regions cover the tile**: decode both blobs back into
  features, dedupe, re-encode into a merged blob, and cache the result
  (keyed by the set of region IDs + z/x/y) so the decode/merge cost is
  paid once per tile, not once per request.

### 4.3 Dedup and conflict resolution

- Prefer deduping on a stable feature identifier if Njord features carry
  one (S-57/S-101-style object IDs typically do).
- Fall back to a geometry + attribute hash as a dedup key if stable IDs
  aren't guaranteed.
- For features that genuinely differ between regions, prefer the more
  authoritative source available in metadata — newer edition/survey date,
  or the finer native scale for that zoom band.

### 4.4 Region removal

Because canonical per-region data stays intact under this model, deleting
a region is simple: drop its rows and invalidate any merged-cache entries
that referenced it. The remaining region's tiles continue to serve
directly with no recomputation needed for untouched interior tiles.

### 4.5 Validation step before building merge logic

Before implementing the decode/merge path, diff the tiles in a real
overlap zone between two adjacent Njord regions. If they turn out to be
byte-identical in practice (because Njord already produces seamless
overlap), the merge step can be replaced with a much cheaper
content-hash dedup at insert time, and full decode/merge logic can be
deferred or skipped.

## 5. Summary of the End-to-End Flow

1. Njord (or on-device import) generates a sparse MBTiles file per region,
   with per-feature zoom ranges derived from chart scale.
2. User downloads the region's MBTiles file directly (not via
   `OfflineManager`).
3. Tile rows are imported into a per-region on-device table.
4. An OkHttp interceptor serves tiles from the local store when covered,
   falling back to network otherwise.
5. Boundary tiles covered by more than one region are merged and cached
   on first request; all other tiles serve directly with no processing.
6. Missing tiles render as empty rather than errors, backed by a
   persistent base layer and `maxzoom` overzoom for graceful degradation.

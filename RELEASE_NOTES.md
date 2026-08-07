# Release Notes

## 1.3

### Offline Regions: MBTiles Archives

Region export was rebuilt around **MBTiles**. A region is now a single `.mbtiles` file holding
pre-rendered vector tiles rather than a `.sqlite` file of raw features, so mobile clients render
from the same `TileEncoder` output the live server serves instead of re-deriving geometry on
device. The old `chart` / `feature` / `lnam_refs` / `feature_bbox` tables are gone.

- **Sparse tile pyramids** — candidate tiles are compiled per feature from its `[MINZ, MAXZ]` range
  capped at its chart's compiled-scale zoom, so overview charts yield shallow pyramids and detailed
  charts deeper ones. Tiles the encoder finds empty are never written.
- **Chart catalog inside the archive** — `charts` and `chart_tiles` tables ride along in the same
  file (invisible to MapLibre, which only reads `metadata` and `tiles`), giving devices the per-chart
  → tile edges needed to uninstall one chart without disturbing tiles another still needs.
- **World base map region** — a charts-free `WORLD` archive of Natural Earth base map tiles (z0–6)
  for areas with no ENC coverage.
- **Region manifest** — `GET /v1/regions` returns every configured region with coverage geometry,
  a label point, archive size, sha256, and creation time; entries appear before first export with
  null archive fields.
- **Resumable downloads** — region archive downloads support HTTP `Range` requests.
- **Automatic rebuilds** — regions are marked stale via a DB-assigned `ingested_at` rather than the
  S-57 edition date, so a re-ingested chart is always detected. Deleting a chart now clears the
  export state of every region containing it.
- **Region editor** — the web UI can draw and redefine region coverage areas directly on the map.

### Catalog-Driven NOAA Updates (`enc_cron`)

A new Kotlin/Native module replaces the one-line `curl OneDay_ENCs.zip` cron pod, which could only
ever see a 24-hour window and wrote partial zips straight into the ingest directory.

`enc_cron` streams NOAA's 52 MB ISO-19115 product catalog (7,229 cells) through a new `libexpat`
SAX binding, diffs it against `GET /v1/chart_editions`, and downloads only cells whose revision
actually changed — bundling them into complete zips before handing them to the ingester.

- **New `/v1/chart_editions` endpoint** — every chart's revision key as `"<UPDN>:<UADT>:<ISDT>"`,
  keyed by chart name. `DSID_EDTN` is deliberately excluded: GDAL overwrites it from the last
  applied update file, and NOAA publishes `0` for some cells, which would re-download them forever.
- **Withdrawn charts** — cells NOAA no longer publishes are reported and deleted.
- **`ZipWriter`** — added to `libzip` for bundling fetched cells.

### Database

- **Versioned migrations** — `DbMigrations` now applies numbered revisions in order under a
  distributed lock, stamping each into `meta_data` so a failure resumes rather than replaying.
  Migrations can carry a Kotlin action for backfills that don't fit in one transaction.
- **Connection pooling** — `PgDataSource` previously opened a fresh TCP socket via `PQconnectdb` for
  *every* query. With region export issuing one query per chart plus two per candidate tile, that
  meant thousands of connect/disconnect cycles per export. Replaced with a bounded pool (10
  connections) with health-checked checkout.
- **Index audit** — dropped redundant `charts_idx` / `features_idx` (already covered by each primary
  key), replaced `features_layer_idx` with `(layer, id)` to serve the layer page's keyset pagination,
  and added an expression index on `props->>'LNAM'`.

### Rendering

- **Decluttered soundings** — depth labels no longer overlap: collision is enabled with a sort key on
  `METERS`, so shallower (more safety-critical) soundings win when labels collide.
- **Scale-derived zoom** — chart display zoom is computed from `DSPM_CSCL` and center latitude.
- **PLY excluded at z10+** — chart coverage boundaries stop drawing once you're zoomed in past them.
- **`promoteId` on TileJSON** — feature state now works against real feature ids in MapLibre.

### Breaking Changes

- **`charts.name` is now the primary key** — the surrogate `charts.id` is gone, and
  `features.chart_id` is now `features.chart_name`. Applied automatically by DB migration 3, which
  backfills existing rows in place; on a large database expect a long startup, and run
  `VACUUM FULL features;` afterwards to reclaim the bloat.
- **Charts are addressed by name over HTTP** — `/v1/chart` and `/v1/chart_catalog` no longer accept
  an `id` parameter (use `name`), and `/v1/geojson` takes `chart_name` in place of `chart_id`.
- **`ChartCatalog.nextId` is now `nextName`** — catalog paging uses the chart name as its cursor.
  `ChartItem` no longer carries an `id`.
- **Region archives changed format** — `.sqlite` → `.mbtiles` with a new internal schema. Clients
  built against the 1.2 archive layout must be updated; existing archives should be regenerated.

`/v1/chart_editions` is unchanged; `enc_cron` already keyed on chart names.

### Bug Fixes

- **TOPMAR associations were silently empty** — no insert ever populated `features.lnam_refs`, so the
  GIN index backing topmark association lookups was empty and every lookup returned nothing. Fixed at
  insert, with a batched backfill for existing rows (migration 2).
- **Connection exhaustion during region export** — `Dao` retried from *inside* `conn.use { }`, leaving
  failed connections open and stacking up to 7 deep; combined with the lack of pooling this could blow
  past `FD_SETSIZE`. Retries now close the connection before the next attempt.
- **Stuck migration lock** — a crashed instance could leave the distributed migration lock held,
  blocking every subsequent startup.
- **Broken frontend routing** — fixed UI errors from the route matcher and debug tile rendering.

### Web UI

- **Reusable router** — routes are now declared once in a `NjordRoute` registry with a shared matcher
  and typed path parameters, replacing per-page ad-hoc parsing.

### Operations

- **Nuke endpoint** — signature-guarded `DELETE /v1/nuke` truncates all charts, features, and base
  features, and clears rendered region archives.

### Build & Dependencies

- Dependency versions moved to a Gradle version catalog (`gradle/libs.versions.toml`).
- Kotlin 2.3.20 → **2.4.10**, Ktor 3.4.0 → **3.5.1**, Compose 1.10.0 → **1.11.1**,
  kotlinx.serialization 1.10.0 → **1.11.0**.
- New modules: `enc_cron`, `libexpat`.

### Documentation

- `docs/NOAA_ENC_UPDATE_CRON.md` — catalog-driven update design and measurements.
- `docs/CHART_ACCESS_CONTROL.md` — plan for gating restricted chart data.
- `docs/region_render_opt.md` — region render optimization notes.

---

## 1.2

### New Features

- **Light sectors** — Lights now render sector geometry showing the angular coverage and color of each sector
- **Region export for mobile** — Post-ingestion export of SQLite archives per geographic region for offline chart rendering on mobile clients (`libsqlite` module)
- **Feature bounding box export** — SQLite region exports include per-feature bounding boxes to support spatial indexing on mobile
- **ENC URL ingestion** — New endpoint (`EncUrlHandler`) to trigger ingestion directly from a remote ENC URL
- **Auto external URL detection** — Server automatically detects its external URL, removing the need to configure it manually
- **NOAA daily update cron** — Kubernetes cron job for automated daily NOAA ENC ingestion

### New S-57 Layers

40+ new object class layers including: ADMARE, ACHPNT, CONZNE, COSARE, CUSZNE, DRGARE, DRYDOC, EXEZNE, FSHZNE, GRIDRN, HRBARE, HRBFAC, LNDELV, LOCMAG, MAGVAR, MCOVR, MCSCL, MNPUB, MNSYS, MQUAL, MSDAT, MSREL, MVDAT, PIPOHD, PIPSOL, RADSTA, RAILWY, RAPIDS, RDOSTA, ROADWY, RSCSTA, SBDARE, SLOTOP, SMCFAC, SPRING, TESARE, TIDEWY, TSFEB, UWTROC, VEGATN, WATFAL, WATTUR, WRECKS

### Bug Fixes

- Fixed ingestion memory leak
- Fixed TSEZNE transparency (semi-transparent fill per S-52 `AC(TRFCF,3)`)
- Fixed light sector colors for day/dusk/night display modes
- Fixed daily cron job scheduling
- Fixed ingestion to process one zip file at a time (prevents concurrent ingest conflicts)

### Packaging & Deployment

- Debian packages for Linux x64, ARM64, and Raspberry Pi
- macOS development documentation and build support

---

## 1.1

### Performance Improvements

- **Tile caching** — MVT tiles are now cached, eliminating redundant PostGIS queries for repeated tile requests
- **Batch DB queries** — Replaced N+1 query pattern with a single batched `ANY($2)` query
- **Composite index** — Replaced `INT4RANGE` z_range column with `z_min`/`z_max` integer columns and a composite B-tree index for 5–20x faster feature lookups
- **`ST_ClipByBox2D`** — Replaced `ST_Intersection` with the faster clip function for tile envelope clipping
- **HashMap layer dispatch** — O(1) layer lookup replaces O(n) linear scan over 100+ layer instances
- **PGBouncer** — Connection pooling support

### ⚠️ Breaking Change: Database Schema

Version 1.1 introduces an incompatible database schema change. Adopters upgrading from 1.0 must start with a **fresh database** — existing data cannot be migrated. Re-ingest your S-57 chart files after upgrading.

---

## 1.0

Initial release.

- S-57 layer coverage: 100+ object classes implemented (navigation aids, buoys, beacons, depths, lights, traffic separation zones, infrastructure, coastal features, and more)
- MVT tile serving backed by PostGIS
- Web UI with MapLibre GL frontend

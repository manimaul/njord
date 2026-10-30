# Rust Port — Phase 0 Checklist

_Created 2026-10-29. Tracks [Phase 0 — Characterization](RUST_PORT_PLAN.md#phase-0--characterization-kotlin-repo) of [RUST_PORT_PLAN.md](RUST_PORT_PLAN.md). Fixture IDs (P-SY-1, …) are defined in §5 of the plan; defects D1–D9 and audit IDs (SY-3, TE-1, …) are in [TEST_COVERAGE_AUDIT.md](TEST_COVERAGE_AUDIT.md)._

All work happens in the Kotlin repo. Rule: **no fixture is frozen until the defects it touches are resolved** (§8, "Fixtures freeze bugs as behavior").

## 1. Groundwork

- [ ] Create `testdata/` at the repo root with the layout from §5 (`symbology/`, `geo/`, `s57/`, `db/`, `ws/`, `regions/`, `admin/`, `security/`, `enc_cron/`, `fixtures/`)
- [ ] Agree fixture conventions: `{ "input": …, "expected": … }` case lists; binary outputs as `.bin` + decoded `.json`
- [ ] Every fixture file carries a header (or sidecar) naming the decision it pins
- [ ] Shared Kotlin test helper for reading/writing fixtures (regenerate mode vs. verify mode)
- [ ] Copy `US5WA22M` from `libgdal/src/nativeTest/resources` to `testdata/fixtures/US5WA22M/`
- [ ] Build two tiny synthetic cells with overlapping coverage at different scales (needed by P-TE-5 / TE-1)
- [ ] Build or obtain a cell with `.001`–`.003` updates (needed by P-IN-4)
- [ ] Trim a real NOAA catalog to `enc_cron/catalog_sample.xml` incl. the `dataQualityInfo` decoy and a record missing fields (P-EC-1)

## 2. Defect decisions (D1–D9)

Fix or formally accept each one; record the decision in `TEST_COVERAGE_AUDIT.md`.

- [x] **D1** `displayDepths` tenths truncation — fixed 2026-10-27, pinned by `SoundingsTest` / `SoundgTest`
- [ ] **D2** `EncSaveHandler` `filename` path traversal (SEC-3)
- [ ] **D3** `ZipFile.unzipToPath` zip-slip, server ingest and `enc_cron` `extractCell` (SEC-4)
- [ ] **D4** `verifySignature` throws on malformed input → decide throw vs `false`; constant-time compare (SEC-1)
- [ ] **D5** `sqlOpAsync` retry double-counts features; `ChartDao.insertAsync(overwrite=true)` delete + insert outside a transaction (IN-4, DB-2)
- [ ] **D6** `DistributedLock` check-then-rename race; may a non-owner clear? does an upload abort an in-flight ingest? (CC-1, CC-2)
- [ ] **D7** missing `DSID_DSNM` → chart named `""`; `MultiPolygon` `M_COVR` dropped (IN-2)
- [ ] **D8** `TileHandler` `z/x/y` not range-checked, unbounded cache growth (API-3)
- [ ] **D9** `EncUrlHandler` saves non-2xx bodies as `.zip` (API-6)

## 3. Fixtures and tests (§5)

### 3.1 Symbology — highest value

- [ ] **P-SY-1** `symbology/pre_tile_encode/<OBJL>.json` for every `Layerable` with `preTileEncode`
- [ ] **P-SY-2** generator: `preTileEncode` over every real feature in `US5WA22M`, grouped by layer
- [ ] **P-SY-3** `symbology/depth_bands.json` incl. non-default `ChartsConfig`
- [ ] **P-SY-4** `symbology/display_depths.json` 0.0…31.0 in 0.1 steps + drying heights (generate from the fixed D1 code)
- [ ] **P-SY-5** `symbology/light_description.json` — PresLib examples + every LIGHTS feature in the cell
- [ ] **P-SY-6** `symbology/attribute_helpers.json` — every `attributehelpers/*` decode, 0…max+1
- [ ] **P-SY-7** `symbology/style/<depth>_<theme>.json` — full style JSON for every `Depth × ThemeMode × custom theme`, fixed `baseUrl`
- [ ] **P-SY-8** `symbology/sprites.json` (lift from `SpriteSheetTest`)
- [ ] **P-SY-9** `symbology/svg_sector.json`

### 3.2 Tile math and MVT

- [ ] **P-TE-1** `geo/tile_system.json` incl. poles, antimeridian, z0, z24
- [ ] **P-TE-2** `geo/mvt/<case>.bin` + `.json` — points, lines, polygons, holes, every prop type, buffer clipping, winding
- [ ] **P-TE-3** `geo/filtered_props.json`
- [ ] **P-TE-4** `geo/base_map_scale.json`
- [ ] **P-TE-5** golden tiles: ~40 decoded tiles z4–z16 over the cell, the synthetic overlap, and base-map-only tiles — **the most important fixture**

### 3.3 S-57 ingest

- [ ] **P-IN-1** `s57/US5WA22M/chart_insert.json`
- [ ] **P-IN-2** `s57/US5WA22M/features/<LAYER>.json` — props + WKT after `makeValid`
- [ ] **P-IN-3** `s57/layer_counts.json`
- [ ] **P-IN-4** `s57/update_cell/` — `DSID_UPDN`/`EDTN` and changed features after updates
- [ ] **P-IN-5** `s57/US5WA22M/db_state.json` — `charts` row + per-layer aggregates after ingest
- [ ] **P-IN-6** `s57/natural_earth.json` — `normalizeLayerName`, `scaleFromFilename`, NE sample row counts
- [ ] **P-IN-7** `ws/messages.json` — every `WsMsg` subtype

### 3.4 Database contract

- [ ] **P-DB-1** `DbMigrations`: empty → current, v1 → migrated, schema dumps identical; `meta_data.version` handshake
- [ ] **P-DB-2** chart overwrite, delete cascade, unknown name → false
- [ ] **P-DB-3** `db/feature_queries.json` — feature query row shape and clipping
- [ ] **P-DB-4** TOPMAR association queries
- [ ] **P-DB-5** catalog paging / editions key — move existing expectations into shared fixtures
- [ ] **P-DB-6** region flags (`regionNeedsRebuild`, `markRegionExported`, `clearRegionExportState`, `regionsContainingChart`)
- [ ] **P-DB-7** `db/retry.json` — `sqlOpAsync` retry/backoff spec table

### 3.5 HTTP conformance suite (`conformance/`)

- [x] Decide the implementation: **Python / pytest** (validated 2026-10-30; see §5.5 of the plan for why not Rust or `hurl`)
- [ ] Scaffold `conformance/` with `requirements.txt` (pinned `pytest`, `requests`, `websockets`, `mapbox-vector-tile`; Python ≥ 3.12) and a `conftest.py` taking `--base-url`, `--compare-url`, `--record`, `--golden-dir`
- [ ] `lib/client.py`: admin login via `GET /v1/admin` basic auth, signed GET/POST/DELETE, multipart upload (lift from `data/upload_noaa_region_encs.py`)
- [ ] `lib/ws.py`: `/v1/ws/enc_process` client on `websockets.sync`, returns the ordered `WsMsg` list
- [ ] `lib/tiles.py`: gunzip, `mapbox_vector_tile.decode`, canonical sort by (layer, id/`LNAM`, props); sha256 of raw bytes recorded but not asserted
- [ ] `lib/mask.py`: volatile-field masking by JSON pointer (timestamps, uuids, durations) and base-URL → `{BASE}` normalisation; masking list committed next to each golden file
- [ ] Record / verify / diff modes wired up; `golden/<route>.json` written by record mode against the Kotlin server
- [ ] Suite imports nothing from `server/`, `shared/` or (later) `crates/`
- [ ] **P-API-1** `about/version`, `s57objects`, `s57attributes`
- [ ] **P-API-2** `style/{depth}/{theme}[?c=]` — equal to P-SY-7 after normalising `baseUrl`
- [ ] **P-API-3** `tile_json`, `tile/{z}/{x}/{y}`, `?info=true`
- [ ] **P-API-4** `chart`, `chart_catalog`, `chart_editions`, `feature/layer`, `feature/lnam`, `geojson` GET
- [ ] **P-API-5** `content/…`, `icon/{name}`, `sector_svg/…` incl. traversal refusal
- [ ] **P-API-6** `admin` basic auth, `admin/verify`
- [ ] **P-API-7** every mutating route × {no signature, bad signature} → 401
- [ ] **P-API-8** `enc_save` → ingest → `ws/enc_process` sequence → `DELETE enc_save`
- [ ] **P-API-9** `chart` POST/DELETE, `geojson` POST, `cache` GET/DELETE, `nuke`
- [ ] **P-API-10** `regions` GET/POST, `regions/{archive}` with/without `Range` (206/416)
- [ ] **P-API-11** `X-Forwarded-*` → `externalBaseUrl` in style URLs and signatures
- [ ] **P-API-12** 405 / 404 / CORS

### 3.6 Region export

- [ ] **P-RE-1** `regions/schema.sql`
- [ ] **P-RE-2** `regions/metadata.json`
- [ ] **P-RE-3** archive from the fixture cell: tile coordinate set, `charts` row, pruned `chart_tiles`, decoded tiles vs P-TE-5
- [ ] **P-RE-4** `regions/timestamps.json` incl. DST hour, archive name parsing, prune ordering
- [ ] **P-RE-5** `regions/manifest.json` with/without archives, sidecar reuse

### 3.7 Admin signatures and security

- [ ] **P-SEC-1** `admin/hmac_vectors.json` — RFC 4231 + `createSignature` with fixed inputs
- [ ] **P-SEC-2** `admin/verify_cases.json` (needs D4)
- [ ] **P-SEC-3** `security/paths.json` — `resourceName()`, archive names, upload filenames (D2), zip entries (D3)

### 3.8 `enc_cron`

- [ ] **P-EC-1** `enc_cron/catalog_sample.xml` + `parsed.json`
- [ ] **P-EC-2** `enc_cron/selection.json` — lift from existing tests
- [ ] **P-EC-3** `enc_cron/bundle_layout.json`
- [ ] **P-EC-4** `enc_cron/config.json`
- [ ] **P-EC-5** `deleteOrphans` guard-ordering decision table

### 3.9 Concurrency primitives (behavioral, not golden)

- [ ] **P-CC-1** lock file format and acquire/clear semantics (needs D6)
- [ ] **P-CC-2** `IngestStatus` file: atomic write, JSON `WsMsg`, written only while holding the lock
- [ ] **P-CC-3** worker cadence: ingest poll 5 s; region worker 5 s / idle 15 min / `wake()`

### 3.10 Frontend (§7)

- [ ] Route list and query-param behavior pinned as a fixture (extend `RouteMatcherTest`)
- [ ] Screenshot checklist per page (home/map, catalog, chart info, layer, symbols, installer/admin, about)

## 4. CI

- [ ] CI workflow with a PostGIS service container running `./gradlew allTests`
- [ ] CI fails if regenerating fixtures produces a diff against committed `testdata/`
- [ ] Conformance suite job: `actions/setup-python` (3.12) + `pip install -r conformance/requirements.txt` + `pytest conformance --base-url=http://localhost:9000` against a Kotlin server started in CI

## 5. Exit criteria

- [ ] All D1–D9 resolved, each decision recorded
- [ ] `testdata/` committed and validated in CI
- [ ] Conformance suite green in verify mode against the Kotlin server locally
- [ ] Conformance suite green in verify mode against `https://openenc.com` (read-only routes)
- [ ] Diff mode exercised once: local Kotlin vs local Kotlin on a second port must report zero differences (proves the masking list before Rust exists)

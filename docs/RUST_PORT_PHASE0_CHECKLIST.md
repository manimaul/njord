# Rust Port — Phase 0 Checklist

_Created 2026-10-29. Tracks [Phase 0 — Characterization](RUST_PORT_PLAN.md#phase-0--characterization-kotlin-repo) of [RUST_PORT_PLAN.md](RUST_PORT_PLAN.md). Fixture IDs (P-SY-1, …) are defined in §5 of the plan; defects D1–D9 and audit IDs (SY-3, TE-1, …) are in [TEST_COVERAGE_AUDIT.md](TEST_COVERAGE_AUDIT.md)._

All work happens in the Kotlin repo. Rule: **no fixture is frozen until the defects it touches are resolved** (§8, "Fixtures freeze bugs as behavior").

## 1. Groundwork

Done 2026-10-30. Conventions and the verify/regenerate workflow are documented in [`testdata/README.md`](../testdata/README.md).

- [x] Create `testdata/` at the repo root with the layout from §5 (`symbology/`, `geo/`, `s57/`, `db/`, `ws/`, `regions/`, `admin/`, `security/`, `enc_cron/`, `fixtures/`) — plus `tools/` for the Python generators of the input data
- [x] Agree fixture conventions: `{ "input": …, "expected": … }` case lists; binary outputs as `.bin` + decoded `.json` — `testdata/README.md` "Conventions", enforced by `FixtureStore.table` / `FixtureStore.binary`
- [x] Every fixture file carries a header (or sidecar) naming the decision it pins — `FixtureHeader(pins, decisions, generator, source)` at the top of every JSON file; XML/SQL get a leading comment; S-57 inputs get a `README.md` sidecar
- [x] Shared Kotlin test helper for reading/writing fixtures (regenerate mode vs. verify mode) — module `test_fixtures`, `io.madrona.njord.testdata.FixtureStore`, mode from `NJORD_FIXTURES=regenerate`, root from `NJORD_TESTDATA` or the nearest `testdata/README.md`; on the test classpath of `libgdal`, `server`, `enc_cron`; self-tested by `FixtureStoreTest`
- [x] Copy `US5WA22M` from `libgdal/src/nativeTest/resources` to `testdata/fixtures/US5WA22M/` — verified intact by `libgdal` `FixtureCellsTest`
- [x] Build two tiny synthetic cells with overlapping coverage at different scales (needed by P-TE-5 / TE-1) — `testdata/fixtures/synthetic/US4ZZ01M.000` (1:80 000, zoom 11) and `US5ZZ02M.000` (1:10 000, zoom 14, nested inside), generated deterministically by `testdata/tools/make_synthetic_cells.py` with GDAL's S-57 writer; read back through `OgrS57Dataset` in `FixtureCellsTest`
- [x] Build or obtain a cell with `.001`–`.003` updates (needed by P-IN-4) — `US5WA22M` already ships `.001`–`.004`; `DSID_UPDN` reads back as `4`. For the pre-update state copy only the `.000` to a scratch directory.
- [x] Trim a real NOAA catalog to `enc_cron/catalog_sample.xml` incl. the `dataQualityInfo` decoy and a record missing fields (P-EC-1) — `testdata/enc_cron/catalog_sample.xml` by `testdata/tools/trim_catalog.py`: four real records (`US1EEZ1M`, `US3AK12M`, `US509890`, `US5A1KFV` with an `x.0` edition), a synthetic `US5ZZ99M` without `<onLine>`, and the real trailing `seriesMetadata` `MD_Metadata` record that has no cell fields at all

## 2. Defect decisions (D1–D9)

Decided 2026-10-30, recorded in [TEST_COVERAGE_AUDIT.md §3.1](TEST_COVERAGE_AUDIT.md#31-decisions-2026-10-30). **Policy: D1 is fixed in Kotlin; D2–D9 are accepted as-is in Kotlin and fixed in the Rust port.** Fixtures record the Kotlin value as `expected` and the corrected value as `port.expected` where the two differ; conformance cases that only pass on the port are tagged `port_only`.

- [x] **D1** `displayDepths` tenths truncation — fixed 2026-10-27, pinned by `SoundingsTest` / `SoundgTest`
- [x] **D2** `EncSaveHandler` `filename` path traversal (SEC-3) — port: bare `.zip` file name only, else 400
- [x] **D3** `ZipFile.unzipToPath` zip-slip, server ingest and `enc_cron` `extractCell` (SEC-4) — port: `enclosed_name()`; a bad entry aborts the whole extraction (server) or fails that cell (`enc_cron`)
- [x] **D4** `verifySignature` throws on malformed input → decide throw vs `false`; constant-time compare (SEC-1) — port: **`false`** (401) and constant-time byte compare; expiry rule, date formatting and the unchecked `expirationDate` stay Kotlin-compatible
- [x] **D5** `sqlOpAsync` retry double-counts features; `ChartDao.insertAsync(overwrite=true)` delete + insert outside a transaction (IN-4, DB-2) — port: retry only when no connection / SQLSTATE 08 before side effects; counters added after COMMIT; overwrite is one transaction
- [x] **D6** `DistributedLock` check-then-rename race; may a non-owner clear? does an upload abort an in-flight ingest? (CC-1, CC-2) — port: same file protocol plus a 1500 ms settle re-read; **non-owner may clear**; **upload does not abort**, `DELETE /v1/enc_save` does
- [x] **D7** missing `DSID_DSNM` → chart named `""`; `MultiPolygon` `M_COVR` dropped (IN-2) — port: fall back to the `.000` file name; union of all `CATCOV=1` polygons incl. `MultiPolygon` members
- [x] **D8** `TileHandler` `z/x/y` not range-checked, unbounded cache growth (API-3) — port: `0 ≤ z ≤ 24`, `0 ≤ x,y < 2^z` checked before encode/cache, else 400; non-integer stays 404
- [x] **D9** `EncUrlHandler` saves non-2xx bodies as `.zip` (API-6) — port: `error_for_status()` plus zip magic check before publishing; 202 contract unchanged

## 3. Fixtures and tests (§5)

### 3.1 Symbology — highest value

Done 2026-10-30 (`server` tests `PreTileEncodeFixtureTest`, `PreTileEncodeCellFixtureTest`, `SymbologyTablesFixtureTest`, `StyleFixtureTest`; SY-10 and SY-11 as plain tests). P-SY-1 inputs are derived mechanically — a recording props map reveals which attributes each rule reads — plus hand-written tier-0 boundary cases; 124 files, ~3.7k cases. P-SY-7 pins the first request of a fresh `LayerFactory` (D11).

- [x] **P-SY-1** `symbology/pre_tile_encode/<OBJL>.json` for every `Layerable` with `preTileEncode`
- [x] **P-SY-2** generator: `preTileEncode` over every real feature in `US5WA22M`, grouped by layer
- [x] **P-SY-3** `symbology/depth_bands.json` incl. non-default `ChartsConfig`
- [x] **P-SY-4** `symbology/display_depths.json` 0.0…31.0 in 0.1 steps + drying heights (generate from the fixed D1 code)
- [x] **P-SY-5** `symbology/light_description.json` — PresLib examples + every LIGHTS feature in the cell
- [x] **P-SY-6** `symbology/attribute_helpers.json` — every `attributehelpers/*` decode, 0…max+1
- [x] **P-SY-7** `symbology/style/<depth>_<theme>.json` — full style JSON for every `Depth × ThemeMode × custom theme`, fixed `baseUrl`
- [x] **P-SY-8** `symbology/sprites.json` (lift from `SpriteSheetTest`)
- [x] **P-SY-9** `symbology/svg_sector.json`

### 3.2 Tile math and MVT

P-TE-1…4 done 2026-10-30 (`libgdal` `TileSystemFixtureTest`, `MvtFixtureTest`, `OgrGeometryFixtureTest`; `server` `TileEncoderPureFixtureTest`). P-TE-5 done 2026-10-30 (`GoldenTilesTest`): 45 tiles z0–z16 under `geo/tiles/<z>_<x>_<y>.json` plus two `_info.json`, ingested through the real `ChartIngest` (`TestIngest`) into the `njord_test` database; stored as canonical decoded views (ids dropped, layers and features sorted) because PostGIS row order and MVT feature ids are not stable, so byte-exact MVT is pinned by P-TE-2 only. Note: PLY is a boundary line, so a tile wholly inside a coverage has no PLY layer, while a world-sized tile carries PLY for every chart. P-TE-1 exposed D10 (`mapSize` overflow at z ≥ 20). LB-1 geometry ops are pinned in `geo/ogr_geometry.json`.

- [x] **P-TE-1** `geo/tile_system.json` incl. poles, antimeridian, z0, z24
- [x] **P-TE-2** `geo/mvt/<case>.bin` + `.json` — points, lines, polygons, holes, every prop type, buffer clipping, winding
- [x] **P-TE-3** `geo/filtered_props.json`
- [x] **P-TE-4** `geo/base_map_scale.json`
- [x] **P-TE-5** golden tiles: ~40 decoded tiles z4–z16 over the cell, the synthetic overlap, and base-map-only tiles — **the most important fixture**

### 3.3 S-57 ingest

P-IN-1…7 done 2026-10-30 (`libgdal` `S57FixtureTest`, `NaturalEarthFixtureTest`; `server` `WireFormatFixtureTest`, `ChartIngestTest` for IN-1/3/4/5 through the real `ChartIngest`, `db_state.json` for P-IN-5). The 5 geometry-less collection objects in US5WA22M are skipped at insert (5163 counted, 5158 installed). IN-4 pins Kotlin as-is: an aborted chart keeps its `charts` row with zero features. P-IN-4 uses the real cell: `.000` alone is UPDN 0 / 5164 features, with `.001`–`.004` applied UPDN 4 / 5163 features, 4 added / 2 removed / 442 changed LNAMs.

- [x] **P-IN-1** `s57/US5WA22M/chart_insert.json`
- [x] **P-IN-2** `s57/US5WA22M/features/<LAYER>.json` — props + WKT after `makeValid`
- [x] **P-IN-3** `s57/layer_counts.json`
- [x] **P-IN-4** `s57/update_cell/` — `DSID_UPDN`/`EDTN` and changed features after updates
- [x] **P-IN-5** `s57/US5WA22M/db_state.json` — `charts` row + per-layer aggregates after ingest
- [x] **P-IN-6** `s57/natural_earth.json` — `normalizeLayerName`, `scaleFromFilename`, NE sample row counts
- [x] **P-IN-7** `ws/messages.json` — every `WsMsg` subtype

### 3.4 Database contract

Done 2026-10-30 (`DbMigrationsTest`, `ChartDaoTest`, `FeatureQueriesTest`, `RegionDaoTest`, `DaoRetryFixtureTest`) against the dedicated `njord_test` database (`TestEnv`, audit §7 items 2–3); `ChartQueryTest` deleted. `db/schema.json` is the schema contract (fresh == migrated-from-v1). `db/overwrite.json` carries the D5 `port` divergence. US5WA22M has no TOPMAR layer, so P-DB-4 uses a synthetic TOPMAR chart plus `db/lnam_refs.json` over the real cell.

- [x] **P-DB-1** `DbMigrations`: empty → current, v1 → migrated, schema dumps identical; `meta_data.version` handshake
- [x] **P-DB-2** chart overwrite, delete cascade, unknown name → false
- [x] **P-DB-3** `db/feature_queries.json` — feature query row shape and clipping
- [x] **P-DB-4** TOPMAR association queries
- [x] **P-DB-5** catalog paging / editions key — move existing expectations into shared fixtures
- [x] **P-DB-6** region flags (`regionNeedsRebuild`, `markRegionExported`, `clearRegionExportState`, `regionsContainingChart`)
- [x] **P-DB-7** `db/retry.json` — `sqlOpAsync` retry/backoff spec table

### 3.5 HTTP conformance suite (`conformance/`)

Done 2026-10-30; see [`conformance/README.md`](../conformance/README.md) for modes, markers and the exact-vs-shape rule (exact everywhere for catalogue-independent routes, shape against a different catalog). Goldens recorded from build 76a4e0f: 232 passed / 2 `port_only` skipped locally; diff mode :9100 vs :9101 zero differences. P-API-2 compares the live style with P-SY-7 after `{BASE}` and layer-id normalisation (D11); P-API-3 adds every P-TE-5 coordinate under `testdata/geo/tiles/`. Kotlin behaviours the suite pinned that the audit did not list: `geojson` GET returns 404 for any layer that has features (`Feature.id` decode failure); `style/{bad}` is 500 not 404; unknown routes and non-integer tile parameters serve the SPA page (200 `text/html`), so D8's "non-integer stays 404" is really "falls through to the web app"; `CompletionReport.totalFeatureCount` counts the geometry-less objects that `items` does not.

- [x] Decide the implementation: **Python / pytest** (validated 2026-10-30; see §5.5 of the plan for why not Rust or `hurl`)
- [x] Scaffold `conformance/` with `requirements.txt` (pinned `pytest`, `requests`, `websockets`, `mapbox-vector-tile`; Python ≥ 3.12) and a `conftest.py` taking `--base-url`, `--compare-url`, `--record`, `--golden-dir`
- [x] `lib/client.py`: admin login via `GET /v1/admin` basic auth, signed GET/POST/DELETE, multipart upload (lift from `data/upload_noaa_region_encs.py`)
- [x] `lib/ws.py`: `/v1/ws/enc_process` client on `websockets.sync`, returns the ordered `WsMsg` list
- [x] `lib/tiles.py`: gunzip, `mapbox_vector_tile.decode`, canonical sort by (layer, id/`LNAM`, props); sha256 of raw bytes recorded but not asserted
- [x] `lib/mask.py`: volatile-field masking by JSON pointer (timestamps, uuids, durations) and base-URL → `{BASE}` normalisation; masking list committed next to each golden file
- [x] Record / verify / diff modes wired up; `golden/<route>.json` written by record mode against the Kotlin server
- [x] Suite imports nothing from `server/`, `shared/` or (later) `crates/`
- [x] **P-API-1** `about/version`, `s57objects`, `s57attributes`
- [x] **P-API-2** `style/{depth}/{theme}[?c=]` — equal to P-SY-7 after normalising `baseUrl`
- [x] **P-API-3** `tile_json`, `tile/{z}/{x}/{y}`, `?info=true`
- [x] **P-API-4** `chart`, `chart_catalog`, `chart_editions`, `feature/layer`, `feature/lnam`, `geojson` GET
- [x] **P-API-5** `content/…`, `icon/{name}`, `sector_svg/…` incl. traversal refusal
- [x] **P-API-6** `admin` basic auth, `admin/verify`
- [x] **P-API-7** every mutating route × {no signature, bad signature} → 401
- [x] **P-API-8** `enc_save` → ingest → `ws/enc_process` sequence → `DELETE enc_save`
- [x] **P-API-9** `chart` POST/DELETE, `geojson` POST, `cache` GET/DELETE, `nuke`
- [x] **P-API-10** `regions` GET/POST, `regions/{archive}` with/without `Range` (206/416)
- [x] **P-API-11** `X-Forwarded-*` → `externalBaseUrl` in style URLs and signatures
- [x] **P-API-12** 405 / 404 / CORS

### 3.6 Region export

Done 2026-10-30 (`RegionExportTest`): a real archive from the three fixture cells (116 tiles, 3 charts) whose tiles equal the live tile path and the P-TE-5 goldens where coordinates coincide; `njord:generated_at` and archive names are pinned by format. The test forces `TZ=America/Los_Angeles` for the DST cases; CI should set `TZ` explicitly too.

- [x] **P-RE-1** `regions/schema.sql`
- [x] **P-RE-2** `regions/metadata.json`
- [x] **P-RE-3** archive from the fixture cell: tile coordinate set, `charts` row, pruned `chart_tiles`, decoded tiles vs P-TE-5
- [x] **P-RE-4** `regions/timestamps.json` incl. DST hour, archive name parsing, prune ordering
- [x] **P-RE-5** `regions/manifest.json` with/without archives, sidecar reuse

### 3.7 Admin signatures and security

Done 2026-10-30 (`AdminSignatureFixtureTest`, `SecurityPathsFixtureTest`). `AdminUtil` gained internal overloads with an injectable clock and nonce so the vectors are reproducible; the public API is unchanged.

- [x] **P-SEC-1** `admin/hmac_vectors.json` — RFC 4231 + `createSignature` with fixed inputs
- [x] **P-SEC-2** `admin/verify_cases.json` (needs D4)
- [x] **P-SEC-3** `security/paths.json` — `resourceName()`, archive names, upload filenames (D2), zip entries (D3)

### 3.8 `enc_cron`

Done 2026-10-30. `BundleBuilder` and `deleteOrphans` now depend on an `internal interface EncTransport` that `EncHttp` implements, so the fixtures drive them with a fake transport. Building `enc_cron` locally needs `brew install expat`.

- [x] **P-EC-1** `enc_cron/catalog_sample.xml` + `parsed.json`
- [x] **P-EC-2** `enc_cron/selection.json` — lift from existing tests
- [x] **P-EC-3** `enc_cron/bundle_layout.json`
- [x] **P-EC-4** `enc_cron/config.json`
- [x] **P-EC-5** `deleteOrphans` guard-ordering decision table

### 3.9 Concurrency primitives (behavioral, not golden)

Done 2026-10-30 (`ConcurrencyPrimitivesTest`, plus `concurrency/lock_semantics.json` carrying the D6 port semantics and `concurrency/cadence.json`).

- [x] **P-CC-1** lock file format and acquire/clear semantics (needs D6)
- [x] **P-CC-2** `IngestStatus` file: atomic write, JSON `WsMsg`, written only while holding the lock
- [x] **P-CC-3** worker cadence: ingest poll 5 s; region worker 5 s / idle 15 min / `wake()`

### 3.10 Frontend (§7)

- [x] Route list and query-param behavior pinned as a fixture (extend `RouteMatcherTest`) — `testdata/frontend/routes.json`, embedded in `shared_fe` `RouteFixtureTest` and mirrored by `testdata/tools/check_routes_fixture.py` (Kotlin/JS tests cannot read files)
- [x] Screenshot checklist per page (home/map, catalog, chart info, layer, symbols, installer/admin, about) — [FRONTEND_SCREENSHOT_CHECKLIST.md](FRONTEND_SCREENSHOT_CHECKLIST.md)

## 4. CI

Decided 2026-10-30: **no hosted CI.** A GitHub Actions workflow (`.github/workflows/ci.yml`) was written and then removed without ever running. The same checks are run locally before committing, against the dev PostGIS (`chart_server_db`, `localhost:6432`):

- [x] `./gradlew allTests` (verify mode)
- [x] `NJORD_FIXTURES=regenerate ./gradlew allTests` then `git diff --exit-code -- testdata/`: a diff is a behaviour change to review and commit deliberately
- [x] `pytest conformance --base-url=http://localhost:9000` against a locally running Kotlin server (`conformance/README.md`)

## 5. Exit criteria

- [x] All D1–D9 resolved, each decision recorded — plus D10–D12 found while generating fixtures (TEST_COVERAGE_AUDIT.md §3)
- [x] `testdata/` committed; validated locally (219 native tests, verify mode, regenerate produces no diff). GDAL-derived fixtures pin the macOS GDAL 3.13.1; regenerating under Debian 13's GDAL may change them
- [x] Conformance suite green in verify mode against the Kotlin server locally — 232 passed, 2 `port_only` skipped (2026-10-30, build 76a4e0f)
- [ ] Conformance suite green in verify mode against `https://openenc.com` (read-only routes) — **134 passed, 19 skipped with `style` and `content/sprites` deselected**; those 59 cases fail only because production runs `505032bc` (2026-10-18, before `b4800ae soundg - drying heights` on master) with the old SOUNDG style and sprite sheet. Re-run after the next `openenc.com` deploy; everything else (about, tile_json, icons, sector_svg, 401/405/404/CORS, data routes by shape) is green
- [x] Diff mode exercised once: local Kotlin vs local Kotlin on a second port must report zero differences (proves the masking list before Rust exists) — 0 differences (both servers must hold region archives: `region_export_state` is per database, so a second server on the same database never renders its own)

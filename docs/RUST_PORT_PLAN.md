# Rust / Leptos Port Plan

_Written 2026-10-27. Companion to [TEST_COVERAGE_AUDIT.md](TEST_COVERAGE_AUDIT.md), whose test IDs (SY-3, TE-1, …) are referenced below._

## 1. Goals

1. Re-implement the Njord backend in Rust and the web UI in Leptos, as **two separately built and deployed artifacts** that only meet at the HTTP API.
2. Replace the hand-written C interop modules (`libgdal`, `libpq`, `libzip`, `libsqlite`, `libexpat`, in-house `geojson`, in-house MVT encoder) with maintained crates wherever a pure-Rust crate exists.
3. Preserve every externally observable contract: the HTTP/WebSocket API, the PostGIS schema, the MapLibre style JSON, the mobile `.mbtiles` archive, the `enc_cron` ↔ server protocol, and the on-disk layout (`save/`, `ingest/`, `regions/`, tile cache, lock and status files) that Kubernetes and the deb package rely on.
4. Run old and new servers side by side against the **same database** during cutover (strangler pattern), so the port can be switched over route by route.

**Non-goals:** changing the S-52-ish symbology, the schema, or the mobile format during the port. Any of those should be a separate change after the Rust server is at parity, when the conformance suite described below can catch regressions.

## 2. Why tests come first

The Kotlin code is the only specification of most behaviour. The audit found that the parts that matter most — symbology (`preTileEncode`), tile assembly, ingest, admin auth — have almost no tests. Porting them without a safety net means re-deriving behaviour from the Kotlin source and hoping.

The plan therefore front-loads **characterization tests whose expected values live in language-agnostic fixture files** (`testdata/`). The Kotlin implementation generates the fixtures; the Rust implementation must reproduce them byte-for-byte or value-for-value. Those same fixtures stay as regression tests after the Kotlin code is deleted.

Three layers of safety net, from cheapest to most complete:

| Layer | What it pins | Runs against |
|---|---|---|
| **Golden fixtures** (JSON / binary files under `testdata/`) | Pure functions: symbology, depth formatting, style JSON, tile math, MVT bytes, HMAC, S-57 metadata extraction | Either implementation's unit tests |
| **Database contract tests** | SQL behaviour: migrations, chart overwrite, feature queries, region flags | Either implementation, shared PostGIS |
| **HTTP conformance suite** (black box, takes a base URL) | Every route's status codes, headers, JSON shape, tile bytes | Kotlin server, then Rust server, then both with a diff |

## 3. Target architecture

### 3.1 Cargo workspace

```
njord/
├── Cargo.toml                  # workspace
├── crates/
│   ├── njord-model/            # shared API types (serde). Compiles for native AND wasm32. The contract.
│   ├── njord-geo/              # tile system, Web Mercator math, clip polygons, MVT encoder
│   ├── njord-s57/              # S-57 + shapefile ingestion (GDAL behind one trait; see §4.2)
│   ├── njord-symbology/        # Layerable equivalents: style JSON generation + preTileEncode rules
│   ├── njord-db/               # sqlx queries, migrations, DAOs
│   ├── njord-server/           # axum binary: endpoints, workers, tile cache, region export
│   ├── njord-enc-cron/         # NOAA catalog diff/download binary
│   └── njord-web/              # Leptos (CSR, wasm32) — depends only on njord-model
├── testdata/                   # golden fixtures, shared with the Kotlin repo during transition
└── conformance/                # HTTP black-box suite (Rust integration tests + hurl files)
```

`njord-web` must **not** depend on any backend crate other than `njord-model`. That is the mechanical guarantee of frontend/backend separation; a CI check (`cargo tree -p njord-web`) enforces it.

### 3.2 Runtime shape

- **Backend:** one `njord-server` binary (axum on tokio). Blocking work — GDAL reads, gzip, SQLite writes, tile encoding — runs on `spawn_blocking` or a dedicated rayon pool. Same CLI shape as today: `njord-server <resources dir>` with `CHART_SERVER_OPTS` JSON overrides.
- **Frontend:** `njord-web` built with Trunk to static files, served either by `njord-server` (today's `webStaticContent`) or by any static host. MapLibre GL stays as the JS map engine, bound through `wasm-bindgen`.
- **Cron:** `njord-enc-cron` binary, same CLI flags, same `ENV_OVERRIDES` config merge.

## 4. Library replacement map

Columns: what the Kotlin module does today → recommended crate → notes. Major-version lines are as of mid-2026; confirm on crates.io before pinning.

### 4.1 Direct replacements (pure Rust)

| Kotlin module | Responsibility | Rust crate | Notes |
|---|---|---|---|
| `libpq` (hand-rolled `PgDataSource`, `PgStatement`, `ResultSet`, `PqArray`) | PostgreSQL client, pooling, arrays, bytea, jsonb | **`sqlx`** (`postgres`, `runtime-tokio`, `json`, `uuid` features) | Async, built-in pool, parameter binding, `Vec<String>` ↔ `varchar[]`, `serde_json::Value` ↔ `jsonb`. Add **`geozero`** with `with-postgis-sqlx` to decode `geometry` columns straight to `geo_types::Geometry` instead of hand-parsing `st_asbinary` output. Alternative: `tokio-postgres` + `deadpool-postgres` if compile-time query macros are unwanted. |
| `libsqlite` | Writing `.mbtiles` archives | **`rusqlite`** (`bundled` feature) | Statically links SQLite, so the container no longer needs `libsqlite3`. Transactions, prepared-statement reuse, blobs all map 1:1 to the current `SqliteDb` API. |
| `libzip` | Reading uploaded ENC zips, writing `enc_cron` bundles | **`zip`** (`deflate` feature) | `ZipFile::by_index(i).enclosed_name()` returns `None` for zip-slip paths, which fixes audit defect **D3** for free. Streams entries; no whole-file reads. |
| `libexpat` (`ExpatSax`, `ElementPath`) | Streaming SAX over NOAA's ~100 MB `ENCProdCat_19115.xml` | **`quick-xml`** | Pull parser with a bounded buffer; the `ElementPath` tail-matching rules in `EncProdCatParser` port directly. Namespace-aware when configured. |
| `geojson` (in-house RFC 7946 impl) | GeoJSON (de)serialization, bbox | **`geojson`** (georust) + **`geo-types`** | Serde-based, RFC 7946, converts to/from `geo` geometries. Drop the in-house module entirely. |
| `libgdal/tile/*` (`VectorTileEncoder`, `Tile.kt` protobuf model, zig-zag commands) | MVT encoding | **`geozero`** (`mvt` feature) or **`mvt`** | `geozero::mvt` encodes `geo` geometries into the MVT protobuf (via `prost`), handles tile extent scaling, and — important for tests — **decodes** tiles too, which the Kotlin side never could. Use `mvt` if a smaller dependency is preferred; it is a pure encoder with a clean builder API. |
| `libgdal/TileSystem.kt` | Web Mercator math, scale↔zoom, clip polygons | **own code in `njord-geo`** | ~190 lines, already well tested (`TileSystemTest`). Port as-is; golden fixtures pin it (§5.2). |
| Gzip (zlib interop in `util/Gzip.kt`) | Compressing archive tiles | **`flate2`** | — |
| SHA-256 / HMAC (OpenSSL interop) | Archive checksums, admin signatures | **`sha2`**, **`hmac`**, **`subtle`** | RustCrypto. `subtle::ConstantTimeEq` for the signature compare (audit **D4**). |
| `DistributedLock` inotify interop | Watching the shared lock file | **`notify`** | inotify on Linux, FSEvents/kqueue on macOS, polling fallback — replaces both the inotify path and the hand-written fallback. |
| Ktor server | HTTP, WebSocket, static files, CORS, forwarded headers, basic auth | **`axum`** + **`tower-http`** (`fs`, `cors`, `compression`, `trace`) | WebSocket via `axum::extract::ws`. Forwarded-header handling for `externalBaseUrl()` via `axum-client-ip` or a small extractor. |
| Ktor client (curl engine) | Downloads in `EncUrlHandler` and `enc_cron` | **`reqwest`** (`rustls-tls`, `stream`) | Set `.error_for_status()` so error bodies are never saved as `.zip` (audit **D9**). |
| `kotlinx.serialization` | JSON | **`serde`**, **`serde_json`** | `njord-model` holds every wire type once, with `#[serde(rename = "…")]` matching current field names exactly. |
| `kotlinx.datetime` / `kotlin.time` | Timestamps, signature expiry | **`jiff`** (or `time`) | `jiff` handles the local-zone archive timestamp stem round-trip (`RegionExporter.parseTimestampStem`) including DST. |
| `UUID` interop | Lock ownership, signature nonce | **`uuid`** (`v4`) | — |
| Logging | — | **`tracing`** + **`tracing-subscriber`** | — |
| Kotlin/JS + Compose HTML | Web UI | **`leptos`** (CSR) + **`leptos_router`** + **`wasm-bindgen`** / **`web-sys`** | See §7. |

### 4.2 The one that stays C: S-57 reading

There is **no mature pure-Rust S-57 / ISO 8211 reader** that applies `.001…` update files, assembles spatial primitives into feature geometries, and emits the exact attribute names GDAL does. Everything downstream — the `features.props` JSONB, `LNAM`/`LNAM_REFS`, the `METERS` sounding field, `DSID_*`/`DSPM_*` metadata, `SPLIT_MULTIPOINT` behaviour, `RECODE_BY_DSSI` charset handling — is shaped by GDAL's S57 driver options:

```
RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,
SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON,ADD_SOUNDG_DEPTH=ON
```

Changing the reader changes the data in the database and in every mobile archive. So:

| Kotlin module | Responsibility | Rust crate | Notes |
|---|---|---|---|
| `libgdal/OgrS57Dataset`, `OgrLayer`, `OgrFeature` | Open `.000`, apply updates, iterate layers/features, field → JSON mapping | **`gdal`** (georust bindings, with `gdal-sys`) | Safe, maintained bindings over the system `libgdal` (Debian 13 ships 3.10). Confine it to **`njord-s57`** behind a `trait S57Reader` so it is the only crate with a C dependency and can be swapped later. The same crate reads Natural Earth shapefiles, though the pure-Rust **`shapefile`** + **`dbase`** crates can take that over immediately. |
| `libgdal/OgrGeometry` (intersection, difference, boundary, envelope, centroid, isValid, isEmpty, WKB/WKT/GeoJSON) | Geometry ops in `TileEncoder` and `RegionExporter` | **`geo`** (+ `geo-types`, `wkb`/`wkt`) | Pure Rust: `BooleanOps` (intersection/difference/union, and `clip` for lines against polygons), `Validation`, `BoundingRect`, `Centroid`, `Area`, `Simplify`. Robust i_overlay-based boolean ops since 0.28. |
| `OgrGeometry.makeValid()` | Repairing invalid S-57 polygons at ingest | `gdal` (`Geometry::make_valid`) **inside `njord-s57` only** | `geo` has no `make_valid`. Since the reader already uses GDAL, do validity repair there at ingest time, and the tile path never needs it. If a pure-Rust ingest ever lands, **`geos`** (libgeos bindings) is the fallback. |

**Long-term option (not in scope):** a `s57` crate implementing ISO 8211 + S-57 Part 3 (update application, FSPT/VRPT linkage). Roughly the size of GDAL's `ogr/ogrsf_frmts/s57` (~8k lines of C++). The golden fixtures in §5.3 are exactly what would make that swap safe.

### 4.3 What the container loses and keeps

Today's runtime image installs `libgdal`, `libpq5`, `libzip`, `libsqlite3`, `libexpat1`, `libssl`, `zlib`. After the port: **only `libgdal` (and its transitive deps) remains**; everything else is statically linked Rust. If `njord-s57` is built as a separate ingest binary, the tile-serving image can drop GDAL too.

## 5. Tests to write before the port

These are the tests whose *expected values are produced by the Kotlin implementation* and stored under `testdata/`. Write them in Kotlin now (they also close most of the audit's tier-0 list), have them emit fixtures, then re-implement the *same* tests in Rust reading the *same* fixtures.

Fixture conventions:
- `testdata/<area>/<name>.json` — a list of `{ "input": …, "expected": … }` cases. Table-driven on both sides.
- Binary outputs (MVT, protobuf) stored as `.bin` plus a `.json` decoded view, so diffs are readable.
- `testdata/fixtures/US5WA22M/` — the existing NOAA cell from `libgdal/src/nativeTest/resources`. Add two tiny synthetic cells with overlapping coverage at different scales (needed for TE-1).

### 5.1 Symbology (`layers/*` → `njord-symbology`)  — **highest value**

This is 128 classes of conditional logic and the hardest thing to port by reading. Capture it mechanically.

| ID | Fixture | What it pins | Audit ref |
|---|---|---|---|
| P-SY-1 | `symbology/pre_tile_encode/<OBJL>.json` — for every `Layerable` with `preTileEncode`, a list of `{props_in, geometry_type, props_out}` | Exact output props (`SY`, `AC`, `LC`, `AP`, `LP`, sounding fields, `TOPMAR` assoc …) for a representative input set | SY-3…SY-12 |
| P-SY-2 | Generator: run `preTileEncode` over **every real feature in `US5WA22M`** grouped by layer and record props in/out. ~5k cases, generated, not hand-written. | Real-world coverage of every branch a NOAA cell exercises | SY-10 |
| P-SY-3 | `symbology/depth_bands.json` | `Depare` thresholds at exact boundaries, with a non-default `ChartsConfig` | SY-3 |
| P-SY-4 | `symbology/display_depths.json` | `displayDepths` for 0.0…31.0 in 0.1 steps plus drying heights. **Decide D1 first**: record the *corrected* values and fix Kotlin, or the port inherits the bug. | SY-1, SY-2 |
| P-SY-5 | `symbology/light_description.json` | `LightDescription.head/tail` for the PresLib examples + every LIGHTS feature in the fixture cell | existing `LightDescriptionTest` |
| P-SY-6 | `symbology/attribute_helpers.json` | Every `attributehelpers/*` enum decode, all values 0…max+1 | SY-9 |
| P-SY-7 | `symbology/style/<depth>_<theme>.json` — the **full style JSON** from `StyleHandler` for every `Depth × ThemeMode × custom theme`, with `baseUrl` fixed | The entire MapLibre style: layer order, ids, filters, expressions, paint/layout. The port must serialise byte-identically after JSON normalisation. | API-2, SY-13 |
| P-SY-8 | `symbology/sprites.json` | `SpriteSheet` name → rect per theme; `Sprite` enum list | existing `SpriteSheetTest` |
| P-SY-9 | `symbology/svg_sector.json` | `buildSectorSvg` for a grid of sector/colour inputs | — |

### 5.2 Tile math and MVT encoding (`libgdal/TileSystem`, `tile/*` → `njord-geo`)

| ID | Fixture | What it pins | Audit ref |
|---|---|---|---|
| P-TE-1 | `geo/tile_system.json` | `latLngToTileXy`, `latLngToTileBoundedXy`, `scaleToZoom(Int)`, `createTileClipPolygon(expandPixels)` WKT for a grid of inputs incl. poles, antimeridian, z0 and z24 | TE-9 |
| P-TE-2 | `geo/mvt/<case>.bin` + `.json` | `VectorTileEncoder.encode()` bytes for hand-built points/lines/polygons/multipolygons with holes, props of every JSON type, clipping at the buffer, winding order. The Rust side decodes with `geozero` and compares the decoded view; byte equality is a bonus, not a requirement (tag ordering may differ). | TE-8 |
| P-TE-3 | `geo/filtered_props.json` | `TileEncoder.filtered()` — blank strings, `"0"` on enumerated vs float attrs, empty arrays | TE-3 |
| P-TE-4 | `geo/base_map_scale.json` | `findBaseMapScale` boundaries | TE-6 |
| P-TE-5 | **Golden tiles.** Ingest the fixture cells into a clean DB and record `/v1/tile/{z}/{x}/{y}` for ~40 tiles spanning z4–z16 across the cell, the overlap of the synthetic pair, and tiles with no chart (base map only). Store decoded JSON (layers → features → props + geometry). | Chart priority masking, zoom eligibility, LIGHTS not clipped, PLY boundaries, base-map fill — the whole `addCharts` algorithm | TE-1, TE-2, TE-4–TE-7 |

P-TE-5 is the single most important fixture in the plan. It is the only thing that proves the Rust tile path draws the same chart.

### 5.3 S-57 ingest (`OgrS57Dataset`, `ChartIngest` → `njord-s57`, `njord-db`)

| ID | Fixture | What it pins | Audit ref |
|---|---|---|---|
| P-IN-1 | `s57/US5WA22M/chart_insert.json` | `chartInsertInfo()` output: name, scale, zoom, updated/issued, coverage GeoJSON, full `dsidProps`, `chartTxt` map | IN-2 |
| P-IN-2 | `s57/US5WA22M/features/<LAYER>.json` | Every feature's GDAL-mapped props and geometry WKT per layer (after `makeValid`). This is the GDAL output contract; the `gdal` crate must reproduce it exactly, and a future pure-Rust reader would be measured against it. | IN-1, LB-2 |
| P-IN-3 | `s57/layer_counts.json` | `featureCount(exLayers)` per layer, `exLayers` set | IN-1 |
| P-IN-4 | `s57/update_cell/` | A cell with `.001`–`.003` updates: `DSID_UPDN`/`EDTN` after application and the changed features | IN-10 |
| P-IN-5 | DB contract: after ingest, dump `charts` row and `SELECT layer, count(*), min(z_min), max(z_max), count(lnam_refs)` grouped — stored as `s57/US5WA22M/db_state.json` | `insertFeatureSync` mapping (MINZ/MAXZ defaults, lnam_refs, 2D forcing) | DB-6 |
| P-IN-6 | `s57/natural_earth.json` | `normalizeLayerName`, `scaleFromFilename` tables; shapefile ingest row counts for a tiny NE sample | IN-8 |
| P-IN-7 | `ws/messages.json` | Every `WsMsg` subtype serialised (`Idle`, `Extracting`, `Info`, `CompletionReport`, `Error`) | SH-1 |

### 5.4 Database contract (`db/*` → `njord-db`)

The schema is shared with the running Kotlin server during cutover, so these are plain SQL-behaviour tests both implementations must pass against the same PostGIS.

| ID | Test | Audit ref |
|---|---|---|
| P-DB-1 | `DbMigrations`: empty DB → current version; v1 DB → migrated; schema dump of fresh vs migrated identical. The Rust migration runner must read and write the same `meta_data.version` row so the two servers agree on schema state. | DB-1 |
| P-DB-2 | Chart overwrite semantics; delete cascades; unknown name → false | DB-2, DB-3 |
| P-DB-3 | `findAllChartFeaturesAsync4326` / `findChartFeaturesAsync4326` row shape and clipping on the fixture cell (store as `db/feature_queries.json` with WKT) | DB-4 |
| P-DB-4 | TOPMAR association queries | DB-5 |
| P-DB-5 | Catalog paging and editions key (already tested; make the fixtures shared) | existing |
| P-DB-6 | Region flags: `regionNeedsRebuild`, `markRegionExported`, `clearRegionExportState`, `regionsContainingChart` (already partly tested) | DB-11 |
| P-DB-7 | `Dao.sqlOpAsync` retry/backoff semantics as a spec table (`db/retry.json`) so the Rust pool/retry wrapper matches: null-connection retries, non-retry on cancellation, max 7 | DB-13 |

### 5.5 HTTP conformance suite (`conformance/`)

Black box. Written once in Rust (`reqwest` + `serde_json`, or `hurl` files for the simple cases), parameterised by `NJORD_BASE_URL`. Run against Kotlin first to record, then against Rust to compare.

| ID | Routes | What it pins | Audit ref |
|---|---|---|---|
| P-API-1 | `GET /v1/about/version`, `/about/s57objects`, `/about/s57attributes` | JSON shape | API-7 |
| P-API-2 | `GET /v1/style/{depth}/{theme}[?c=]` for all combos | Byte-equal to P-SY-7 after normalising `baseUrl` | API-2 |
| P-API-3 | `GET /v1/tile_json`, `GET /v1/tile/{z}/{x}/{y}`, `?info=true` | Golden tiles P-TE-5; content type `application/x-protobuf`; info JSON | TE-7, API-3 |
| P-API-4 | `GET /v1/chart?name=`, `/v1/chart_catalog`, `/v1/chart_editions`, `/v1/feature/layer/{l}?start_id=`, `/v1/feature/lnam/{lnam}`, `/v1/geojson?chart_name=&layer_name=` | JSON shape, 404 paths, paging cursors | API-1, API-5 |
| P-API-5 | `GET /v1/content/{…}` (fonts, sprites), `/v1/icon/{name}`, `/v1/sector_svg/…` | Bytes, content types, traversal refusal | existing `StaticResourceContentTest` |
| P-API-6 | `GET /v1/admin` (basic auth), `POST /v1/admin/verify` | 401 without auth; signature verifies; expired fails | SEC-1, SEC-6 |
| P-API-7 | **Every mutating route × {no signature, bad signature}** → 401 | The authorization boundary | SEC-5 |
| P-API-8 | `POST /v1/enc_save?filename=` → `.zip` appears in `save/`; ingest worker picks it up; `/v1/ws/enc_process` streams `Extracting → Info → CompletionReport`; `DELETE /v1/enc_save` resets | End-to-end ingest through the API, including WebSocket message sequence | IN-1, CC-5 |
| P-API-9 | `POST/DELETE /v1/chart`, `POST /v1/geojson`, `DELETE /v1/cache`, `GET /v1/cache`, `DELETE /v1/nuke` | Status codes and side effects (cache dir, DB rows) | API-4, API-7 |
| P-API-10 | `GET /v1/regions`, `POST /v1/regions?name=`, `GET /v1/regions/{archive}` with and without `Range` | Manifest JSON, 202, 206/416 semantics | RE-7, RE-8 |
| P-API-11 | Forwarded headers: `X-Forwarded-Proto/Host/Port` → `externalBaseUrl` reflected in style URLs and signature validity | SEC-8 |
| P-API-12 | 405 for unimplemented verbs; 404 for unknown routes; CORS headers | API-8 |

Record responses as `conformance/golden/<route>.json` with volatile fields (timestamps, uuids, durations) masked.

### 5.6 Region export (`RegionExporter` → `njord-server`)

| ID | Fixture | What it pins | Audit ref |
|---|---|---|---|
| P-RE-1 | `regions/schema.sql` — `sqlite3 x.mbtiles .schema` of a Kotlin-produced archive | Table/index DDL, `WITHOUT ROWID`, FK | RE-1 |
| P-RE-2 | `regions/metadata.json` | Every `metadata` row for a known region config (`njord:*` keys, bounds, center, zooms) | RE-1 |
| P-RE-3 | Archive produced from the fixture cell: tile coordinate set (`z,x,tms_row` list), `charts` row, `chart_tiles` edges after pruning; per-tile decoded MVT compared to P-TE-5 | `compileTileCoordinates`, TMS flip, pruning, gzip | RE-1, RE-5 |
| P-RE-4 | `regions/timestamps.json` | `currentTimestamp` ↔ `parseTimestampStem` cases incl. DST hour; archive name parsing; prune ordering | RE-3, RE-4 |
| P-RE-5 | `regions/manifest.json` | `buildManifest()` with and without archives, sidecar reuse | RE-2 |

### 5.7 Admin signatures and security

| ID | Fixture | What it pins | Audit ref |
|---|---|---|---|
| P-SEC-1 | `admin/hmac_vectors.json` | `HmacSHA256` on RFC 4231 vectors; `createSignature` with fixed `now`/`uuid`/`baseUrl`/`key` → exact `signature` and `signatureEncoded` (base64 + URL-encoding). The Rust port must accept signatures minted by the Kotlin server during cutover and vice versa. | SEC-1, SEC-2 |
| P-SEC-2 | `admin/verify_cases.json` | Expired, tampered, wrong base URL, malformed base64/JSON → expected result. Decide **D4** (throw vs false) now. | SEC-1 |
| P-SEC-3 | `security/paths.json` | `resourceName()` traversal cases; archive name cases; upload filename cases (**D2**); zip entry cases (**D3**) | SEC-3, SEC-4, SEC-7 |

### 5.8 `enc_cron`

| ID | Fixture | What it pins | Audit ref |
|---|---|---|---|
| P-EC-1 | `enc_cron/catalog_sample.xml` + `parsed.json` | `EncProdCatParser` output for a trimmed real catalog incl. the `dataQualityInfo` decoy and a record missing fields | existing |
| P-EC-2 | `enc_cron/selection.json` | `selectStale`, `selectOrphans`, `deletableOrphans`, `batches` cases (lift from the existing tests) | existing |
| P-EC-3 | `enc_cron/bundle_layout.json` | `BundleBuilder` output entry list for two fixture cell zips: only `ENC_ROOT/<CELL>/…`, no `CATALOG.031` | EC-1 |
| P-EC-4 | `enc_cron/config.json` | `EncCronConfig.load` + `ENV_OVERRIDES` merge + base-URL derivation | EC-3 |
| P-EC-5 | Guard ordering for `deleteOrphans` as a decision table | EC-2 |

### 5.9 Concurrency primitives

The lock file, status file and worker loops are the inter-process protocol between replicas (and between server and `enc_cron` via `save/`). They cannot be golden-filed; write them as behavioural tests with the semantics decided up front:

| ID | Decision to record, then test | Audit ref |
|---|---|---|
| P-CC-1 | Lock file format (`uuid` text), acquire = atomic rename of a temp file, clear = rename of an empty file. **Resolve D6**: may a non-owner clear? Does an upload abort an in-flight ingest? | CC-1, CC-2 |
| P-CC-2 | `IngestStatus` file: atomic write, JSON `WsMsg`, write only while holding the lock | CC-4 |
| P-CC-3 | Worker cadence: ingest poll 5 s; region worker poll 5 s / idle 15 min / `wake()` | RE-9, IN-7 |

## 6. Port phases

Each phase ends when its conformance/fixture tests pass in Rust. No phase starts porting code whose fixtures don't exist yet.

### Phase 0 — Characterization (Kotlin repo)
- Write the §5 tests in Kotlin; make them emit `testdata/`.
- Add CI (PostGIS service + `./gradlew allTests`) so the fixtures are regenerated and validated on every change.
- Fix or formally accept each suspected defect (D1–D9) **before** fixtures are frozen. Every fixture is a decision about which behaviour the port must copy.
- Exit: `testdata/` committed; conformance suite green against the Kotlin server.

### Phase 1 — Workspace and the contract crate
- `njord-model`: every wire type (`Chart`, `ChartCatalog`, `ChartEditions`, `FeatureRecord`, `LayerQueryResultPage`, `RegionManifestEntry`, `AdminSignature`/`AdminResponse`, `EncUpload`, `EncUrlRequest`, `WsMsg`, `Style`/`Layer`/`Paint`/`Layout`, `S57Object`, `About`, `TileJson`, `Depth`, `ThemeMode`, `Sprite`, `Color`).
- Serde round-trip tests against `testdata/` JSON (P-IN-7, P-SY-7 types, P-API golden bodies).
- Exit: `cargo test -p njord-model` deserialises every golden response and re-serialises it identically.

### Phase 2 — Pure libraries
- `njord-geo`: tile system, clip polygons, MVT encoder (`geozero`/`mvt`), prop filtering. Pass P-TE-1…4.
- `njord-symbology`: port the 128 `Layerable`s and attribute helpers. Pass P-SY-1…9. Suggested order: `Layerable` base helpers → `Depare`, `Soundg`, `Obstrn`, `Wrecks`, `Uwtroc`, `Lights`, `Topmar` (tier-0 symbology) → buoys/beacons → everything else via P-SY-2 generated cases.
- `njord-s57`: `gdal` crate reader behind `trait S57Reader`; shapefile reading via `shapefile` crate. Pass P-IN-1…4.
- Exit: all §5.1–5.3 fixtures pass natively.

### Phase 3 — Read-only server, side by side
- `njord-db` (sqlx) with the migration runner reading the same `meta_data` version. Pass P-DB-1, 3, 4, 5, 6.
- `njord-server` read routes: `about`, `style`, `content`, `icon`, `sector_svg`, `tile_json`, `tile`, `chart`, `chart_catalog`, `chart_editions`, `feature`, `geojson` GET, `regions` GET, `regions/{archive}`, `cache` GET.
- Tile cache: same on-disk layout so both servers share it.
- Run both servers against the production-shaped dev DB; run the conformance suite against each and `diff` the recorded bodies and tiles (P-TE-5 decoded).
- Exit: conformance suite green on read routes; a reverse proxy can route `GET /v1/tile` to Rust in staging.

### Phase 4 — Write path and workers
- Admin signatures (P-SEC-1/2, cross-compatible with Kotlin-minted signatures), `enc_save`, `enc_url`, `chart` POST/DELETE, `geojson` POST, `cache` DELETE, `nuke`, `regions` POST, WebSocket status.
- `ChartIngestWorker` / `ChartIngest` / `NaturalEarthIngest` on `spawn_blocking`; lock + status files per P-CC-*.
- `RegionExporter` + worker with `rusqlite`. Pass P-RE-1…5.
- Exit: P-API-7…10, P-RE-*, P-CC-* green. Rust server can be the only replica.

### Phase 5 — `enc_cron`
- Port with `quick-xml`, `reqwest`, `zip`. Pass P-EC-1…5. Swap the CronJob image.

### Phase 6 — Leptos frontend
- See §7. Exit: route parity with `shared_fe/routing`, every API call typed through `njord-model`, manual QA on the map.

### Phase 7 — Cutover and removal
- Containerfile → multi-stage `rust:` builder, runtime with `libgdal` only. Update `k8s_deploy`, `debpkg`, `RELEASE_NOTES.md`.
- Delete Kotlin modules once the conformance suite has run green against Rust alone for a release cycle. Keep `testdata/` and `conformance/`.

## 7. Frontend: Leptos

The current UI is ~4.5k lines of Compose-HTML across `web` and `shared_fe`: a router (`NjordRoute`, `RouteMatcher`), view models, and pages (home/map, chart catalog, chart info, layer page, symbols/sprites, chart installer/admin, about). MapLibre GL is bound through `MapLibre.kt` externals.

- **Mode:** client-side rendering (`leptos` CSR, built with Trunk). SSR buys nothing for a map SPA and would couple the frontend to a Rust server process — the opposite of the separation goal.
- **Map:** keep `maplibre-gl` as JS, loaded from a `<script>` tag (or vendored into `dist/`), and bind with `#[wasm_bindgen(js_namespace = maplibregl)]` externs mirroring `MapLibre.kt`. Only the handful of calls the app uses need bindings (`Map::new`, `setStyle`, `on('click')`, `queryRenderedFeatures`, `addControl`, `fitBounds`, `getCanvas`).
- **State/VMs:** `BaseViewModel` → Leptos signals + `Resource`s; `Network.kt` → a small `reqwest`/`gloo-net` client returning `njord-model` types.
- **Routing:** `leptos_router` with the same path set as `NjordRoute` (keep URLs stable for bookmarks).
- **Admin flows:** signature in local storage (`gloo-storage`), WebSocket via `gloo-net::websocket` deserialising `WsMsg`.
- **Tests before porting:** the existing frontend tests are one `RouteMatcherTest`. The practical safety net is (a) the route list and query-param behaviour pinned as a fixture, (b) `njord-model` round-trips, and (c) a screenshot checklist per page. Frontend characterization beyond that isn't worth the cost.

## 8. Risks and decisions to make up front

| Risk | Mitigation |
|---|---|
| GDAL field mapping differences between the Kotlin interop and the `gdal` crate (e.g. string lists, booleans, Integer64, charset recoding) | P-IN-2 pins every feature in the fixture cell; run it on several real NOAA cells (one per `DSSI_NALL` charset) before trusting it. |
| `geo` boolean ops produce slightly different vertices than GEOS for `intersection`/`difference` | Compare P-TE-5 tiles by decoded geometry with a tolerance in tile pixels, not byte equality. Tile pixel rounding absorbs most of it. If not, use `geos` for the include-mask ops only. |
| MVT tag/value ordering differs → not byte-identical | Compare decoded tiles. Byte identity is not a contract; MapLibre doesn't care. |
| Admin signatures minted by one implementation must verify on the other during cutover | P-SEC-1 cross-vectors; same base64/URL-encoding choices; `jiff` RFC 3339 output must match `kotlinx.datetime`'s `Instant.toString()` (nanosecond trimming!). |
| Two implementations writing one schema | Freeze `DbMigrations` during the port. Any schema change goes into both or waits. |
| Fixtures freeze bugs as behaviour | Resolve D1–D9 in Phase 0; each fixture file gets a header comment naming the decision. |
| Kotlin's `Dispatchers.IO` + GDAL vs tokio | All GDAL work in `spawn_blocking`; GDAL datasets are `!Send` in the `gdal` crate, so open/iterate/close within one blocking task per chart. |
| Floating point formatting (`format()` in `LightDescription`, bearings, clearances, `Double.toString`) | Golden fixtures in P-SY-4/5; use `ryu`-based formatting deliberately matched to Kotlin's output. |

## 9. Rough sizing

| Area | Kotlin LOC today | Expected Rust LOC | Notes |
|---|---:|---:|---|
| Interop modules (`libgdal`, `libpq`, `libzip`, `libsqlite`, `libexpat`, `geojson`) | ~4,400 | ~600 | Mostly deleted; thin wrappers over crates |
| Symbology (`layers/*`) | ~7,500 | ~6,000 | Mechanical, fixture-driven |
| Server core (db, endpoints, ingest, export, workers) | ~4,000 | ~4,500 | |
| `enc_cron` | ~1,000 | ~800 | |
| Frontend | ~4,500 | ~4,000 | Rewrite |
| Phase 0 tests + fixtures (Kotlin) | — | ~3,000 Kotlin | The investment that makes the rest safe |

Phase 0 is the largest piece of new work and the only one that can't be parallelised with the port. Phases 2 (symbology) and 6 (frontend) are independent of each other and of Phase 3 once the fixtures exist.

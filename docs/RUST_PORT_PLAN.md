# Rust / Leptos Port Plan

_Written 2026-10-27, updated 2026-10-29 (§10: separate deployment environment), 2026-10-30 (deployment deferred to Phase 7). Companion to [TEST_COVERAGE_AUDIT.md](TEST_COVERAGE_AUDIT.md), whose test IDs (SY-3, TE-1, …) are referenced below._

## 0. Why

Njord started life in Rust as [s57tiler](https://github.com/manimaul/s57tiler). It was reinvented in Kotlin because Kotlin/Native plus Kotlin/JS let one person move fast with one language across server and browser. That trade-off has shifted:

- **Performance.** The tile hot path is CPU-bound geometry clipping and protobuf encoding. Kotlin/Native's garbage collector and the copying across its C interop boundary are measurable there, and Kotlin/Native is the slowest of Kotlin's backends. Rust gives predictable latency, no GC pauses, and zero-cost calls into GDAL and PostGIS clients.
- **Library availability and maturity.** Kotlin/Native has almost no server-side ecosystem, so this repo hand-maintains five C interop modules (`libgdal`, `libpq`, `libzip`, `libsqlite`, `libexpat`) plus an in-house GeoJSON model and MVT encoder. Rust has maintained, widely used crates for every one of those (§4), leaving GDAL as the only C dependency.
- **Container size and runtime.** The runtime image today pulls in `libgdal`, `libpq5`, `libzip`, `libssl`, `libcurl`, `libgd` and `libexpat`; a Rust build statically links everything except GDAL (§4.3). The build side also simplifies: no JDK, no multi-gigabyte Konan toolchain cache, and ARM64 cross-compilation for the Raspberry Pi `.deb` without the linker workarounds in `debpkg/Containerfile.arm64`.
- **Developer productivity is no longer the deciding factor.** Kotlin was chosen for how quickly a small team could write it. With AI-assisted development the cost of writing Rust has fallen sharply, and Rust's stricter compiler becomes an asset rather than a tax: when much of the code is machine-generated, a type system and borrow checker that reject whole classes of bugs are the cheapest reviewer available.
- **One language front and back is preserved.** Leptos compiled to WebAssembly keeps the shared-types-across-the-wire benefit (`njord-model`, §3.1) that justified Kotlin multiplatform, while the UI framework (Compose HTML) and the Kotlin/Native Linux targets have remained niche with an uncertain roadmap. Rust, Tokio, Axum and wasm32 are mainstream targets with large communities.

None of this is urgent; the Kotlin server works. The port is worth doing because every item above compounds over the project's remaining life, and the test-first approach below makes it low risk.

## 1. Goals

1. Re-implement the Njord backend in Rust and the web UI in Leptos, as **two separately built and deployed artifacts** that only meet at the HTTP API.
2. Replace the hand-written C interop modules (`libgdal`, `libpq`, `libzip`, `libsqlite`, `libexpat`, in-house `geojson`, in-house MVT encoder) with maintained crates wherever a pure-Rust crate exists.
3. Preserve every externally observable contract: the HTTP/WebSocket API, the PostGIS schema, the MapLibre style JSON, the mobile `.mbtiles` archive, the `enc_cron` ↔ server protocol, and the on-disk layout (`save/`, `ingest/`, `regions/`, tile cache, lock and status files) that Kubernetes and the deb package rely on.
4. Deploy the Rust stack as a **separate environment**: the `mxmariner` Kubernetes namespace, `https://njord.mxmariner.com`, and its own `njord` database on the existing managed Postgres cluster. The `njord` namespace / `openenc.com` deployment is not modified, scaled or re-pointed until it is deliberately retired (§10). Old and new run side by side as two complete stacks and are compared through the HTTP conformance suite; nothing is shared at runtime.

**Non-goals:** changing the S-52-ish symbology, the schema, or the mobile format during the port. Touching the `njord` namespace before §10.6 is also out of scope: the Kotlin deploy tooling keeps deploying Kotlin there. Any of those should be a separate change after the Rust server is at parity, when the conformance suite described below can catch regressions.

## 2. Why tests come first

The Kotlin code is the only specification of most behavior. The audit found that the parts that matter most — symbology (`preTileEncode`), tile assembly, ingest, admin auth — have almost no tests. Porting them without a safety net means re-deriving behavior from the Kotlin source and hoping.

The plan therefore front-loads **characterization tests whose expected values live in language-agnostic fixture files** (`testdata/`). The Kotlin implementation generates the fixtures; the Rust implementation must reproduce them byte-for-byte or value-for-value. Those same fixtures stay as regression tests after the Kotlin code is deleted.

Three layers of safety net, from cheapest to most complete:

| Layer | What it pins | Runs against |
|---|---|---|
| **Golden fixtures** (JSON / binary files under `testdata/`) | Pure functions: symbology, depth formatting, style JSON, tile math, MVT bytes, HMAC, S-57 metadata extraction | Either implementation's unit tests |
| **Database contract tests** | SQL behavior: migrations, chart overwrite, feature queries, region flags | Either implementation, shared PostGIS |
| **HTTP conformance suite** (black box, Python/pytest, takes one or two base URLs) | Every route's status codes, headers, JSON shape, decoded tile content | Kotlin server, then Rust server, then both live with a diff |

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
└── conformance/                # HTTP black-box suite (Python/pytest; see §5.5). Independent of both implementations
```

`njord-web` must **not** depend on any backend crate other than `njord-model`. That is the mechanical guarantee of frontend/backend separation; a check (`cargo tree -p njord-web`, run with the other local checks) enforces it.

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
| `DistributedLock` inotify interop | Watching the shared lock file | **none** (decided in Phase 4) | The port reads the few-byte lock file whenever it asks whether it still holds the lock (a few times per layer at most), which is never staler than a watch; `notify` was planned but a cached flag behind FSEvents/inotify buys nothing here. |
| Ktor server | HTTP, WebSocket, static files, CORS, forwarded headers, basic auth | **`axum`** + **`tower-http`** (`fs`, `cors`, `compression`, `trace`) | WebSocket via `axum::extract::ws`. Forwarded-header handling for `externalBaseUrl()` via `axum-client-ip` or a small extractor. |
| Ktor client (curl engine) | Downloads in `EncUrlHandler` and `enc_cron` | **`reqwest`** (`rustls-tls`, `stream`) | Set `.error_for_status()` so error bodies are never saved as `.zip` (audit **D9**). |
| `kotlinx.serialization` | JSON | **`serde`**, **`serde_json`** | `njord-model` holds every wire type once, with `#[serde(rename = "…")]` matching current field names exactly. |
| `kotlinx.datetime` / `kotlin.time` | Timestamps, signature expiry | **`jiff`** (or `time`) | `jiff` handles the local-zone archive timestamp stem round-trip (`RegionExporter.parseTimestampStem`) including DST. |
| `UUID` interop | Lock ownership, signature nonce | **`uuid`** (`v4`) | — |
| Logging | — | **`tracing`** + **`tracing-subscriber`** | — |
| Kotlin/JS + Compose HTML | Web UI | **`leptos`** (CSR) + **`leptos_router`** + **`wasm-bindgen`** / **`web-sys`** | See §7. |

### 4.2 The one that stays C: S-57 reading

There is **no mature pure-Rust S-57 / ISO 8211 reader** that applies `.001…` update files, assembles spatial primitives into feature geometries, and emits the exact attribute names GDAL does. Everything downstream — the `features.props` JSONB, `LNAM`/`LNAM_REFS`, the `METERS` sounding field, `DSID_*`/`DSPM_*` metadata, `SPLIT_MULTIPOINT` behavior, `RECODE_BY_DSSI` charset handling — is shaped by GDAL's S57 driver options:

```
RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,
SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON
```

`ADD_SOUNDG_DEPTH` is **not** in effect. The Kotlin constant `Gdal.OGR_S57_OPTIONS_V` reads `…,RECODE_BY_DSSI=ON:ADD_SOUNDG_DEPTH=ON`: the colon makes GDAL take `ON:ADD_SOUNDG_DEPTH=ON` as the value of `RECODE_BY_DSSI` (still true), so no SOUNDG feature has ever had a `DEPTH` field. Every fixture and every production chart was read without it; `njord_s57::S57_OPTIONS` reproduces the options GDAL actually applies (found 2026-10-30, Phase 2). Turning it on would be a data change, outside the port.

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
| P-SY-4 | `symbology/display_depths.json` | `displayDepths` for 0.0…31.0 in 0.1 steps plus drying heights. D1 is fixed and pinned by `libgdal` `SoundingsTest`; generate the fixture from the fixed code. Sign handling is decided: drying heights keep negative parts in the props and the style applies `abs` plus the SOUNDSA1 underline (`SoundgTest`), so the port must reproduce both the parts and the style expressions. | SY-1, SY-2 |
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

In Kubernetes the Rust server gets its own database (§10), but the local Phase 3 diff runs the Kotlin ingest and the Rust server against one database, and the deb package upgrades a Kotlin-provisioned database in place. So these are plain SQL-behavior tests both implementations must pass against the same PostGIS schema.

| ID | Test | Audit ref |
|---|---|---|
| P-DB-1 | `DbMigrations`: empty DB → current version; v1 DB → migrated; schema dump of fresh vs migrated identical. The Rust migration runner must read and write the same `meta_data.version` row so a database provisioned or migrated by either implementation is accepted by the other (the local diff runs, deb upgrades). | DB-1 |
| P-DB-2 | Chart overwrite semantics; delete cascades; unknown name → false | DB-2, DB-3 |
| P-DB-3 | `findAllChartFeaturesAsync4326` / `findChartFeaturesAsync4326` row shape and clipping on the fixture cell (store as `db/feature_queries.json` with WKT) | DB-4 |
| P-DB-4 | TOPMAR association queries | DB-5 |
| P-DB-5 | Catalog paging and editions key (already tested; make the fixtures shared) | existing |
| P-DB-6 | Region flags: `regionNeedsRebuild`, `markRegionExported`, `clearRegionExportState`, `regionsContainingChart` (already partly tested) | DB-11 |
| P-DB-7 | `Dao.sqlOpAsync` retry/backoff semantics as a spec table (`db/retry.json`) so the Rust pool/retry wrapper matches: null-connection retries, non-retry on cancellation, max 7 | DB-13 |

### 5.5 HTTP conformance suite (`conformance/`)

Black box, written once in **Python (pytest)**, parameterised by a base URL. It is the one test layer that must be independent of *both* implementations: a suite written in Rust would share `serde`/MVT crates with the code under test, a suite in Kotlin would share the model classes, and either could pass on a shared bug. `hurl` was considered and rejected because it cannot decode protobuf tiles or drive the WebSocket route. Python is already the repo's operator language (`data/post_noaa_regions.py`, `data/upload_noaa_region_encs.py`, `data/walk_tiles.py` drive `/v1/admin`, `/v1/enc_url`, `/v1/ws/enc_process` and the tile routes with the standard library alone) and already runs in the cluster (`k8s_deploy/walk_tiles_cronjob.yaml` uses `python:3.12-slim`), so it adds no new toolchain.

Validated 2026-10-30 on Python 3.14 (floor is 3.12, matching the cluster image): `pytest` 9.2, `requests` 2.34, `websockets` 17.3, `mapbox-vector-tile` 2.2 (pulls `protobuf` and `shapely`) install cleanly and an MVT tile encodes and decodes to a stable, sortable dict. No GDAL, no JVM, no `cargo` is needed to run the suite.

Layout and tooling:

```
conformance/
├── requirements.txt            # pinned: pytest, requests, websockets, mapbox-vector-tile
├── conftest.py                 # --base-url, --compare-url, --record, --golden-dir options; session fixtures
├── lib/
│   ├── client.py               # requests.Session with base URL, admin login (GET /v1/admin, basic auth), signed calls
│   ├── mask.py                 # volatile-field masking by JSON pointer; host normalisation to "{BASE}"
│   ├── tiles.py                # gunzip if needed, mapbox_vector_tile.decode, canonical sort (layer, id/LNAM, props)
│   └── ws.py                   # websockets.sync client for /v1/ws/enc_process, collects the WsMsg sequence
├── test_api_01_about.py …      # one module per P-API-n row below
└── golden/<route>.json         # recorded, masked bodies; tiles as decoded JSON + sha256 of the raw bytes
```

Three modes, selected on the `pytest` command line:

| Mode | Invocation | Use |
|---|---|---|
| **record** | `pytest conformance --base-url=$URL --record` | Against the Kotlin server; rewrites `golden/`. |
| **verify** (default) | `pytest conformance --base-url=$URL` | Against either server; fails on any difference from `golden/`. |
| **diff** | `pytest conformance --base-url=$A --compare-url=$B` | Two live servers (local Kotlin vs local Rust, or §10.5 `openenc.com` vs `njord.mxmariner.com`); fetches both, masks both, asserts equal. No golden files involved, so catalog drift between environments shows up as a per-route failure rather than a stale fixture. |

Comparison rules: JSON bodies are compared after masking with keys sorted; tiles are compared **decoded**, after the canonical sort, because two correct MVT encoders may legally order fields and layers differently. The raw-byte sha256 is recorded and reported, but byte equality is informational, not a gate (the byte-exact MVT contract lives in P-TE-2 / P-TE-5 unit fixtures). The suite reuses `testdata/` only to read the expected tile list for P-API-3 and the style JSON for P-API-2; it never imports Kotlin or Rust code.

Run against Kotlin first to record, then against Rust to verify, then both live in diff mode. In production the two base URLs are `https://openenc.com` and `https://njord.mxmariner.com` (§10.5); both environments ingest the same NOAA catalog, so once the new database has caught up the bodies and decoded tiles should match apart from the masked volatile fields and the host name.

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

Record responses as `conformance/golden/<route>.json` with volatile fields (timestamps, uuids, durations) masked by JSON pointer and the base URL replaced by `{BASE}`; the masking list is part of the fixture so a new volatile field is a visible decision, not a silent skip.

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

The lock file, status file and worker loops are the inter-process protocol between replicas (and between server and `enc_cron` via `save/`). They cannot be golden-filed; write them as behavioral tests with the semantics decided up front:

| ID | Decision to record, then test | Audit ref |
|---|---|---|
| P-CC-1 | Lock file format (`uuid` text), acquire = atomic rename of a temp file, clear = rename of an empty file. **Resolve D6**: may a non-owner clear? Does an upload abort an in-flight ingest? | CC-1, CC-2 |
| P-CC-2 | `IngestStatus` file: atomic write, JSON `WsMsg`, write only while holding the lock | CC-4 |
| P-CC-3 | Worker cadence: ingest poll 5 s; region worker poll 5 s / idle 15 min / `wake()` | RE-9, IN-7 |

## 6. Port phases

Each phase ends when its conformance/fixture tests pass in Rust. No phase starts porting code whose fixtures don't exist yet.

### Phase 0 — Characterization (Kotlin repo)
- [x] **Phase 0 complete** (2026-10-30). One follow-up remains: re-run the conformance suite against `https://openenc.com` after its next deploy (see the checklist).
Tracked in [RUST_PORT_PHASE0_CHECKLIST.md](RUST_PORT_PHASE0_CHECKLIST.md).
- Write the §5 tests in Kotlin; make them emit `testdata/`.
- Regenerate and validate the fixtures on every change (`./gradlew allTests` against the dev PostGIS, then regenerate and diff `testdata/`). No hosted CI: these run locally (checklist §4).
- Fix or formally accept each suspected defect (D1–D9) **before** fixtures are frozen. Every fixture is a decision about which behavior the port must copy. Done 2026-10-30: D1 fixed, D2–D9 accepted in Kotlin and fixed in the port (TEST_COVERAGE_AUDIT.md §3.1).
- Exit: `testdata/` committed; conformance suite green against the Kotlin server.

### Phase 1 — Workspace and the contract crate
- [x] **Phase 1 complete** (2026-10-30). Root `Cargo.toml` workspace, `crates/njord-model`, Local checks before committing: `cargo fmt --all --check`, `cargo clippy --workspace --all-targets -- -D warnings`, `cargo test --workspace`, `cargo check -p njord-model --target wasm32-unknown-unknown` (and since Phase 6 `cargo clippy -p njord-web --target wasm32-unknown-unknown -- -D warnings`).
- `njord-model`: every wire type (`Chart`, `ChartCatalog`, `ChartEditions`, `FeatureRecord`, `LayerQueryResultPage`, `RegionManifestEntry`, `AdminSignature`/`AdminResponse`, `EncUpload`, `EncUrlRequest`, `WsMsg`, `Style`/`Layer`/`Paint`/`Layout`, `S57Object`, `About`, `TileJson`, `Depth`, `ThemeMode`, `Sprite`, `Color`).
- Serde round-trip tests against `testdata/` JSON (P-IN-7, P-SY-7 types, P-API golden bodies).
- Exit: `cargo test -p njord-model` deserialises every golden response and re-serialises it identically.
- Done 2026-10-30:
  - `tests/golden.rs` round-trips all 68 JSON bodies in `conformance/golden/`, each through the type its route returns. A golden whose route has no mapped type fails the test. Masked values are swapped for typed placeholders first. Request bodies are checked too, including that `chart_post_invalid` is rejected.
  - `tests/testdata.rs` covers `ws/messages.json`, all 27 `symbology/style/` files, `sprites.json` (including the `Sprite` declaration order), `regions/manifest.json`, `s57/chart_insert.json` and `admin/signature_vectors.json`. The admin test is byte-exact: base64 of the serialised `AdminSignature` must equal Kotlin's `signatureEncoded`.
  - "Identically" means the same JSON values with keys in the same order, and integers kept distinct from floats (except inside GeoJSON, see below). Fixtures that Kotlin wrote with `explicitNulls = false` (manifest, chart insert) are compared after dropping nulls.
  - GeoJSON is the georust `geojson` crate (1.x, without its `geo-types` feature; §4.1). It reads Kotlin's output, but it doesn't write the `"id": null` / `"bbox": null` members or the member order of the Kotlin `geojson` module. Nothing on the wire depends on those, so the round-trip tests ignore member order and `null` members inside GeoJSON objects only. One quirk: `"crs": null` on a Kotlin-written geometry is kept as a foreign member and written back out.
  - Decision: `kotlin.time.Instant` is `jiff::Timestamp` with a formatter that pads the fraction to 0/3/6/9 digits as Kotlin does (`njord_model::instant`). The admin HMAC signs that exact text.
  - Not modelled, because they are server-internal and never on the wire: `ChartInfo`, `ChartFeature` (WKB byte arrays) and `LayerGeoJson`. `Theme` has no serde impl because Kotlin never serialises it.

### Phase 2 — Pure libraries
- [x] **Phase 2 complete** (2026-10-30). `crates/njord-geo`, `crates/njord-s57`, `crates/njord-symbology`, plus `crates/njord-testdata` (the shared fixture helpers, a dev-dependency only).
- `njord-geo`: tile system, clip polygons, MVT encoder (`geozero`/`mvt`), prop filtering. Pass P-TE-1…4.
- `njord-symbology`: port the 128 `Layerable`s and attribute helpers. Pass P-SY-1…9. Suggested order: `Layerable` base helpers → `Depare`, `Soundg`, `Obstrn`, `Wrecks`, `Uwtroc`, `Lights`, `Topmar` (tier-0 symbology) → buoys/beacons → everything else via P-SY-2 generated cases.
- `njord-s57`: `gdal` crate reader behind `trait S57Reader`; shapefile reading via `shapefile` crate. Pass P-IN-1…4.
- Exit: all §5.1–5.3 fixtures pass natively.
- Done 2026-10-30. Every §5.1–5.3 fixture passes except P-TE-5 (golden tiles) and P-IN-5 (DB state after ingest). Those two need PostGIS and the `addCharts` / insert path, so they moved to the Phase 3 exit.
  - **`njord-symbology`** (P-SY-1…7, 9; P-SY-8 is in `njord-model`):
    - All 130 registered layerables, one module per Kotlin file, behind a `Layerable` trait and `LayerFactory`. The 35 Kotlin classes that `StandardLayers` never registers have no Rust module.
    - Every P-SY-1 case and every one of the 5163 P-SY-2 cell features matches with no tolerance.
    - All 27 P-SY-7 styles are byte-for-byte the same JSON document, layer ids included. Ids are numbered per style generation, which reproduces the fixtures.
    - Pure Rust; builds for `wasm32`.
  - **`njord-geo`** (P-TE-1…4):
    - The tile system is bit-identical to Kotlin. The exceptions are the 127 z ≥ 19 cases where Kotlin's `Int` overflows (D10); they are sanity-checked instead.
    - The MVT encoder is hand-written on a small protobuf writer, because neither `geozero` nor `mvt` reproduces Kotlin's clipping, simplification, ring orientation and minimum sizes. All 9 P-TE-2 cases decode to the fixture view, and 8 of 9 are byte-identical.
    - Geometry ops use the pure-Rust `geo` crate. 153 of the 155 run `ogr_geometry.json` cases match. Accessor-only, WKB and `union` ops are skipped with a reason each, and 2 GEOS-specific outputs are recorded as divergences.
  - **`njord-s57`** (P-IN-1…4, 6):
    - 5177/5177 features, plus every chart-insert, layer-count, update-cell and Natural Earth case.
    - Built on the georust `gdal` crate, behind `trait S57Reader`. It is pinned to master commit `c0a6266`, because the released 0.19 does not compile against GDAL 3.13; move to the first release that includes it. That commit has prebuilt bindings for GDAL 3.8–3.13, so no bindgen is needed for Homebrew's 3.13 or Debian 13's 3.10. Every GDAL call is in `ogr.rs`. The only raw `gdal-sys` use is the field-subtype lookup the safe API lacks, plus a documented `Send` impl for owned geometries.
    - Shapefiles also go through GDAL. Ingest needs GDAL's `make_valid` anyway, and the fixture's field types come from GDAL.
    - The S-57 driver options are the ones GDAL actually applies (§4.2).
    - D7 has no `port.expected` case, so it is pinned by a unit test over an in-memory `S57Reader`.
  - **Expected P-TE-5 differences in Phase 3**, from `geo` versus GEOS:
    - `make_valid` keeps only polygons.
    - Line simplification is plain Douglas–Peucker rather than topology-preserving.
    - Boolean ops snap to a grid, about 1e-6 px at z16.
    - Ring start vertices differ.
    - Compare with `njord_geo::mvt::view::decode_to_json` plus ring-start normalisation (`njord-geo/tests/mvt.rs`).
  - **Resolved:** **D13**, decided 2026-10-30 (TEST_COVERAGE_AUDIT.md §3): Kotlin drops a MultiPoint feature from MVT tiles but still emits its layer. The `multi` fixture now carries `port.decoded`, and the port encodes the feature. `FixtureStore.binary` gained the `port` argument for this.

### Phase 3 — Read-only server, side by side
- [x] **Phase 3 complete** (2026-10-30). `crates/njord-db`, `crates/njord-server`; `Containerfile.rust` and `deploy/mxmariner/` prepared, not applied.
- `njord-db` (sqlx) with the migration runner reading the same `meta_data` version. Pass P-DB-1, 3, 4, 5, 6.
- `njord-server` read routes: `about`, `style`, `content`, `icon`, `sector_svg`, `tile_json`, `tile`, `chart`, `chart_catalog`, `chart_editions`, `feature`, `geojson` GET, `regions` GET, `regions/{archive}`, `cache` GET.
- Tile cache: same on-disk layout under `chartTempData` so the deb package and the operational docs stay valid. In Kubernetes each environment has its own PVC; nothing is shared between namespaces.
- Locally, run both servers against the same dev PostGIS and run the conformance suite in diff mode (`pytest conformance --base-url=http://localhost:9000 --compare-url=http://localhost:9001`), which compares masked bodies and decoded tiles (P-TE-5) between the two live servers.
- No deployment (decided 2026-10-30): everything is tested locally first, and the `mxmariner` environment is stood up in Phase 7. `Containerfile.rust` and `deploy/mxmariner/` were prepared in this phase and are not applied.
- Exit: conformance suite green on read routes locally, in diff mode (Kotlin and Rust against one database) and in verify mode against the Rust server. P-TE-5 golden tiles and P-IN-5 DB state pass natively (moved here from Phase 2).
- Done 2026-10-30:
  - **`njord-db`:**
    - Every fixture passes with no tolerance: P-DB-1 (fresh and migrated v1 schema), P-DB-3, 4, 5, 6, 7, D5 `overwrite.json`, and P-IN-5 for all three cells.
    - Runtime sqlx 0.9 queries. Geometry is read as `st_asbinary` and decoded with geozero into `geo-types`, so the SQL stays Kotlin's.
    - Retry follows D5: only acquiring a connection or `BEGIN` is retried, never a statement. An overwrite is one transaction, and a feature install commits once, refusing to report a count after an aborted transaction.
    - Chart and feature inserts are ported too (Phase 4 wraps them), because P-TE-5 and P-IN-5 need real data.
    - The migration runner reads and writes Kotlin's `meta_data.version` row. It takes a PostgreSQL advisory lock that does not exclude a Kotlin instance's file lock, so don't start both on an unmigrated database at once.
  - **`njord-server` (axum):**
    - Every read route, plus the tile encoder (`addCharts`) and the tile cache in Kotlin's on-disk layout.
    - D8: an out-of-range tile is an empty 400.
    - D14: `geojson` serves layers that have features.
    - Mutating routes answer 401 without a valid signature, as Kotlin does, and 501 with one until Phase 4.
    - `about/version` reports `0.1.0-rust` (the suite auto-detects the implementation from it) and `gdalVersion` `"none"` until Phase 4 links GDAL.
    - Icons are byte-identical to Kotlin's libgd output through `libz-sys` zlib, a second C library beside GDAL.
  - **P-TE-5:** all 46 golden tiles pass. 18 features differ by simplification within 0.1 px (tolerance 0.5 px), because `geo` and GEOS start rings at different vertices. Any missing or unstable feature fails.
  - **`njord-geo` fixes found along the way:**
    - Encoding was non-deterministic, because `geo`'s polygon repair iterates a randomly seeded `HashSet`.
    - Closed lines are now clipped and split the way GEOS splits them.
    - Touching points are kept.
    - Rings that round to zero area are dropped.
  - **Conformance suite:**
    - **Seeding:** the seed now uploads the `.TXT` sidecars, which it had silently left out. Re-recording changed only `chart.json`.
    - **Comparison rules:**
      - ring starts and part order are canonicalised;
      - tile vertices are compared within 0.5 px;
      - `?info=true` coordinates are rounded to 7 decimals, and ring direction is accepted either way;
      - GeoJSON `null` members are dropped on both sides, since the port does not write Kotlin's nulls.
    - **Port blocks:** fixed, and applied in diff mode against a Rust compare server.
    - **`port_pending`:** a new marker that skips Phase 4 tests against the Rust server.
  - **Final local runs** (Kotlin :9000 and Rust :9001 on one database seeded through Kotlin):
    - diff: 210 passed, 0 failed, 24 skipped;
    - verify against Rust: 201 passed, 0 failed, 33 skipped.
  - **Open:** five region-archive tests skip on Rust only because the suite gets signatures from `GET /v1/admin` (Phase 4). The route itself is ported.

### Phase 4 — Write path and workers (FM)
- [x] **Phase 4 complete** (2026-10-30). `njord-server` now holds the whole server: every mutating route, the ingest and region export workers, and the lock and status files.
- Admin signatures (P-SEC-1/2, cross-compatible with Kotlin-minted signatures), `enc_save`, `enc_url`, `chart` POST/DELETE, `geojson` POST, `cache` DELETE, `nuke`, `regions` POST, WebSocket status.
- `ChartIngestWorker` / `ChartIngest` / `NaturalEarthIngest` on `spawn_blocking`; lock + status files per P-CC-*.
- `RegionExporter` + worker with `rusqlite`. Pass P-RE-1…5.
- Exit: P-API-7…10, P-RE-*, P-CC-* green. Locally, the Rust server ingests (P-API-8, the suite's seed) and exports regions without any Kotlin binary.
- Done 2026-10-30:
  - **Routes** (`handlers/`): `GET /v1/admin` mints (`Verifier::mint`; P-SEC-1 `signature_vectors.json` byte-exact, `signatureEncoded` encoded as Ktor's `encodeURLPath(encodeSlash = true)`: `+` and `=` kept, `/` as `%2F`), `POST /v1/admin/verify`, `POST/DELETE /v1/enc_save` (D2: a bare `.zip` name or 400; D6: the upload no longer clears the lock), `POST /v1/enc_url` (D9: `error_for_status` and a zip-magic check before the rename), `POST/DELETE /v1/chart`, `POST /v1/geojson`, `DELETE /v1/cache`, `DELETE /v1/nuke`, `POST /v1/regions`, and the `/v1/ws/enc_process` socket (500 ms poll; an unsigned client is upgraded and closed, as Kotlin's `requireSignature` inside the socket behaved, so the suite sees a clean close). A body Ktor could not decode keeps Ktor's 400 text (`chart_post_invalid` golden). axum's default 2 MB body limit is disabled: uploads are whole ENC bundles.
  - **Lock** (`lock.rs`, P-CC-1): Kotlin's file protocol unchanged, so a Kotlin-provisioned `lock` is upgraded in place; D6's 1500 ms settle re-read after the rename; a non-owner may clear. `acquired()` reads the file rather than caching a watched flag (§4.1 table updated; `notify` is not used).
  - **Status file** (`ingest/status.rs`, P-CC-2): atomic temp-and-rename; `write` only under the lock, `write_ignore_lock` for the upload and reset routes. `init_if_needed` writes `Idle` on a volume without a status file (Kotlin's went through the lock-conditional write and never did; the socket answered `Idle` for the missing file either way).
  - **Ingest** (`ingest/`): the worker claims one zip per lock cycle (`save/<zip>` → `ingest/<uuid>/`), 5 s poll; after an ingest it re-checks `save/` at once and announces `Extracting(0)` before taking the lock, so a client that connects between two queued ingests never reads the previous completion report as the current one. Cells are read one layer at a time on the blocking pool (GDAL datasets are `Send`, not `Sync`) and inserted async, up to `chartIngestWorkers` cells at once. D3: an unsafe zip entry aborts the whole extraction, nothing is written, the status is a fatal `Error`. D5: one transaction per chart, counted after `COMMIT`; rows inserted but not yet committed are `pending` so the progress message still moves. The P-API-8 report of the fixture cell is reproduced exactly (5163 features counted, 5158 installed, the geometry-less `C_AGGR`/`C_ASSO` skipped). Natural Earth zips go to `base_features` as before.
  - **Region export** (`regions/export.rs`, `regions/worker.rs`, P-RE-1…5): `rusqlite` with the bundled SQLite (no `libsqlite3` in the image). The DDL, every `metadata` row (Kotlin's `Double.toString` for bounds and center), the 116-tile coordinate set, the chart rows and the pruned `chart_tiles` edge counts of `archive.json` match with no tolerance; every archive tile equals the live tile and the committed P-TE-5 golden under the P-TE-5 comparison. The worker keeps Kotlin's cadence (5 s / 15 min / wake). Tiles of a batch render four at a time; the order written is `(z, x, y)` as before.
  - **Tests** (`njord-server/tests/`): `signature.rs` (P-SEC-1/2), `lock.rs` (P-CC-1/2), `security.rs` (P-SEC-3 upload names and zip entries with their `port` values), `regions.rs` (P-RE-4/5), `export.rs` (P-RE-1…3 on the seeded database), `ingest.rs` (the worker end to end on its own database: the fixture cell, a re-ingest, a zip with a broken cell, Natural Earth, a hostile zip, a lock held elsewhere), and the conformance replay now covers `api_06` minting/verify, `api_08` and the standalone `api_09`/`api_10` mutations (the insert/read/delete sequence, archives and nuke are the live suite's). `cargo test --workspace` green against the dev PostGIS.
  - **Live run, Rust alone** (release build on :9001, a fresh `njord_rust_live` database, `useTileCache: true` as the goldens were recorded): the suite seeds through `POST /v1/enc_save` and the socket, waits for `REGION_04` and `WORLD` to render, and runs every test — nothing is `port_pending` any more. **234 passed, 0 failed, 0 skipped** (verify mode, 2 min 4 s). The only failure of the first attempt was configuration: with the shipped `useTileCache: false` the `cache_get` golden (recorded with the cache on) sees no counts.
  - **Decided along the way:** `/v1/ws/enc_process` answers 405 to other verbs like every other route (Ktor's exact answer for a verb on a socket route is not pinned by any golden). `parseTimestampStem` accepts a stem that already holds colons, as Kotlin's regex-then-parse did (found by P-RE-4). The `__MACOSX` skip is the ingest's rule, not the zip writer's, as in Kotlin (found by P-SEC-3).

### Phase 5 — `enc_cron`
- [x] **Phase 5 complete** (2026-10-30). `njord-enc-cron` replaces `enc_cron.kexe`: same CLI (`[resourcesDir] [--from-file <catalog.xml>] [--dry-run]`), same `enc_cron.json` + `ENC_CRON_OPTS` + `ENC_CRON_ADMIN_USER/PASS` layering, same log lines.
- Port with `quick-xml`, `reqwest`, `zip`. Pass P-EC-1…5, and run it locally against the Rust server (it needs only the save directory, `GET /v1/chart_editions` and `DELETE /v1/chart`).
- Done 2026-10-30:
  - **Catalog** (`catalog.rs`, P-EC-1): `EncProdCatParser`'s path-tail rules unchanged, driven by `quick-xml` 0.42 with prefixes matched literally and the root's `xmlns` / `xmlns:gco` bindings asserted. The network path streams the body (`reqwest` `bytes_stream` → `StreamReader` → `read_event_into_async`), so the 52 MB document is never held. expat failed a truncated document at `finish()`; `quick-xml` just stops at EOF, so the parser fails a document that ends with elements open (or has no root), which keeps the retry-the-whole-fetch semantics. The fixture is checked three ways: from the file, through a reader that hands out 4096 bytes at a time, and through the async driver. D15 (found by the live run, 2026-10-31): the trailing `seriesMetadata` record is the catalog's own and is no longer counted as a seen-and-skipped cell, so the nightly `skipped N of M records` warning now means a real broken cell record; `parsed.json` carries the port's 5/1 as `port.expected`.
  - **Selection** (`select.rs`, P-EC-2), **config** (`config.rs`, P-EC-4: defaulted fields are skipped when serialised at their default, which is the form kotlinx recorded; a missing file still fails on the required fields) and **orphan deletes** (`run.rs`, P-EC-5: every refusing guard before the signature fetch) match their tables with no divergence.
  - **Bundles** (`bundle.rs`, P-EC-3): only `ENC_ROOT/<CELL>/…`, written sorted, published by rename into `save/`. D3: every entry name of a cell zip is checked (`enclosed_name`, no backslash) before anything is extracted, and one unsafe name fails the whole cell, which is then skipped like a failed download (the fixture's `port` expectation). The all-failing case keeps Kotlin's leftover empty `download/` directory, which the fixture records.
  - **HTTP** (`transport.rs`): `reqwest` with rustls, `error_for_status` on every call as Ktor's `expectSuccess` did, the request timeout from config and a 60 s read timeout, Kotlin's retry/backoff (`2 s × attempt`). Wire types come from `njord-model` (`ChartEditions`, `AdminResponse`), so the admin answer is now parsed in full rather than for `signatureEncoded` alone. `202` from `DELETE /v1/chart` is "deleted", `204` "already gone", as before.
  - **Live run** (release builds; the Rust server on :9001 with a fresh database, `chartTempData` shared with the cron, a local HTTP server standing in for NOAA with a hand-written catalog and a NOAA-layout zip of the US5WA22M fixture): (1) Njord empty → one bundle of 9 files published, ingested by the Rust worker, `/v1/chart_editions` answers `4:20190319:20200626`; (2) a catalog with that key → "all cells up to date"; (3) a catalog without US5WA22M → reported as an orphan, nothing deleted without credentials, deleted through the signed `DELETE /v1/chart` with them; a cell whose zip 404s is retried, skipped, and the run still exits 0. Against NOAA's real catalog (`--dry-run`): 7297 cells, peak RSS 15 MB; the one skipped record was the trailing `seriesMetadata` (D15, now not counted). Njord unreachable exits 1, for the CronJob's `OnFailure`.
  - **Left for Phase 7** (deployment is deferred there): `Containerfile.rust` does not build or ship `njord-enc-cron` or `enc_cron/src/nativeMain/resources` yet; add both (e.g. `/opt/njord/njord-enc-cron` and `/opt/njord/enc_cron_resources`, the path the `njord-enc-cron-config` mount expects), then flip `NJORD_ENC_CRON_IMPL=rust`, the `enc-download` command in `deploy/mxmariner/noaa_enc_daily_cron.yaml` and the guard line in `deploy.sh resolve_images`.

### Phase 6 — Leptos frontend
- [x] **Phase 6 complete** (2026-10-31). `crates/njord-web` replaces `web` + `shared_fe`: Leptos 0.8 CSR, built with Trunk (`trunk build --release` in `crates/njord-web` → `dist/`, served as `webStaticContent`).
- See §7. Exit: route parity with `shared_fe/routing`, every API call typed through `njord-model`, manual QA on the map.
- Done 2026-10-31:
  - **Routing** (`routing.rs`): `NjordRoute`, `RouteMatcher` and `RouteRegistry` ported line for line (`regex-lite`), not `leptos_router`, so the matcher's quirks (`/chart` matching with an empty name, a trailing `#`, the unanchored `groups`) stay exactly as bookmarked URLs expect. `tests/routes.rs` passes all 54 cases and the route list of `testdata/frontend/routes.json` natively. History is driven by hand (`pushState`/`replaceState`, `popstate`) as `RouteViewModel` did.
  - **State**: the Kotlin view-model singletons are one set of app-wide signals (`store.rs`), created once at mount. Loads start the first time a page needs them (catalog, S-57 catalogue + colours, sprites), so each page issues the `/v1` requests the Kotlin page did. `Async<T>` and its error texts (`401 - Unauthorized`, `<status> - Network status`) are kept.
  - **Dependency rule**: `njord-web` depends on `njord-model` and no other backend crate (`cargo tree -p njord-web -e normal`). For that, `build_sector_svg` moved from `njord-symbology` to `njord_model::sector_svg` (symbology re-exports it); the UI draws missing `sector_*` images with it, as `ChartController` did.
  - **JavaScript**: MapLibre GL 4.7.1 and Bootstrap 5.3.3 (the versions in `gradle/libs.versions.toml`) are vendored unmodified under `crates/njord-web/vendor/`, so the build needs no npm and the UI works offline. `maplibre.rs` and `bootstrap.rs` bind the calls the UI makes.
  - **Compatibility**: `localStorage` keys and encodings are the Kotlin ones (`MapLocation`, `Depth`, `AdminResponse`, and `Theme` in kotlinx's array polymorphism), so an in-place upgrade keeps a viewer's position, theme and login. This was confirmed live: the Leptos UI picked up the depth unit and admin login the Kotlin UI had stored. Text the Kotlin UI printed through `toString()` (`Duration`, `WsMsg` data classes, enum names) is reproduced in `kotlin.rs`, with unit tests.
  - **Decided along the way** (fixes of Kotlin UI behaviour):
    - Navigating carries only the target's own query string. Kotlin kept the query of the page being left, so `/enc?lat=…` followed by "About" went to `/?lat=…`.
    - Chart deletes go to `DELETE /v1/chart`. Kotlin requested `/v1//chart`, which Ktor routed and axum does not.
    - The chart-query modal derives the theme's colours from the loaded colour table. Kotlin computed them only when the theme changed, so they could stay unset.
    - A failed upload (non-2xx `POST /v1/enc_save`) shows the error with a reset. Kotlin failed to decode the body and stayed on the progress bar.
    - Nav dropdown items `preventDefault`, so picking one no longer appends `#` to the URL.
    - A pending "ENC Zoom" from the chart page fits the bounds on the map's `load` event. Fitting right after creating the map landed at the wrong zoom, because the container had not been laid out yet.
  - **Live QA** (release build; Rust server on :9000 against `njord_rust_live`, Chromium): every route and control-panel tab; the map at z14 (soundings, light sectors from `styleimagemissing`); the feature-query modal and its accordion (chart summary, colour swatches, attributes); chart page → "ENC Zoom" (bounds and coverage highlight); layer page paging and "ENC Zoom" (point highlight); depth / theme switching (style reload, scale units); the region overlay and the admin region editor (drag, context menu, insert, exit); the catalog filter; an upload of the US5WA22M fixture through to the `CompletionReport` and Reset; not-found page and back button. No console errors.
  - **Open:** the side-by-side capture of [FRONTEND_SCREENSHOT_CHECKLIST.md](FRONTEND_SCREENSHOT_CHECKLIST.md) against the Kotlin UI (pixels and network logs, 1440 and 390 widths) has not been done; QA above was functional, against the Rust server only.
  - **Left for Phase 7:** `Containerfile.rust` still copies the Kotlin/JS build into `/opt/njord/resources/www`. Build the UI with Trunk in the builder stage instead (needs the `wasm32-unknown-unknown` target and `trunk`) and copy `crates/njord-web/dist`.

### Phase 7 — Cutover and removal
- [ ] **Phase 7 complete**
- [x] Containerfile → multi-stage `rust:` builder, runtime with `libgdal` only (`Containerfile.rust`, prepared in Phase 3). Update `k8s_deploy`, `debpkg`, `RELEASE_NOTES.md`.
  - Done 2026-10-31. `Containerfile.rust` became the root `Containerfile` and `deploy/mxmariner/containerignore` the root `.containerignore`, so a plain `podman build .` uses the right context whitelist; `deploy.sh build`, `deploy/mxmariner/README.md`, `README.md` and `RELEASE_NOTES.md` point at them. The runtime stage installs `ca-certificates`, `libgdal36` and `zlib1g` only. `k8s_deploy/` is gone (its replacement is `deploy/mxmariner/`) and `debpkg/` was rewired with the Kotlin removal below. Checked with a `linux/arm64` build on the dev Mac; the `linux/amd64` image is built when `mxmariner` is stood up.
- [ ] Stand up the `mxmariner` environment (§10.4) from `deploy/mxmariner/`, with the Rust image for every workload (`njord-chart-dep`, `njord-ingest-rs`, `njord-enc-download`, `njord-walk-tiles`), and seed it from NOAA. Run the conformance suite in diff mode against `https://openenc.com` and `https://njord.mxmariner.com` (§10.5) until it is green apart from the expected catalog differences. This is the first deployment of any Rust code.
- [ ] Cutover per §10.6: switch the `openenc.com` consumers to `njord.mxmariner.com`, move regatta's DB relay, turn `openenc.com` into a redirect, retire the `njord` namespace. This is the first point at which anything in `njord` changes.
- [x] Delete Kotlin modules once the conformance suite has run green against Rust alone for a release cycle. Keep `testdata/` and `conformance/`.
- Kotlin removal done 2026-10-31, **ahead of the release-cycle condition** (decided 2026-10-31): the Rust stack is not deployed yet. On this branch only; `master` keeps the Kotlin code and remains the source of the `openenc.com` (`njord` namespace) deployment, and of any Kotlin hot-fix, until the cutover.
  - **Deleted:** every Gradle module (`server`, `shared`, `shared_fe`, `web`, `libgdal`, `libpq`, `libzip`, `libsqlite`, `libexpat`, `geojson`, `enc_cron`, `test_fixtures`), `buildSrc`, the Gradle wrapper and build files, `kotlin-js-store`, `.java-version`, the Kotlin `Containerfile` / `.containerignore` / `container/` and `k8s_deploy/`. Also `testdata/tools/check_routes_fixture.py` and `test_results.py`, which only served the Kotlin tests.
  - **Moved:** `server/src/nativeMain/resources` → `crates/njord-server/resources`, `enc_cron/src/nativeMain/resources` → `crates/njord-enc-cron/resources`. Tests that read `libgdal/src/nativeTest/resources/ne_110_extract` use the identical copy in `testdata/fixtures/`. `README_RUST.md` became `README.md` (`CLAUDE.md` links to it); `enc_cron/README.md` became `crates/njord-enc-cron/README.md`.
  - **Fixtures are frozen.** With the generators gone there is no regenerate mode; `testdata/README.md` says how a deliberate behaviour change edits them. `cargo test --workspace` and clippy green against the dev PostGIS after the move.
  - **`njord-ingest`:** a second `njord-server` binary, the port of `IngestApp.kt`: the ingest and region export workers without HTTP, ignoring `enableIngestion`. `njord-ingest-rs` needed it to run the Rust image.
  - **Deployment files rewired to Rust only** (the flips Phases 3–6 left for here; nothing applied): `Containerfile.rust` builds the UI with Trunk and ships `njord-server`, `njord-ingest`, `njord-enc-cron`, `/opt/njord/resources` and `/opt/njord/enc_cron_resources`. In `deploy/mxmariner/`, every workload renders `${NJORD_IMAGE}`. `NJORD_KOTLIN_IMAGE`, `NJORD_INGEST_IMPL` and `NJORD_ENC_CRON_IMPL` are gone, `deleteOrphans` is `true`, and any `njord-chart-server` image in a rendered manifest is refused. `debpkg/` builds from Cargo and Trunk. The ARM64 `.deb` builds natively in a `linux/arm64` container, so the Kotlin/Native cross-linking workarounds are gone. It depends on `libgdal36` and `zlib1g` only.
  - `deploy/mxmariner/deploy.sh` became `cargo xtask` (`crates/xtask`, clap + xshell, the same approach as regatta), 2026-10-31. Same tasks, environment variables, refusals and dry-run-by-default (`--execute` to act); `render` output is byte-identical to the script's. `SHOW_SECRETS=1` became `render secrets --show-secrets`, the `DRY_RUN` variable is gone, and `build` now checks the built image's architecture against `NJORD_PLATFORM`. References to `deploy.sh` above are historical.

## 7. Frontend: Leptos

The current UI is ~4.5k lines of Compose-HTML across `web` and `shared_fe`: a router (`NjordRoute`, `RouteMatcher`), view models, and pages (home/map, chart catalog, chart info, layer page, symbols/sprites, chart installer/admin, about). MapLibre GL is bound through `MapLibre.kt` externals.

- **Mode:** client-side rendering (`leptos` CSR, built with Trunk). SSR buys nothing for a map SPA and would couple the frontend to a Rust server process — the opposite of the separation goal.
- **Map:** keep `maplibre-gl` as JS, loaded from a `<script>` tag (or vendored into `dist/`), and bind with `#[wasm_bindgen(js_namespace = maplibregl)]` externs mirroring `MapLibre.kt`. Only the handful of calls the app uses need bindings (`Map::new`, `setStyle`, `on('click')`, `queryRenderedFeatures`, `addControl`, `fitBounds`, `getCanvas`).
- **State/VMs:** `BaseViewModel` → Leptos signals + `Resource`s; `Network.kt` → a small `reqwest`/`gloo-net` client returning `njord-model` types.
- **Routing:** `leptos_router` with the same path set as `NjordRoute` (keep URLs stable for bookmarks).
- **Admin flows:** signature in local storage (`gloo-storage`), WebSocket via `gloo-net::websocket` deserialising `WsMsg`.
- **Tests before porting:** the existing frontend tests are one `RouteMatcherTest`. The practical safety net is (a) the route list and query-param behavior pinned as a fixture, (b) `njord-model` round-trips, and (c) a screenshot checklist per page. Frontend characterization beyond that isn't worth the cost.

## 8. Risks and decisions to make up front

| Risk | Mitigation |
|---|---|
| GDAL field mapping differences between the Kotlin interop and the `gdal` crate (e.g. string lists, booleans, Integer64, charset recoding) | P-IN-2 pins every feature in the fixture cell; run it on several real NOAA cells (one per `DSSI_NALL` charset) before trusting it. |
| `geo` boolean ops produce slightly different vertices than GEOS for `intersection`/`difference` | Compare P-TE-5 tiles by decoded geometry with a tolerance in tile pixels, not byte equality. Tile pixel rounding absorbs most of it. If not, use `geos` for the include-mask ops only. |
| MVT tag/value ordering differs → not byte-identical | Compare decoded tiles. Byte identity is not a contract; MapLibre doesn't care. |
| Admin signatures minted by one implementation must verify on the other | Signatures embed `externalBaseUrl()`, so an `openenc.com` signature never verifies at `njord.mxmariner.com` anyway; cross-compatibility matters for deb in-place upgrades and for the local diff runs, where one implementation's signature drives the other. P-SEC-1 cross-vectors; same base64/URL-encoding choices; `jiff` RFC 3339 output must match `kotlinx.datetime`'s `Instant.toString()` (nanosecond trimming!). |
| Two implementations writing one schema (the local diff runs: Kotlin ingest, Rust server; deb in-place upgrades) | Freeze `DbMigrations` during the port. Any schema change goes into both or waits. The `njord` namespace never runs Rust code, so a mistake here cannot reach `openenc.com`. |
| Cluster-scoped resources leak between the two environments (image tag pulled with `Always`, the `nfs` StorageClass, the relay's host port) | §10.2: distinct image repository, distinct NFS release and StorageClass, relay on host port 6433. |
| Both environments run on the same three 2 vCPU / 4 GiB nodes | Right-size the Rust pods from measurement (§10.2 item 5), `minReplicas: 1` until cutover, check `kubectl describe nodes` before applying; add a node rather than squeeze the old environment. |
| Shipped mobile builds hard-code `https://openenc.com/v1/regions` and `/v1/style` | `openenc.com` must keep answering (serve, then 301) for as long as those builds are in the field (§10.6). |
| Fixtures freeze bugs as behavior | Resolved 2026-10-30 (TEST_COVERAGE_AUDIT.md §3.1): D1 fixed in Kotlin; D2–D9 accepted in Kotlin and fixed in the port. Every fixture names the decisions it depends on in its header; a case where Kotlin and the port legitimately differ carries `port.expected`, and conformance cases that only pass on the port are tagged `port_only` and skipped against a Kotlin server. |
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

## 10. Deployment: a second environment in `mxmariner`

The Rust stack is deployed as a complete, separate environment. Nothing in the `njord` namespace is modified, scaled or re-pointed until the cutover in §10.6, and the Kotlin repo's `./gradlew :deploy` keeps targeting `njord` so a Kotlin hot-fix cannot land in the wrong namespace. The cluster facts below were read from the live LKE cluster on 2026-10-29.

### 10.1 Targets

| | Today (Kotlin, untouched) | New (Rust) |
|---|---|---|
| Namespace | `njord` | `mxmariner` (exists; already hosts `mxmariner-web-dep` and `regatta-deployment`) |
| Host | `openenc.com`, `www.openenc.com` | `njord.mxmariner.com`, no `www`. Already resolves to the ingress load balancer `172.232.176.134`, same as `regatta.mxmariner.com`. |
| Certificate → TLS secret | `oe-cert` → `njord-cert` | `njord-cert` → `njord-cert`, issuer `letsencrypt-prod` (ClusterIssuer, shared by every namespace, fine) |
| Ingress | `njord-ingress`, class `haproxy` | `njord-ingress`, class `haproxy`. Routing is by host, so no overlap with `mxmariner.com`, `www.mxmariner.com`, `regatta.mxmariner.com`. |
| Database cluster | Akamai managed Postgres (host/port in `njord-db-upstream`) | the same cluster |
| Database / role | `s57server` / `akmadmin` | `njord` / a dedicated `njord` role (§10.2 item 4) |
| Env var naming the database | `NJORD_DB_NAME` | **`NJORD_DB`** |
| Database connection | pgbouncer sidecar, `[databases]` alias `s57server` hard-coded in `Secret.kt` and `vacuum_job.yaml` | no pgbouncer: sqlx pools in each process and connects to `njord-db-relay:6432/${NJORD_DB}` (`pgConnectionInfo`, secret `njord-db-url` for the vacuum Job) |
| Image | `ghcr.io/manimaul/njord-chart-server:<ver>` | `ghcr.io/manimaul/njord:<ver>`, a different repository (§10.2 item 1) |
| Shared volume | PVC `njord-shared-data`, 9Gi RWX, StorageClass `nfs` | PVC `njord-shared-data` (namespaced, so the name is free) on a **new** StorageClass `njord-nfs` (§10.2 item 2) |
| DB relay | DaemonSet `njord-db-relay`, `hostNetwork`, node port 6432 | DaemonSet `njord-db-relay` in `mxmariner`, `hostNetwork`, node port **6433** (§10.2 item 3) |
| Workloads | `njord-chart-dep` (+ HPA 2–5), `njord-ingest-rs`, `njord-enc-download`, `njord-walk-tiles` | same names in `mxmariner`; HPA `minReplicas: 1` until cutover; CronJobs at 02:00 / 03:00 PT |

### 10.2 Overlap audit

Namespaced kinds (Deployment, ReplicaSet, DaemonSet, Service, PVC, ConfigMap, Secret, Ingress, Certificate, CronJob, HPA) can keep today's names in `mxmariner` without touching `njord`, and none of them collide with what `mxmariner` already holds (`mxmariner-*`, `regatta-*`, `ghreg`, `kube-root-ca.crt`). The PVC the question was about is namespaced and its PV is auto-named, so the claim itself is fine. What the namespace boundary does **not** isolate:

1. **Image tag: would change the old environment.** `njord-chart-dep` and `njord-ingest-rs` pull `ghcr.io/manimaul/njord-chart-server:1.4-SNAPSHOT` with `imagePullPolicy: Always`, and `njord-enc-download` hard-codes the same tag. A Rust image pushed to that repository under an existing tag is pulled by the **old** environment on its next pod start (HPA scale-up, node drain, `rolloutRestart`). The Rust image gets its own repository, `ghcr.io/manimaul/njord`, and the new manifests reference only that. Never push the Rust build to `njord-chart-server`.

2. **`nfs` StorageClass: cluster-scoped and backed by the old environment's disk.** `storageClassName: nfs` is served by provisioner `cluster.local/nfs-server-nfs-server-provisioner`, the Helm release `nfs-server` *in namespace `njord`*, whose data lives on the 10Gi block volume `data-nfs-server-nfs-server-provisioner-0` in `njord`. The existing 9Gi `njord-shared-data` already claims most of it. A PVC in `mxmariner` that asks for `nfs` would be carved out of the old environment's NFS server and disk: not isolated, and 9Gi + 9Gi > 10Gi. Running `create_nfs.sh` unchanged in `mxmariner` fails too: Helm refuses to adopt the `nfs` StorageClass owned by another release, and the identical release name would give the second provisioner the same provisioner name, so two provisioners would compete for one class. New environment: Helm release `njord-nfs` in `mxmariner`, `--set storageClass.name=njord-nfs`, `persistence.size` of at least 20Gi (region archives, tile cache and NOAA scratch for a full catalog; the old 10Gi is at its limit), and the PVC uses `storageClassName: njord-nfs`.

3. **DB relay host port: would CrashLoop.** `njord-db-relay` runs `hostNetwork: true` with `socat TCP4-LISTEN:6432,bind=<node InternalIP>` on all three nodes. A second copy on 6432, in any namespace, cannot bind. Two options:
   - Reuse the old relay across namespaces: `host=njord-db-relay.njord.svc.cluster.local port=6432`. **Regatta already does this** (`regatta-pgbouncer-ini` in `mxmariner` points there), which means deleting the `njord` namespace today would take regatta down.
   - Run a second relay in `mxmariner` on host port **6433**: `TCP4-LISTEN:6433`, Service `njord-db-relay` port 6432 → `targetPort: 6433`, probes `grep -q ':1921' /proc/net/tcp` (hex of 6433). Same `DB_HOST` / `DB_PORT` values, since it is the same managed cluster.

   Take the second option so the new environment has no dependency on `njord`, and move regatta's `pgbouncer.ini` onto the `mxmariner` relay as part of §10.6 so `njord` can be deleted cleanly. The IPv6 reasoning in the `chart_server.yaml` comment is unchanged.

4. **Same managed cluster, new database.** Nothing in the server creates the database or the PostGIS extension: `DbMigrations.initializeSchema()` assumes both exist, and `debpkg/db_setup.sh` is the only place in the repo that runs `CREATE DATABASE` and `CREATE EXTENSION postgis`. One-time manual step as the cluster admin:
   ```sql
   CREATE ROLE njord LOGIN PASSWORD '…';
   CREATE DATABASE njord OWNER njord;
   \c njord
   CREATE EXTENSION IF NOT EXISTS postgis;
   ```
   A dedicated role rather than `akmadmin` means the new environment's credentials cannot touch `s57server` even by misconfiguration. The cluster's disk, connection and CPU limits are now shared by two chart databases of similar size (the full NOAA catalog is ~7.2k cells) plus `regatta`; check the managed plan's free disk before seeding.

5. **Node capacity.** Three nodes, 2 vCPU / 3.9 GiB allocatable each. Requests already sit at 49–63% CPU and 28–51% memory per node, and memory *limits* at 81–87%. The old environment alone requests about 1.4 CPU / 3 GiB (two chart pods, the ingest pod, pgbouncer sidecars, relays) with 3 GiB limits sized for Kotlin/Native ingestion. A copy at the same numbers only just fits. Size the Rust pods from measurement (start `njord-chart-dep` at requests 200m / 512Mi, limits 1 CPU / 1.5Gi; `njord-ingest-rs` at 300m / 768Mi, limits 1 CPU / 2Gi) and run the HPA at `minReplicas: 1` until cutover. If it does not fit, add a node rather than shrinking the old environment.

6. **Nightly jobs share upstreams and timing.** `njord-enc-download` (00:00 PT) and `njord-walk-tiles` (01:00 PT) in both namespaces would fetch the same ~100 MB NOAA catalog and up to `maxCellsPerRun` cells in the same minute. Schedule the new ones at 02:00 / 03:00 PT. They write only to their own PVC and call only their own `njord-chart-svc`: `http://njord-chart-svc/v1/chart_editions` resolves inside the namespace, so that value in `njord-enc-cron-config` and in the walk-tiles args stays as it is.

7. **Ingress and certificates.** haproxy (`haproxy-controller` namespace) and cert-manager are cluster-wide and already serve five namespaces by host name. `njord.mxmariner.com` already points at the load balancer, so HTTP-01 succeeds as soon as the Ingress exists. The `openenc.com` rules are not touched.

8. **`ghreg` pull secret** already exists in `mxmariner` (regatta uses it). Nothing to create; `k8s_create_reg_sec.sh k8s_login` needs a namespace parameter if the token is ever rotated for both.

Checked and **not** overlapping: PVC/PV binding (new class, new PV), Service names (namespace-local DNS), ConfigMap and Secret names, the HPA, the `letsencrypt-prod` ClusterIssuer (shared by design), and the upstream `ghcr.io/cloudnative-pg/pgbouncer` and `alpine/socat` images.

### 10.3 Repo changes for the new environment

Everything that names the old environment is hard-coded. The Rust repo's `k8s_deploy/` starts as a copy of today's manifests with the edits below, and the Gradle deploy tasks (`secret`, `k8sApply`, `cycleIngestPod`, `rolloutRestart`, `deploy`) become a shell or `just` script, since the Rust build has no Gradle.

| Where today | Hard-coded value | New |
|---|---|---|
| `buildSrc/src/main/kotlin/Secret.kt` `dbName()` | `NJORD_DB_NAME` | **`NJORD_DB`**. `NJORD_DB_HOST`, `NJORD_DB_PORT`, `NJORD_DB_USER`, `NJORD_DB_PASS` keep their names (same cluster; user and password are the new role's). `getEnv` falls back to the literal string `undefined` instead of failing, so a shell that still exports `NJORD_DB_NAME` but not `NJORD_DB` would silently write `dbname=undefined`; the replacement script must abort on any unset variable. |
| `Secret.kt` pgbouncer `[databases]` | `s57server = … dbname=${dbName()}` | alias **`${NJORD_DB}`**, so alias, `dbname` and the `pgConnectionInfo` path (`postgresql://user@localhost:5432/${NJORD_DB}`) are one value. Today they only agree because `NJORD_DB_NAME` happens to equal `s57server`. |
| `Secret.kt` | `host=njord-db-relay.njord.svc.cluster.local` | `host=njord-db-relay` (same namespace) |
| `Secret.kt`, every manifest, `k8s_create_reg_sec.sh`, `create_nfs.sh`, `build.gradle.kts` (`cycleIngestPod`, `rolloutRestart`) | `namespace: njord` / `-n njord` | `mxmariner`, from one `NJORD_NAMESPACE` variable |
| `K8S.kt`, `makeImg`, `pubImg`, `noaa_enc_daily_cron.yaml`, `k8s_deploy/README.md` dry-run | `ghcr.io/manimaul/njord-chart-server` | `ghcr.io/manimaul/njord` |
| `chart_server.yaml` PVC | `storageClassName: nfs` | `njord-nfs` |
| `create_nfs.sh` | release `nfs-server`, `storageClass.name=nfs`, 10Gi | release `njord-nfs`, `storageClass.name=njord-nfs`, ≥ 20Gi |
| `chart_server.yaml` DaemonSet + Service | `TCP4-LISTEN:6432`, `targetPort: 6432`, probe `:1920` | `TCP4-LISTEN:6433`, `targetPort: 6433`, probe `:1921` |
| `chart_server.yaml` Certificate / Ingress | `oe-cert`, `openenc.com`, `www.openenc.com` | `njord-cert`, `njord.mxmariner.com` |
| `chart_server.yaml` `njord-config` | `pgConnectionInfo: …/s57server`, Kotlin-sized resources | `…/njord`, resources per §10.2 item 5 |
| `vacuum_job.yaml` | `sed -n 's/^s57server *= *//p'` | `s/^njord *= *//p` |
| `noaa_enc_daily_cron.yaml`, `walk_tiles_cronjob.yaml` | `0 0 * * *`, `0 1 * * *` | `0 2 * * *`, `0 3 * * *` |
| `container/application.json`, `server/.../application.json`, `chart_server_db/*`, Kotlin test fixtures | `s57server` | Leave the Kotlin dev/test name alone. The Rust dev compose, `application.json` and tests use `njord` from day one. |
| `k8s_deploy/k8s_deployments.md`, `docs/DESIGN.md`, `README.md` | `njord` namespace, `openenc.com`, `NJORD_DB_NAME` | Rewrite for the new environment, with a "legacy `njord` namespace" section kept until §10.6 completes. |

### 10.4 Bootstrap order

```shell
export NJORD_NAMESPACE=mxmariner
export NJORD_ADMIN_KEY=… NJORD_ADMIN_USER=… NJORD_ADMIN_PASS=…
export NJORD_DB_HOST=… NJORD_DB_PORT=… NJORD_DB=njord NJORD_DB_USER=njord NJORD_DB_PASS=…

# 0. Database and role, once, as the cluster admin             (§10.2 item 4)
# 1. Namespace and ghreg already exist in mxmariner
# 2. NFS: Helm release njord-nfs, StorageClass njord-nfs        (create_nfs.sh, parameterised)
# 3. admin-secret-json, njord-db-url, njord-db-upstream
#                                                               (replacement for ./gradlew :secret)
# 4. njord-admin-basic for the cron's orphan deletes            (k8s_create_reg_sec.sh create_cron_secret)
# 5. Apply chart_server.yaml: relay on 6433, PVC on njord-nfs, Ingress for njord.mxmariner.com
# 6. Wait for the Certificate to be READY, then GET https://njord.mxmariner.com/v1/about/version
# 7. Apply the CronJobs; create one njord-enc-download Job by hand to start seeding
kubectl -n mxmariner get pods,pvc,certificate,ingress
```

This runs in **Phase 7**, once Phases 3–6 have passed locally (decided 2026-10-30; the earlier plan stood the environment up in Phase 3 with a Kotlin ingest and cron beside the Rust server). Every workload runs the **Rust** image. `deploy/mxmariner/` was prepared in Phase 3 with that Kotlin split built in (`NJORD_INGEST_IMPL` / `NJORD_ENC_CRON_IMPL`, the "PHASE 4 flip" / "PHASE 5 flip" markers, `deleteOrphans: false`); flip all of them to Rust before the first apply. The old namespace is never part of this.

### 10.5 Comparing the two environments

The conformance suite (§5.5) runs in diff mode against the two production hosts: `pytest conformance --base-url=https://openenc.com --compare-url=https://njord.mxmariner.com`. Expected differences, all handled by the masking list or by skipping the route in diff mode: `about/version`, host names in `tile_json` and style URLs, `regions` manifests (`createdAt`, archive names), and cells one environment has ingested and the other has not yet. Admin signatures are per host by design (`externalBaseUrl()` is part of the HMAC input), so each environment is driven through its own `/v1/admin`.

### 10.6 Cutover and retirement of `openenc.com`

Consumers outside this repo that hard-code `https://openenc.com`, all in the `mxmariner` repository:

| Consumer | Where | Impact |
|---|---|---|
| mxmariner.com `/chart` page | `frontend/webApp/.../ChartPage.kt` `OPENENC_BASE_URL` | Redeploy `mxmariner-web-dep` |
| MX Mariner mobile styles and regions | `frontend/map/.../StyleAssembler.kt` `ONLINE_STYLE_BASE`, `frontend/data/.../RegionsRepository.kt` `REGIONS_API_URL` | **Shipped app versions keep calling `openenc.com/v1/regions` and `/v1/style`.** `openenc.com` must keep answering, by serving and later by 301, for as long as those builds are in the field. |
| Android asset refresh tasks | `frontend/androidApp/build.gradle.kts` (`refreshStyles`, `refreshWorldBasemap`, S-57 dictionaries) | Build-time only |
| Regatta chart view | `regatta/web/.../ChartController.js.kt` | Redeploy regatta |
| Regatta DB relay | `regatta-pgbouncer-ini` → `njord-db-relay.njord.svc.cluster.local` | Must move to the `mxmariner` relay **before** the `njord` namespace goes away |

Order:
1. Rust environment at parity: Phase 4/5 exits green, conformance diff clean, region archives published.
2. Switch the consumers above to `https://njord.mxmariner.com`; ship the mobile release.
3. Point regatta's `pgbouncer.ini` at the `mxmariner` relay.
4. In `njord`: suspend both CronJobs, scale `njord-ingest-rs` to 0, then replace `njord-chart-dep` behind `njord-ingress` with a redirect-only backend (301 `openenc.com/*` → `njord.mxmariner.com/*`), keeping `oe-cert` so the redirect is served over TLS. This is the first change to the `njord` namespace in the whole plan.
5. After the mobile fleet has moved (watch `openenc.com` request counts), `pg_dump` `s57server` and keep the last region archives, then delete the `njord` namespace, the `nfs-server` Helm release and its `nfs` StorageClass, and drop the `s57server` database.

## 11. Follow-ups after the port

Changes that came up during the port but wait until Phase 7 is complete, because they change the
schema (frozen while Kotlin and Rust share it, §8) or behaviour the goldens pin.

- [ ] **Subdivided base map polygons** ([BASE_MAP_SUBDIVIDE.md](BASE_MAP_SUBDIVIDE.md)). Base-map tiles from zoom 5 up spend 1–1.6 s in PostGIS intersecting whole Natural Earth 1:10m polygons (the ocean is one 446,794-point row). Storing them cut by `ST_Subdivide` and merging each feature's pieces back measured 5–7× faster on coastal tiles (z5, z10) and ~300× on an open-ocean z7 tile that touches a single piece.

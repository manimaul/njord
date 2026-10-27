# Backend Test Coverage Audit

_Audit date: 2026-10-27 · Scope: backend only (`server`, `enc_cron`, `libgdal`, `libpq`, `libzip`, `libsqlite`, `libexpat`, `shared`, `geojson`). The `web` and `shared_fe` frontend modules are out of scope._

## 1. Summary

The backend has about **184 tests**. They are concentrated in the libraries and in a handful of
well-written, intent-documenting server tests (catalog paging, chart editions, clearance labels,
light descriptions, region pruning). The code that decides **what a mariner sees on the chart** has
almost no coverage. That is the tile assembly in `TileEncoder` and the ~128 per-object-class
`preTileEncode` symbology rules. The **ingest pipeline** and the **admin-gated mutating endpoints**
have none.

The server has ~14.5k lines of source and ~1.3k lines of tests. Of the 57 server tests, 4
(`ChartQueryTest`) have no assertions and only check that nothing crashes.

The most important gaps:

1. **Safety-of-navigation logic is untested.** Depth-area colouring (`Depare`), sounding display
   (`displayDepths`), obstruction, wreck and rock danger symbols, chart-overlap priority in `TileEncoder`.
2. **Ingest has no tests.** S-57 → PostGIS, transactional rollback on abort, re-ingest overwrite,
   report accounting.
3. **Security-relevant input handling has no tests.** Admin HMAC signatures, upload filenames, zip
   entry paths.
4. **The region archive is only tested piecemeal.** The SQL constants are tested, but no test
   produces a full `.mbtiles` and checks it against the mobile contract in
   `docs/REGION_DATA_EXPORT_MOBILE.md`.
5. **No CI runs the tests.** There is no `.github/workflows` (or other CI config). DB-backed tests
   need a live PostGIS on `localhost:6432` with the dev credentials, and some also depend on whatever
   charts the dev database happens to hold.

## 2. Current coverage snapshot

| Module | Src LOC | Test LOC | Tests | What is covered | Notable gaps |
|---|---:|---:|---:|---|---|
| `server` | 14,451 | 1,270 | 57 | Chart catalog paging, chart editions key, region rebuild flags, TMS flip + chart_tiles pruning, clearance/name/bearing label styles, light description text, SBDARE/LNDELV, unique layer ids, sprite sheet completeness, static resource traversal guard, gzip, sha256, distributed lock (basic) | `TileEncoder`, `ChartIngest`, `NaturalEarthIngest`, ~128 `preTileEncode` rules, all endpoints except static content, `AdminUtil`/HMAC, `TileCache`/`TileDao`, `DbMigrations`, `FeatureDao`, `GeoJsonDao`, `BaseFeatureDao`, `Dao` retry, `RegionExporter` end-to-end + manifest, workers, `IngestStatus` |
| `enc_cron` | 1,011 | 441 | 37 | Catalog SAX parser (chunking, decoys, namespace guard), stale/orphan selection, producer gating, batching, base-url derivation | `BundleBuilder`, `EncHttp` (retry, download), `EncCronConfig.load` env merge, `deleteOrphans` guard ordering, `run()` orchestration |
| `libgdal` | 2,239 | 362 | 14 | TileSystem scale↔zoom, clip polygon, S-57 open/feature count, feature props, `VectorTileEncoder.hasFeatures` | `displayDepths`/`addSoundingConversions`, `OgrS57Dataset.chartInsertInfo`, `OgrGeometry` ops (difference/intersection/makeValid), `VectorTileEncoder.encode` output, `transformToTilePixels`, `MvtDataset` |
| `libpq` | 765 | 890 | 27 | Statements, prepared statements, arrays, raw queries | Connection pool exhaustion / reconnect |
| `libsqlite` | 176 | 106 | 6 | Open/exec/prepare/bind | Transaction rollback on throw |
| `libexpat` | 243 | 230 | 14 | SAX parsing, element paths | — (adequate) |
| `libzip` | 231 | 166 | 8 | Reader basic, writer | Zip-slip on `unzipToPath`, large/streamed entries |
| `shared` | 1,512 | 136 | 4 | `File` | `S57Object`, `WsMsg` serialization contract, `Style` serialization |
| `geojson` | 770 | 372 | 17 | Geometry (de)serialization | Malformed input, CRS, bbox on all types |

### Observations on the existing suite

- **Good:** recent tests (`ChartCatalogTest`, `ChartEditionsTest`, `ClearanceTest`, `RegionExporterTest`, `EncProdCatParserTest`) document the *why* and assert precise boundary behaviour. Use them as the house style.
- **`ChartQueryTest`** creates an unused `testing` table and has no assertions. Its `testMvt`/`testMvtDisk` depend on whatever charts are in the dev DB near tile 14/2621/5743. Replace it or turn it into assertions (see TE-1).
- **Hard-coded paths and credentials:** `postgresql://admin:mysecretpassword@localhost:6432/s57server` is repeated in 7 files. `/tmp/njord/...` and `./build/tmp/...` are both used as scratch dirs. A shared test fixture would remove the duplication.
- **Global state:** most server tests must set the `resources` global before touching `Singletons`, so test order and the working directory matter.
- **Reusable fixture:** `libgdal/src/nativeTest/resources/US5WA22M/US5WA22M.000` is a real NOAA cell. It is the natural seed for ingest and tile-encoding integration tests.

## 3. Suspected defects found during the audit

These came from reading the code, not from running it. Each one should get a failing test first, then a fix.

| # | Location | Suspected issue | Test ref |
|---|---|---|---|
| D1 | `libgdal/.../Soundings.kt` `displayDepths` | **Fixed 2026-10-27**, pinned by `libgdal` `SoundingsTest`. `((meters - metersWhole) * 10.0).toInt()` truncated a floating-point error: `2.3` → `0.2999…*10` → tenths = 2, so a 2.3 m sounding displayed as 2₂. Now splits on decimal tenths, truncating toward zero. Negative drying heights still give negative whole *and* tenths parts (`-1.5` → `-1`, `-5`); that is pinned as existing behaviour, not yet decided. | SY-1, SY-2 |
| D2 | `endpoints/EncSaveHandler.kt` | `filename` query parameter is used verbatim in `File(chartDir, fileName)`. `../` escapes the save dir. (Admin-gated, but still a write-anywhere.) | SEC-3 |
| D3 | `libzip/.../ZipFile.kt` `unzipToPath` | Entry names are not normalised, so a `../../x` entry writes outside `dir` (zip-slip). It is reached from server ingest of uploaded zips. `enc_cron`'s `startsWith("ENC_ROOT/<cell>/")` filter does not stop `ENC_ROOT/<cell>/../../x`. | SEC-4 |
| D4 | `endpoints/AdminHandler.kt` `verifySignature(query)` | Malformed base64/JSON throws instead of returning `false`, so the response is likely a 500 instead of a 401. The comparison is not constant-time. | SEC-1 |
| D5 | `ingest/ChartIngest.kt` + `db/Dao.kt` | `installChartFeatures` runs inside `sqlOpAsync`, which retries the *whole block* up to 7 times on any exception. The transaction is rolled back, but `Report.appendChartFeatureCount` has already counted the failed attempt, so completion reports are inflated. `ChartDao.insertAsync(overwrite=true)` does delete + insert outside a transaction, so a failure between them loses the previous chart. | IN-4, DB-2 |
| D6 | `util/DistributedLock.kt` | `tryAcquireLock` is check-then-rename. Two instances can both see an empty lock, both rename, and both briefly read their own uuid. `tryClearLock` does not check ownership. `EncSaveHandler.handlePost` calls it unconditionally, which aborts any in-progress ingest. That may be intentional, but no test pins it. | CC-1, CC-2 |
| D7 | `libgdal/.../OgrS57Dataset.kt` `chartInsertInfo` | A missing `DSID_DSNM` produces a chart named `""` (all such charts overwrite each other). When there are several `M_COVR` features, `MultiPolygon` coverages are silently dropped by `as? Polygon`. | IN-2 |
| D8 | `endpoints/TileHandler.kt` | `z/x/y` are not range-checked. Negative or out-of-range tiles are encoded and written into the disk cache, which lets callers grow the cache without bound. | API-3 |
| D9 | `endpoints/EncUrlHandler.kt` | The `HttpClient(Curl)` has no `expectSuccess`, so a 404/500 body is saved as `<name>.zip` and later picked up by the ingest worker. | API-6 |

## 4. Tier definitions

| Tier | Meaning | Guidance |
|---|---|---|
| **0** | **Critical.** A regression here can put wrong navigational information on screen, corrupt or lose chart data, break a security boundary, or break the mobile or `enc_cron` contracts. Also covers every suspected defect above. | Write these first. They should block merges once CI exists. |
| **1** | **Core.** Main functional paths and API contracts whose failure is visible but not dangerous: an endpoint 500s, a style fails to load, a cache is stale. | Next tranche. Aim for full coverage of public endpoints and DAOs. |
| **2** | **Hardening.** Edge cases, operational robustness, diagnostics, performance guards, and lower-risk helpers. | Opportunistic. Add when touching the code. |

## 5. Recommended tests by group

Each test has an ID for tracking. **Type:** U = pure unit, D = needs PostGIS, F = needs filesystem/GDAL fixture, H = HTTP-level.

### 5.1 Symbology & S-52 conditional logic (`layers/*.preTileEncode`, `Soundings.kt`)

This decides colours, symbols and text on the chart. ~128 classes override `preTileEncode` and only `LightDescription`, `Sbdare.natsur`, and the label styles have tests. Most of these tests are pure unit tests: build a `ChartFeature` with props, call `preTileEncode`, assert the `SY`/`AC`/`LC`/`AP` props.

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| SY-1 | 0 | U | `displayDepths` exact decimal cases: 0.0, 0.1…0.9, 2.3, 9.95, 10.0, 31.0. Assert whole/tenths/feet/fathoms/fathomsFeet. Pins **D1**. |
| SY-2 | 0 | U | `displayDepths` drying heights (negative metres): decide and pin the intended representation. Today it yields negative parts. |
| SY-3 | 0 | U | `Depare.preTileEncode` colour bands: DRVAL1<0 & DRVAL2≤0 → DEPIT, boundaries exactly at `shallowDepth`, `safetyDepth`, `deepDepth` (≤ vs >), missing DRVAL1 or DRVAL2 → CHBLK, DRVAL1<0 with DRVAL2>0. Use a non-default `ChartsConfig` to prove thresholds come from config. |
| SY-4 | 0 | U | `Soundg` adds `METERS_W/T`, `FEET`, `FATHOMS(_FT)` and the style filters show the tenths sub-text only below `deepDepth`. |
| SY-5 | 0 | U | `Obstrn`, `Wrecks`, `Uwtroc`: danger symbol selection by `VALSOU` vs safety depth, `WATLEV`, `CATOBS`/`CATWRK`, point vs area vs line geometry. (S-52 CSP OBSTRN07/WRECKS05 analogues.) |
| SY-6 | 0 | U | `Lights`: sector lights vs all-round lights, colour → symbol mapping, `ORIENT` handling, and that LIGHTS geometry is *not* clipped (see TE-4). |
| SY-7 | 1 | U | Lateral/cardinal/isolated-danger/safe-water/special buoys and beacons (`Boylat`, `Boycar`, `Boyisd`, `Boysaw`, `Boyspp`, `Bcn*`): `BOYSHP`/`BCNSHP` × `COLOUR` × `COLPAT` → sprite. Table-driven; include unknown shape fallback. |
| SY-8 | 1 | U | `Topmar` + `TopmarData.fromAssoc`: symbol varies with the associated buoy vs beacon layer names (`associatedLayerNames`). |
| SY-9 | 1 | U | Attribute helpers (`attributehelpers/*`): `Catspm`, `Catlmk`, `Topshp`, `Functn`, `Restrn`, `Quasou`, `Watlev`, `Conrad`, `Convis`, `DepthColor`: enum value → expected decoding, unknown value → null/default. Table-driven across all helpers. |
| SY-10 | 1 | U | Every `Layerable` in `StandardLayers`/`InlandLayers`/`ExtraLayers`/`BaseLayers`: `preTileEncode` on a minimal feature (empty props, each geometry type) does not throw. A cheap smoke test across all 128. |
| SY-11 | 1 | U | Every sprite name any `preTileEncode` can assign exists in each theme's sprite sheet (extends `SpriteSheetTest` from "sheet ⊇ enum" to "assigned ⊆ sheet"). |
| SY-12 | 2 | U | `Lndmrk`, `Morfac`, `Siltnk`, `Pipare`, `Resare`/`Catrea`, `Drgare`, `Fshfac`: category → symbol/pattern tables. |
| SY-13 | 2 | U | `NameLabel`/`ClearanceLabel` for every object class they claim: style ids unique and source layers exist in `StandardLayers`. |

### 5.2 Tile encoding (`geo/TileEncoder.kt`, `libgdal` MVT)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| TE-1 | 0 | D,F | **Chart priority / masking.** Seed two overlapping charts (a larger-scale chart inside a smaller-scale one) with a feature in the overlap from each. Encode a tile and decode the MVT. Only the larger-scale (more detailed) chart's feature appears in the overlap, and the other chart's features still appear outside it. Replaces `ChartQueryTest`. |
| TE-2 | 0 | D | **Zoom eligibility.** A chart whose `zoom` > tile `z` contributes no features. Features outside `[z_min, z_max]` are excluded at both boundaries. |
| TE-3 | 0 | U | `filtered()`: blank strings removed, `"0"` removed **only** for enumerated (`attributeType == "E"`) attributes (float `0` kept, e.g. `VALSOU=0`), empty arrays removed, nested objects raise. |
| TE-4 | 1 | D | LIGHTS features are not intersected with the tile include mask (so sector arcs that start outside still render), while every other layer is clipped to it. |
| TE-5 | 1 | D | `PLY` boundary is emitted for every intersecting chart and `hasContent()` ignores it. |
| TE-6 | 1 | D | Base map fill: where no chart covers the tile, base features render. Where charts fully cover it, none render. `findBaseMapScale` boundaries at z 2/3 and 4/5. |
| TE-7 | 1 | D | `info=true` mode returns `ChartFeatureInfo` with GeoJSON in WGS84 (not tile pixels) and skips the base map. |
| TE-8 | 1 | U | `VectorTileEncoder.encode()` round-trip: encode points, lines, and polygons with props, decode the protobuf (or via GDAL MVT driver), assert geometry in 0..4096 extent and properties preserved. |
| TE-9 | 1 | U | `transformToTilePixels` at tile corners/edges and the antimeridian; `createTileClipPolygon(expandPixels=15)` buffer size. |
| TE-10 | 2 | U | Invalid/empty geometries after intersection are dropped, not encoded. |
| TE-11 | 2 | D | Timing spans (`spans()`) populated. Debug envelope added when `debugTile`. |

### 5.3 Chart ingestion (`ingest/ChartIngest.kt`, `OgrS57Dataset`, `ChartIngestWorker`, `NaturalEarthIngest`)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| IN-1 | 0 | D,F | **Happy path end-to-end.** Zip `US5WA22M` and run `ChartIngest.ingest`. Assert the `charts` row (name, scale, zoom, `updated`/`issued`, `dsid_props.DSID_UPDN`), the per-layer feature counts match `OgrS57Dataset.featureCount(exLayers)`, `lnam_refs` populated, the `CompletionReport` matches, the tile cache is cleared, and the work dir is deleted. |
| IN-2 | 0 | F | `chartInsertInfo` errors: missing DSID, missing `M_COVR`, missing `DSPM_CSCL`, missing `DSID_DSNM` (pins **D7**). Multiple `M_COVR` with `CATCOV=1` including a MultiPolygon. `CATCOV=2` excluded. Charset selection from `DSSI_NALL`/`AALL`. |
| IN-3 | 0 | D,F | **Re-ingest overwrite.** Ingesting the same cell twice leaves exactly one chart row and one set of features (no duplicates, no leftovers from the previous edition). |
| IN-4 | 0 | D,F | **Abort mid-chart.** Clear the lock during feature insert. That chart's transaction is rolled back (zero features for it), the report is `Idle`, and the lock is released. Also: a thrown insert error does not double-count features across `Dao` retries (pins **D5**). |
| IN-5 | 1 | F | `step1UnzipFiles` skips `__MACOSX` and directories, filters to `.000`, and progress messages go from 0 → 1. |
| IN-6 | 1 | U | `Report`: concurrent `appendChartFeatureCount`/`failChart` from many coroutines totals correctly. `failChart` twice concatenates. `completionMessage` after `abort()` is `Idle`. |
| IN-7 | 1 | F | `ChartIngestWorker.tryClaimAndIngest`: no zips → no lock taken. Lock busy → zip untouched. Claimed zip moved to `ingest/<uuid>/`. A zip containing `.shp` is routed to `NaturalEarthIngest`. |
| IN-8 | 1 | D,F | `NaturalEarthIngest` with a tiny shapefile zip: `normalizeLayerName` / `scaleFromFilename` table (`ne_10m_`, `ne_50m_`, `ne_110m_`, bathymetry/lakes prefixes) and a replace-by-name-and-scale insert. |
| IN-9 | 2 | D,F | `chartIngestWorkers` concurrency honoured (never more than N charts in flight). |
| IN-10 | 2 | F | Cell with `.001…` update files: GDAL applies updates and `DSID_UPDN` reflects the last update (feeds `ChartEditionsTest` assumptions). |

### 5.4 Security & admin authorisation (`AdminHandler`, upload paths, archives)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| SEC-1 | 0 | U | `AdminUtil`: a freshly created signature verifies; expired → false; tampered `uuid`/`date`/`signature` → false; different `baseUrl` → false; different `adminKey` → false; malformed base64 / JSON in `verifySignature(query)` → **false, not throw** (pins **D4**). Also a future-dated signature fails (HMAC covers the date). |
| SEC-2 | 0 | U | `HmacSHA256` matches the RFC 4231 test vectors. Calling `doFinal` twice must not double-free `ctx` (today it frees inside `doFinal`). Pin that it is single-use. |
| SEC-3 | 0 | H/U | `EncSaveHandler` rejects `filename` containing `/`, `\`, `..`, or an absolute path, and only accepts `.zip` (pins **D2**). |
| SEC-4 | 0 | F | `ZipEntry.unzipToPath` refuses entries resolving outside `dir` (`../x`, `/abs`, `a/../../x`). Covers both server ingest and `enc_cron` `extractCell` (pins **D3**). |
| SEC-5 | 0 | H | Every mutating route (`POST/DELETE /v1/chart`, `/v1/enc_save`, `/v1/enc_url`, `/v1/geojson` POST, `/v1/nuke`, `/v1/cache` DELETE, `/v1/regions` POST, `/v1/regions/{archive}`, `/v1/ws/enc_process`) returns 401 without a signature and with an invalid one. Table-driven, so a new handler without `requireSignature` is caught. |
| SEC-6 | 1 | H | `/v1/admin` requires basic auth and `isAdminAuthorized` matches only `adminUser`. `/v1/admin/verify` refreshes a valid signature. |
| SEC-7 | 1 | U | `RegionArchiveHandler` rejects `..`, `/`, and also URL-encoded `%2e%2e` / backslash variants after decoding. |
| SEC-8 | 2 | U | `externalBaseUrl()` with `X-Forwarded-Proto/Host/Port` (default-port elision), since the signature is bound to it. |

### 5.5 Database & DAOs (`db/*`, `DbMigrations`)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| DB-1 | 0 | D | `DbMigrations.run` on (a) an empty DB → `initializeSchema` at the current version; (b) a v1 DB → migrations 2 and 3 applied in order with the version stamped after each; (c) a migration whose `action` returns false → version not stamped and re-run is idempotent; (d) two concurrent runners → one migrates and the other waits. Also: a fresh schema equals the migrated schema (compare `information_schema` / `pg_indexes`). |
| DB-2 | 0 | D | `ChartDao.insertAsync(overwrite=true)`: replaces the chart *and* deletes its old features. A failing insert (e.g. invalid GeoJSON coverage) does not leave the old chart deleted (pins **D5**). |
| DB-3 | 0 | D | `ChartDao.deleteByNameAsync`: removes the chart + features; unknown name → false. |
| DB-4 | 1 | D | `findAllChartFeaturesAsync4326`: clips to the tile box, filters by `chart_name = ANY`, respects `z_min/z_max`, empty name list → empty map without a query. |
| DB-5 | 1 | D | TOPMAR association: `topmarAssocByLnam` / `FeatureDao.findAssociatedLayerNames` return the layers of features whose `lnam_refs` contain the TOPMAR's LNAM. Non-TOPMAR features untouched. |
| DB-6 | 1 | D | `FeatureDao.insertFeatureSync`: `MINZ`/`MAXZ` defaults (0/32), `lnam_refs` from the `LNAM_REFS` array, 3D geometry forced 2D. `lnamRefs()` on missing/non-array/non-string elements. |
| DB-7 | 1 | D | `FeatureDao.findLayerPositionsPage` paging (`start_id`, limit 5, `lastId`). `findFeature(lnam)` hit and miss. |
| DB-8 | 1 | D | `ChartDao.findAsync` returns layers, feature count, bounds, `dsidProps`/`chartTxt` round-trip. |
| DB-9 | 1 | D | `GeoJsonDao.fetchAsync` / `featureInsertAsync` round-trip for each geometry type. |
| DB-10 | 1 | D | `BaseFeatureDao` insert/find/delete by name+scale; `findFeaturesAsync` intersects the inclusion mask. |
| DB-11 | 1 | D | `RegionDao.findChartsInRegion`, `findFeaturesForChart`, `regionsContainingChart` (excludes non-intersecting, handles multiple regions), `latestChartUpdateInRegion`. |
| DB-12 | 1 | D | `NukeDao.truncateAllAsync` empties features, charts and base_features but keeps `meta_data` (schema version) intact. |
| DB-13 | 2 | U | `Dao.sqlOpAsync` retry semantics with a fake `DataSource`: null connection retried up to `tryCount`, `CancellationException` not retried, success after N failures returns the value, delay backoff. |

### 5.6 Region export (`RegionExporter`, `RegionExportWorker`, `RegionHandler`)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| RE-1 | 0 | D,F | **Full archive contract.** Seed a small chart, run `exportNext()`, and open the resulting `.mbtiles`. Check: `metadata` rows (`format=pbf`, min/max zoom, bounds, `njord:*` keys, `njord:generated_at` == filename stem instant), tiles gzip-encoded and decodable, `tile_row` in TMS, `charts` row mirrors PostGIS, every `chart_tiles` edge has a tile, `PRAGMA foreign_key_check` clean, sha256 sidecar matches the file. |
| RE-2 | 0 | D,F | `buildManifest()`: unrendered region → nulls; rendered → newest archive name/size/sha256/createdAt; sidecar reused if present and computed if missing or corrupt. |
| RE-3 | 1 | U | `parseTimestampStem` ↔ `currentTimestamp` round-trip, including a DST-transition hour in a fixed TZ. `parseArchiveTimestamp` ignores a region whose name is a prefix of another (`WA` vs `WA_SOUND`). **`archivesForRegion` uses `startsWith(regionName)`, so `WA` also matches `WA_SOUND_*` archives.** Pin it. |
| RE-4 | 1 | F | `pruneOldArchives` keeps `MAX_ARCHIVES` newest and deletes older archives *and* their sidecars. |
| RE-5 | 1 | D | `compileTileCoordinates`: `MINZ`/`MAXZ` clamped to `chart.zoom`, `MINZ > MAXZ` skipped, bbox → tile range at the poles/antimeridian clamped to `[0, 2^z-1]`. |
| RE-6 | 1 | D | `exportNext()` result: no configs → `NothingToDo`; lock held elsewhere → `LockBusy` with no file written; export failure still releases the lock; `needsRebuild` DB error fails open. |
| RE-7 | 1 | H | `RegionArchiveHandler` ranges: no Range → 200 full; `bytes=0-99` → 206 + `Content-Range`; suffix `bytes=-100`; start beyond EOF → 416; multi-range merged to single. |
| RE-8 | 1 | H | `RegionHandler` POST: missing name → 400; unknown → 404; valid → 202, export state cleared, worker woken. |
| RE-9 | 2 | U | `RegionExportWorker.run` loop with fake exporter: pending zips / held lock → poll; `Rendered` → loop immediately; `NothingToDo` → idle; `wake()` cuts an idle short; an exception does not kill the loop. |
| RE-10 | 2 | D | `WORLD` region: base-map only, full quad tree to z6, no `charts` rows, `ChartHandler.delete` never invalidates it. |

### 5.7 Concurrency & coordination (`DistributedLock`, `IngestStatus`, workers)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| CC-1 | 0 | F | `DistributedLock` contention: N instances racing `tryAcquireLock` in parallel coroutines/threads; at most one *settles* as holder after the watch catches up (pins **D6**). |
| CC-2 | 0 | F | Lock ownership semantics: decide whether a non-holder's `tryClearLock` may release someone else's lock, then pin it. Pin that `EncSaveHandler.handlePost` aborting an in-flight ingest is intended. |
| CC-3 | 1 | F | `lockAcquired` flips to false within ~1 s when another instance takes the lock (inotify path on Linux, fallback poll on macOS). Pins the signal ingest relies on to abort. |
| CC-4 | 1 | F | `IngestStatus.writeMsg` is a no-op without the lock; `writeMsgIgnoreLock` always writes; writes are atomic (a reader polling the file never sees partial JSON). |
| CC-5 | 2 | F | `ChartWebSocketHandler`: sends current status on connect and only sends changes; empty file → `Idle`. |

### 5.8 Tile cache & tile serving (`TileCache`, `TileDao`, `TileHandler`)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| TC-1 | 1 | F | `TileCache`: put/get round-trip; empty data never cached; `get` on a missing or zero-length file → null; `clear()` removes everything and recreates the base; `counts()` per zoom; no `.tmp` files left behind. |
| TC-2 | 1 | D | `TileDao.getTile`: cache hit skips encoding; `useTileCache=false` or `debugTile=true` bypasses read *and* write. |
| TC-3 | 1 | D | Cache invalidation after ingest (IN-1) and after `ChartHandler` DELETE, so stale tiles are never served. |
| API-3 | 1 | H | `TileHandler`: non-integer params → 404/400; out-of-range `z/x/y` (negative, `x ≥ 2^z`, `z > 24`) rejected **before** encoding/caching (pins **D8**); `info=true` returns JSON. |
| TC-4 | 2 | F | Concurrent `put` of the same tile from multiple writers leaves a valid file (rename atomicity). |

### 5.9 HTTP API contract (endpoints, routing, style)

Ktor's `testApplication` (if it is available for the native target in the version used), or handler tests that call into handlers with a stub `ApplicationCall`. If neither is practical, pull the decision logic out into pure functions and unit-test those.

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| API-1 | 0 | H/D | `GET /v1/chart_editions` and `GET /v1/chart_catalog` JSON shape matches what `enc_cron` (`ChartEditionsResponse`) and the frontend deserialize. Serialize with server types and decode with client types. |
| API-2 | 1 | U | `StyleHandler` / `LayerFactory.layers`: for every `Depth` × `ThemeMode` × custom theme, the style serializes and has version 8, glyph/sprite/tile_json URLs built from `externalBaseUrl`, and unique layer ids. Unknown depth or theme → 404. Unknown `c=` custom theme falls back to the base theme. |
| API-4 | 1 | H/D | `ChartHandler` DELETE: 202 on delete, 204 when absent, 400 without name. Clears export state for exactly the intersecting non-WORLD regions and wakes the worker only if any were affected. |
| API-5 | 1 | H/D | `FeatureHandler` `/layer/{name}?start_id=` and `/lnam/{lnam}`; unknown `by` → 404. `GeoJsonHandler` GET 404 on missing params; POST 400 on unknown chart. |
| API-6 | 2 | H | `EncUrlHandler`: filename derivation (query string stripped, non-zip → `chart_<ts>.zip`), and a non-2xx download is not published as a `.zip` (pins **D9**). |
| API-7 | 2 | H | `NukeHandler` clears DB, tile cache, and region archives. `CacheHandler` GET counts. `AboutHandler`, `TileJsonHandler`, `IconHandler`, `SvgLightSectorHandler` return 200 with expected content type. |
| API-8 | 2 | U | `Route.addHandler`: unimplemented verbs return 405. |

### 5.10 NOAA update job (`enc_cron`)

The pure selection logic is already well covered. What is missing is the I/O around it.

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| EC-1 | 0 | F | `BundleBuilder.buildAndPublish` with fake `EncHttp` serving fixture cell zips. The bundle contains only `ENC_ROOT/<CELL>/…` (no `CATALOG.031`/README), `.000` + `.00N` + `.TXT` are colocated, it is published atomically into `saveDir`, staging/download dirs are cleaned, a cell failure is skipped while the others publish, and all failing → null with nothing published. |
| EC-2 | 0 | U | `deleteOrphans` guard ordering with a fake `EncHttp`: disabled → no calls; no base URL → no calls; no creds → no calls; over `maxOrphanDeletes` → **no deletes**; dry-run → no deletes; otherwise signature fetched once and each deletable chart deleted. A truncated catalog must never wipe the DB. |
| EC-3 | 1 | U | `EncCronConfig.load`: file defaults, `ENV_OVERRIDES` shallow merge, credentials from env, missing file. |
| EC-4 | 1 | U | `EncHttp.withRetry`: retries `maxRetries` times with backoff then throws with the last cause; success on retry N returns. `download` removes the `.tmp` on failure and never leaves a partial `dest`. |
| EC-5 | 1 | U | `run()` orchestration: empty catalog → fails loudly (`check`); nothing stale → no publish; `maxCellsPerRun` cap; dry-run publishes nothing. |
| EC-6 | 2 | U | `CommandLine.parse` flags; `queuedBundleCount` affects batching. |

### 5.11 Native library bindings (`libgdal`, `libzip`, `libsqlite`, `libpq`)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| LB-1 | 1 | U | `OgrGeometry`: `difference`, `intersection`, `boundary`, `makeValid` (self-intersecting polygon → valid), `envelope`, `centroidOfLargestPart`, WKB/WKT/GeoJSON round-trips, `isEmpty`/`isValid`. TileEncoder's masking depends on these. |
| LB-2 | 1 | F | `OgrFeature.properties`: `OFTStringList` → JSON array (`LNAM_REFS`), integer lists, reals, empty fields, and non-UTF-8 text under each charset. |
| LB-3 | 1 | U | `SqliteDb.transaction` rolls back when the block throws; `prepare().reset()` reuse across many rows. |
| LB-4 | 2 | U | `libpq` `PgDataSource` under pool exhaustion and after the server drops a connection (returns null → `Dao` retry path). |
| LB-5 | 2 | F | `ZipFile` large-entry chunked extraction (> chunk size) is byte-identical. `ZipWriter` → `ZipFile` round-trip with nested dirs. |
| LB-6 | 2 | U | `MvtDataset`/`MvtDiskDataset` produce tiles for the requested zoom range (replace the assertion-free `ChartQueryTest.testMvt*`). |

### 5.12 Shared models & serialization (`shared`, `geojson`)

| ID | Tier | Type | Test |
|---|:-:|:-:|---|
| SH-1 | 1 | U | `WsMsg` polymorphic serialization round-trip for every subtype (the frontend and status file depend on the discriminator). |
| SH-2 | 1 | U | `RegionManifestEntry`, `ChartEditions`, `ChartCatalog`, `AdminSignature` JSON is stable (golden JSON). These are consumed by mobile, `enc_cron`, and the web UI. |
| SH-3 | 2 | U | `geojson`: malformed input errors, `bbox` on every type, `GeometryCollection` nesting, antimeridian-crossing `BoundingBox`. |
| SH-4 | 2 | U | `S57Object`/`S57ObjectLibrary` attribute type lookup (`"E"` enumerated) used by TE-3. |

## 6. Tier index

**Tier 0 (28):** SY-1, SY-2, SY-3, SY-4, SY-5, SY-6, TE-1, TE-2, TE-3, IN-1, IN-2, IN-3, IN-4, SEC-1, SEC-2, SEC-3, SEC-4, SEC-5, DB-1, DB-2, DB-3, RE-1, RE-2, CC-1, CC-2, API-1, EC-1, EC-2

**Tier 1:** SY-7–SY-11, TE-4–TE-9, IN-5–IN-8, SEC-6, SEC-7, DB-4–DB-12, RE-3–RE-8, CC-3, CC-4, TC-1–TC-3, API-2–API-5, EC-3–EC-5, LB-1–LB-3, SH-1, SH-2

**Tier 2:** SY-12, SY-13, TE-10, TE-11, IN-9, IN-10, SEC-8, DB-13, RE-9, RE-10, CC-5, TC-4, API-6–API-8, EC-6, LB-4–LB-6, SH-3, SH-4

## 7. Test infrastructure recommendations

These make the tests above cheap to write and keep reliable.

1. **CI.** Add a pipeline that starts `chart_server_db` (PostGIS) and runs `./gradlew allTests` on Linux x64. Without it, none of the above is enforced.
2. **Shared server test fixture.** A `TestEnv` object that sets `resources`, builds a `ChartsConfig` with test thresholds and a temp `chartTempData`, owns a `PgDataSource` from one env var (default to the dev URL), and creates a per-test temp dir. Remove the 7 hard-coded connection strings and the mix of `/tmp/njord` and `./build/tmp`.
3. **Isolated database.** Run DB tests against a dedicated `s57server_test` database (created and migrated by `DbMigrations` in the fixture) rather than the dev DB. That removes the `zz_`/prefix workarounds and makes TE-1/IN-1 deterministic.
4. **Fakes over singletons.** Most classes already take their collaborators as constructor defaults from `Singletons`, so tests can pass fakes. The exceptions are `RegionExporter.compileTileCoordinates` (`Singletons.tileSystem`), `TileEncoder` construction inside `TileDao`/`RegionExporter`, `ChartIngest` → `Singletons.regionExportWorker`, and `requireSignature` → `Singletons.adminUtil`. Inject these so tests don't need full `Singletons` initialisation.
5. **MVT decode helper.** A small test-only decoder (protobuf or GDAL's MVT driver reading bytes) so tile tests assert on layers, features, and props instead of byte sizes.
6. **Fixtures.** Reuse `libgdal/src/nativeTest/resources/US5WA22M` for ingest tests. Add two tiny synthetic S-57 cells (overlapping coverages, different scales) for TE-1, plus a malicious zip (zip-slip entry) for SEC-4.
7. **Coverage measurement.** Kotlin/Native has no Kover support. Track coverage by this document's IDs (tick them off here or in `docs/TODO.md`) rather than a line-coverage percentage.

# Catalog-driven NOAA ENC updates (`enc_cron`)

## Context

NOAA republishes its ENC cells continuously. Njord's nightly updater was a `curlimages/curl` pod
(`k8s_deploy/noaa_enc_daily_cron.yaml`) that did exactly one thing:

```yaml
command: [curl, --fail, --silent, --show-error, --location, --create-dirs,
          --output, /mnt/njord/charts/save/OneDay_ENCs.zip,
          https://charts.noaa.gov/ENCs/OneDay_ENCs.zip]
```

Problems with it:

1. **It only sees a one-day window.** Anything published while the cron, the ingest ReplicaSet, or
   the cluster was down is never picked up. There is no reconciliation path back to "what does
   NOAA actually have that we don't".
3. **It writes straight into `save/`.** `ChartIngestWorker` polls that directory every 5 s and
   claims by rename, so it can claim a partially-written zip mid-download.

`enc_cron` replaces it: parse NOAA's product catalog, diff it against what Njord has actually
ingested, and fetch only the cells that changed.

---

## Investigation

Facts measured against the live catalog and real cell downloads. These drive most of the design.

### The catalog

`https://charts.noaa.gov/ENCs/ENCProdCat_19115.xml` — 52 MB of ISO-19115 (`gmd`) XML, **7,229
cells**, ~818 MB of cell payload in total. Well-formed, no DTD, no CDATA, ~20 entity escapes.
Each cell record carries a name, `<edition>`, revision and publication dates, compilation scale,
transfer size, and a per-cell zip URL.

### `zipfile date and time` is useless for change detection

Every record has a `<description>zipfile date and time: ...</description>`. NOAA regenerates every
cell zip nightly, so on any given fetch all 7,229 are stamped within the same hour. It says when
the archive was built, not when the chart changed.

### `<title>` is ambiguous and the decoy always wins a naive match

`<title><gco:CharacterString>` appears twice per record:

| path | value |
|---|---|
| `identificationInfo/MD_DataIdentification/citation/CI_Citation/title/…` | `US5WA22M` — the cell name |
| `dataQualityInfo/DQ_DataQuality/lineage/…/sourceCitation/CI_Citation/title/…` | `0` — always |

The decoy comes second, so first-match extraction yields 7,229 cells all named `0`. Element
matching must be path-scoped, not leaf-name-based.

### `DSID_EDTN` cannot be used as the comparison key

This is the load-bearing finding. The obvious key is the S-57 edition, `EDTN.UPDN`, compared
against the catalog's `<edition>` string. It is wrong for a subset of cells.

GDAL's S57 driver applies the sibling `.001`…`.00N` update files when opening a `.000`, and in
doing so **overwrites the DSID fields with the values carried by the last applied update file**.
NOAA publishes `EDTN = 0` in some of those update files. Measured with `ogrinfo`:

| cell | catalog `<edition>` | catalog revision | catalog publication | EDTN | UPDN | UADT | ISDT |
|---|---|---|---|---|---|---|---|
| `US1EEZ3M` | `2.4` | 2013-10-21 | 2020-08-28 | 2 | 4 | 20131021 | 20200828 |
| `US1GC09M` | `74.6` | 2025-12-09 | 2026-05-20 | **0** | 6 | 20251209 | 20260520 |

Reading `US1GC09M` with `OGR_S57_OPTIONS=UPDATES=OFF` gives `EDTN=74, UPDN=0` — the true base
edition — confirming the `0` comes from update file `.006`, not from the base cell.

An `EDTN`-based key would compare `"0.6"` against the catalog's `"74.6"`, never match, and
re-download and re-ingest that cell **every single night** — reintroducing the exact problem this
change exists to remove.

`UPDN`, `UADT` and `ISDT` all survive update application intact and map exactly onto catalog
fields:

- `UPDN` == the `<edition>` suffix
- `UADT` == the `revision` date (`YYYY-MM-DD` → `YYYYMMDD`)
- `ISDT` == the `publication` date

### Postgres is awkward to reach from a CronJob

Pods reach Postgres through a **per-pod pgbouncer sidecar** on `localhost:5432`
(`k8s_deploy/chart_server.yaml`). A cron job querying the database directly would have to
replicate that sidecar plus the `njord-pgbouncer-ini` and `njord-pgbouncer-userlist-txt` secrets.

### NOAA per-cell zip layout

```
ENC_ROOT/US5WA22M/US5WA22M.000    1778463
ENC_ROOT/US5WA22M/US5WA22M.001      20766
ENC_ROOT/US5WA22M/US5WA22M.002        420
ENC_ROOT/US5WA22M/US5WA22A.TXT       5663   (+ B, C, D)
ENC_ROOT/CATALOG.031                  1329   <- per cell, same path in every cell zip
ENC_ROOT/README.TXT                   2732   <- identical boilerplate
ENC_ROOT/USERAGREEMENT.TXT            5176   <- identical boilerplate
```

No directory entries at all. The per-cell layout is already exactly what `ChartIngest` wants.

---

## Design

### 1. Revision key

Charts are matched on an opaque string built identically on both sides:

```
"<DSID_UPDN>:<DSID_UADT>:<DSID_ISDT>"      e.g.  "6:20251209:20260520"
```

- **Server side** — `ChartDao.editionsAsync()` builds it from `dsid_props->>'DSID_UPDN'` plus the
  existing `charts.updated` (UADT) and `charts.issued` (ISDT) columns.
- **Client side** — `EncCatalogEntry.revisionKey` builds it from the `<edition>` suffix, the
  `revision` date and the `publication` date.

Properties that matter:

- **`DSID_EDTN` is excluded**, for the reason above.
- **Compared as opaque strings, never parsed as numbers.** `"1.10"` and `"1.1"` are distinct S-57
  states but equal as floats.
- **Any difference, in either direction, means re-fetch.** A locally-newer key is still a mismatch;
  there is no ordering.
- **Fails toward re-downloading.** A chart missing any of the three parts is omitted from the
  server's map; a catalog entry missing any part has a `null` key. Both are treated as stale.
  Wasted bandwidth is much cheaper than silently pinning a cell at a stale edition forever.
- **Needs no re-ingest.** `updated` and `issued` are already populated columns, so the key works
  against charts ingested before this change.

### 2. `GET /v1/chart_editions` (`server`)

```
{"editions": {"US1GC09M.000": "6:20251209:20260520", ...}}
```

- `shared/…/model/ChartItem.kt` — `ChartEditions(editions: Map<String, String>)`
- `server/…/db/ChartDao.kt` — `editionsAsync()`
- `server/…/endpoints/ChartEditionsHandler.kt` — registered in `ChartServerApp.kt`

Unpaginated, unlike `/v1/chart_catalog`: the caller needs the whole set to diff, and the whole set
is ~7k entries (~200 KB of JSON). Unauthenticated read, same as the catalog endpoint.

Routing the baseline through HTTP rather than SQL is what keeps the CronJob free of database
credentials and of a pgbouncer sidecar.

### 3. `libexpat` — new cinterop module

A generic streaming SAX facade over expat. Standalone module rather than an inline cinterop in
`enc_cron` so the tricky parts get their own `./gradlew :libexpat:archTest` with no network, GDAL
or Postgres in the loop.

```
libexpat/build.gradle.kts
libexpat/src/nativeInterop/cinterop/libexpat.def
libexpat/src/nativeMain/kotlin/ExpatSax.kt        # SaxHandler, ExpatSax, attsToMap, XmlAttrs
libexpat/src/nativeMain/kotlin/ElementPath.kt     # qualified-name stack with endsWith(tail)
```

Non-obvious details, each of which cost something to get right:

- **`noStringConversion = XML_Parse` in the `.def` is required.** cinterop maps `const char *s` to
  `String?` by default, leaving no way to feed a length-delimited, non-NUL-terminated buffer.
- **Callbacks route through `StableRef`.** `XML_SetUserData(parser, StableRef.create(this).asCPointer())`
  on the way in, `userData.asStableRef<ExpatSax>().get()` on the way out. Handlers are named
  top-level functions wrapped in `staticCFunction(::fn)` — lambdas fight the generated typealiases
  during inference. `selfRef.dispose()` in `close()` is mandatory; a `StableRef` is a GC root.
- **Handler exceptions are trapped, not propagated.** A Kotlin exception unwinding through a C
  frame terminates the process. Every callback body runs inside a `guard {}` that catches, calls
  `XML_StopParser`, and re-raises from the Kotlin side of `feed()`/`finish()`.
- **Character data is `(ptr, len)` UTF-8 and not NUL-terminated** — `s.readBytes(len).decodeToString()`,
  never `toKString()`. Expat never splits a multi-byte sequence across calls but does split text
  nodes at buffer boundaries, so values must be accumulated until `endElement`.
- **`feed()` early-returns on `length <= 0`.** `usePinned { addressOf(0) }` throws on an empty array.
- **Namespace processing is deliberately off** (`XML_ParserCreate`, not `XML_ParserCreateNS`).
  Handlers see raw qualified names like `gco:CharacterString`. NS mode would prefix every name with
  a 40-character URI for no benefit — the document binds its prefixes once on the root and never
  rebinds. The safety net is an assertion on the root element's `xmlns`/`xmlns:gco` bindings, so an
  upstream change fails loudly instead of silently yielding zero records.
- **macOS needs explicit `-I`/`-L`** because Homebrew's `expat` is keg-only. Link `-lexpat`, never
  `-lexpatw` (the `wchar_t` build, where `XML_Char` is not `char`).

### 4. `ZipWriter` — additive to `libzip`

`libzip/src/nativeMain/kotlin/ZipWriter.kt`. `ZipFile`/`ZipFileEntry` and their callers in
`ChartIngest.kt` / `ChartIngestWorker.kt` are untouched.

```kotlin
ZipWriter.create(file).use { w -> w.addFile(entryName, source) }   // sources read HERE, at close()
```

libzip semantics that had to be handled exactly:

- **`zip_source_file` is lazy.** It records a path; libzip opens and reads each file during
  `zip_close()`. Every staged source must still exist and be unmodified at that point. This single
  fact dictates the staging lifecycle in §5 — staging directories are deleted *after* `close()`
  returns, never between adds. `ZipWriterTest` pins it: add a file, delete it, expect `close()` to
  throw.
- **`zip_file_add` ownership is asymmetric.** On failure (`-1`) the caller must `zip_source_free`;
  on success libzip owns the source and freeing it is a double free.
- **`zip_close` is asymmetric too.** Returns 0 and leaves the pointer dangling; returns -1 with the
  archive still live and needing `zip_discard`. This is why `ZipWriter` is `AutoCloseable` rather
  than using `createCleaner` like `ZipFile` — a cleaner would swallow the error that says the
  bundle is corrupt and risk touching a freed pointer.
- **Compression is `ZIP_CM_DEFLATE` level 1, not `ZIP_CM_STORE`.** The staged inputs are raw S-57,
  not pre-compressed. Measured on `US1GC09M.000` (2,712,102 bytes): level 1 → 52% of original,
  level 6 → 50%. Level 1 captures essentially all of the benefit at a fraction of the CPU, and
  `save/` is a shared volume, so storing would roughly double what is written and re-read.

### 5. `enc_cron` — new executable module

```
enc_cron/src/nativeMain/kotlin/io/madrona/njord/enccron/
    Main.kt              # CLI, run flow, selectStale, batches
    EncCronConfig.kt     # config + ENC_CRON_OPTS overlay
    EncProdCatParser.kt  # EncCatalogEntry, SAX rules, parseCatalogFile
    EncHttp.kt           # catalog stream, editions fetch, cell download, retry
    BundleBuilder.kt     # download -> stage -> zip -> publish
    Log.kt
```

Declares no cinterops of its own; consumes `:libexpat`, `:libzip`, `:shared`, `ktor-client-curl`.
Because cinterop `linkerOpts` do not propagate to the final link step, the executable itself sets
`linkerOpts("-L/usr/lib/$multiarchTuple", "--allow-shlib-undefined")` so `-lexpat`/`-lzip` resolve.

#### Flow

1. `GET /v1/chart_editions` → the baseline. **Failure here aborts the run.** With no baseline every
   cell looks stale; silently falling back to "download everything" would hammer NOAA and flood the
   ingest queue.
2. Stream the catalog through `ExpatSax` + `EncProdCatParser`.
3. Diff: stale when `revisionKey == null || have[chartName] != revisionKey`. Sorted by cell name so
   a capped run makes the same progress every time rather than reshuffling which cells get deferred.
4. Take `maxCellsPerRun`.
5. For each batch: download cell zips, stage, bundle, publish. Stop early if `save/` already holds
   `maxQueuedBundles`.
6. Exit non-zero on hard failure so the CronJob's `restartPolicy: OnFailure` retries.

#### Catalog extraction rules

All path-scoped tails, indexed by leaf name so only a handful of element names ever run a check:

| field | element path tail |
|---|---|
| cell | `identificationInfo/MD_DataIdentification/citation/CI_Citation/title/gco:CharacterString` |
| edition | `…/citation/CI_Citation/edition/gco:CharacterString` |
| date | `citation/CI_Citation/date/CI_Date/date/gco:Date` |
| date type | `citation/CI_Citation/date/CI_Date/dateType/CI_DateTypeCode` |
| scale | `spatialResolution/MD_Resolution/equivalentScale/MD_RepresentativeFraction/denominator/gco:Integer` |
| size | `distributionInfo/…/MD_DigitalTransferOptions/transferSize/gco:Real` |
| url | `distributionInfo/…/MD_DigitalTransferOptions/onLine/CI_OnlineResource/linkage/URL` |

Scoping to `citation` rather than `sourceCitation` is what excludes the decoy `<title>` and the
lineage's `<date gco:nilReason>`.

A `CI_Date` holds its `<gco:Date>` **before** the `<dateType>` that identifies it, so the value is
parked in `pendingDate` and dispatched to `revisionDate`/`publicationDate` at `</CI_Date>`.

Record boundary is `MD_Metadata`. Records missing a required field are counted and skipped, not
thrown — one malformed record must not abort a 7,000-cell catalog.

#### HTTP

Reuses the streaming pattern from `EncUrlHandler.kt`: `HttpClient(Curl)`,
`prepareGet(url).execute { }`, 8 MB chunks, `.tmp` + rename.

- `Accept-Encoding: identity` is pinned on the catalog request. If a proxy returns undecoded gzip,
  expat fails with a baffling "not well-formed (invalid token)" at line 1.
- `HttpTimeout` is installed; without it a stalled 52 MB transfer hangs the job forever.
- Retry wraps the **whole** fetch-and-parse, not the read loop — a truncated body cannot be resumed
  mid-stream, so recovery means starting over with a fresh parser.
- **SAX handlers cannot suspend** (a C frame sits between `feed()` and the handler). Entries are
  collected into a list (~1 MB for 7k cells) and all network work happens after the parse.

#### Bundling

Bundling is **required, not an optimisation**: `ChartIngestWorker` claims exactly one zip per
distributed-lock cycle and polls every 5 s, so per-cell zips would mean one full
lock/extract/GDAL-open/insert/tile-cache-invalidate cycle per cell — 7,229 of them on a cold start.

Bundles stay small for the opposite reason. `ChartIngest.ingestInternal`:

- opens **every** `.000` in the bundle up front and holds them all alive for the whole run, and
- pre-counts features across all of them before inserting a single row.

So peak GDAL residency is O(bundle size). Default `bundleSizeCells = 50` (~20 MB uncompressed at
the measured mean cell size), with `maxBundleUncompressedBytes` as a secondary cap for batches that
happen to be large harbour cells.

Per cell: download → extract into a shared staging root **filtered to `ENC_ROOT/<CELL>/`** → delete
the cell zip. Once the whole batch is staged, write one `ZipWriter`, `close()`, *then* delete the
staging root, then publish by `renameTo` into `save/`.

Entry names pass through untouched, so the `.000`, its `.001…` updates and the sibling `.TXT` files
land as siblings — exactly the layout GDAL's S57 driver needs to apply updates. The filter drops
`ENC_ROOT/CATALOG.031` (per-cell, would collide across every cell in a bundle) and the duplicated
`README.TXT`/`USERAGREEMENT.TXT`. None is read by `OgrS57Dataset`, which opens the `.000` directly.

#### Configuration

`<resourcesDir>/config/enc_cron.json` overlaid with the `ENC_CRON_OPTS` environment variable as a
shallow JSON object merge — the same layering the server uses for `CHART_SERVER_OPTS`. Every field
has a default, so both layers are optional.

| key | default | purpose |
|---|---|---|
| `catalogUrl` | NOAA `ENCProdCat_19115.xml` | |
| `chartEditionsUrl` | `http://njord-chart-svc/v1/chart_editions` | baseline source |
| `chartTempData` | `/mnt/njord/charts` | `save/` and `enc_cron/` live under it |
| `maxCellsPerRun` | 500 | bounds a single invocation |
| `bundleSizeCells` | 50 | cells per bundle zip |
| `maxBundleUncompressedBytes` | 256 MB | secondary bundle cap |
| `maxQueuedBundles` | 3 | backpressure against `save/` |
| `scaleFilter` | `[]` (all) | restrict by compilation scale |
| `requestTimeoutSeconds` | 600 | |
| `maxRetries` | 3 | per HTTP resource |
| `deleteOrphans` | `true` | remove charts the catalog no longer lists |
| `maxOrphanDeletes` | 25 | ceiling above which the deletion pass is abandoned |
| `adminUser` / `adminPass` | `""` | basic auth for `GET /v1/admin`; prefer the env vars |

`ENC_CRON_ADMIN_USER` / `ENC_CRON_ADMIN_PASS` override the last two, so a Kubernetes secret can
supply the password on its own key rather than inside the `ENC_CRON_OPTS` JSON blob.

CLI: `enc_cron [resourcesDir] [--from-file <catalog.xml>] [--dry-run]`. `--from-file` parses a
catalog already on disk (same `ExpatSax` path); `--dry-run` reports the diff and downloads nothing.

### 6. Withdrawn cells

The diff is symmetric: `selectOrphans` reports every chart Njord holds that the catalog no longer
lists. NOAA cancels cells (a harbour re-cut into different coverage, a cell folded into its
neighbour), and nothing else in the pipeline notices — an ingested chart has no expiry.

Reporting is unconditional. Deleting is guarded twice, because "absent from the catalog" is also
what a truncated download looks like:

- `deletableOrphans` keeps only cells whose two-letter S-57 producer code appears somewhere in the
  catalog just parsed. NOAA publishes `US` only, so a chart ingested from another hydrographic
  office is never a candidate. The producer set is derived from the catalog rather than hardcoded,
  so pointing `catalogUrl` at a different office still behaves.
- `maxOrphanDeletes` abandons the whole pass if more cells qualify than a plausible night of
  withdrawals. Real cancellations arrive a handful at a time; thousands means the catalog is wrong,
  not that the charts are.

Deletion goes through `DELETE /v1/chart?name=<DSID_DSNM>`, a name-keyed path added alongside the
existing id-keyed one — enc_cron diffs on chart names and never learns Njord's row ids. The
handler does more than drop the row, since a deleted chart otherwise keeps being served from two
caches:

- the tile cache is invalidated, exactly as after an ingest;
- `region_export_state` is cleared for every region whose coverage contained the chart, and the
  export worker woken. `regionNeedsRebuild` only looks for charts *ingested* since the last export,
  so a deletion is invisible to it — the region archive would ship the withdrawn chart until
  something unrelated in that region happened to be re-ingested. Which regions those are has to be
  resolved before the delete, while there is still a coverage polygon to intersect. The `WORLD`
  base map is excluded: it embeds no chart data and is expensive to re-render.

Authorization reuses the existing admin signature flow rather than adding a second mechanism:
basic auth to `GET /v1/admin` yields a signature scoped to the base URL the server saw, which then
authorizes the deletes against that same base URL.

### 7. Deployment

Same image, new entrypoint — mirroring how `ingest.kexe` is handled.

- `Containerfile` — `libexpat1-dev` in the builder stage, `libexpat1` at runtime,
  `:enc_cron:linkReleaseExecutableArch` appended to the gradle line, `enc_cron.kexe` and
  `enc_cron_resources/` copied in.
- `debpkg/build_deb.py` — `libexpat1` added to `DEPENDS`.
- `k8s_deploy/noaa_enc_daily_cron.yaml` — the `curlimages/curl` container is replaced by
  `ghcr.io/manimaul/njord-chart-server:latest` running `/opt/njord/enc_cron.kexe`. Same schedule,
  same `concurrencyPolicy: Forbid`, same PVC mount, same CronJob name (`njord-enc-download`) so the
  existing `kubectl create job --from=cronjob/...` recipes still work. No pgbouncer sidecar, no
  database secrets.

---

## Verification

Every link in the chain was exercised against real NOAA data rather than fixtures alone.

**Unit tests** — 173 across the repo, 0 failures. New coverage:

| suite | tests | notable cases |
|---|---|---|
| `libexpat` `ExpatSaxTest` / `ElementPathTest` | 14 | decoy `<title>` rejection; text node split mid-word across two `feed()` calls; UTF-8 multi-byte chunking; handler exception surfacing |
| `libzip` `ZipWriterTest` | 7 | lazy-source pin (delete before `close()` must throw); duplicate entry overwrite; Store vs DeflateFast |
| `enc_cron` `EncProdCatParserTest` / `StaleSelectionTest` / `BatchingTest` | 25 | dates told apart by `dateType`; `revisionKey` excludes `EDTN`; string comparison so update 10 ≠ update 1; namespace-change assertion |
| `server` `ChartEditionsTest` | 5 | an `EDTN` of zero does not affect the key; UPDN of 0 round-trips; partial rows omitted |

**Against the live catalog.** `--from-file` over the real 52 MB document parses exactly **7,229**
records, none named `0`.

**End-to-end.** Real cells were downloaded, bundled, and ingested through `ingest.kexe` into
PostGIS (`CompletionReport(totalFeatureCount=8586, totalChartCount=2, …)`). Querying the resulting
rows:

```
 name         | edtn | key
--------------+------+----------------------
 US1EEZ3M.000 | 2    | 4:20131021:20200828
 US1GC09M.000 | 0    | 6:20251209:20260520
```

`US1GC09M` really does land in the database with `DSID_EDTN = 0`, and its key matches exactly what
`enc_cron` derives from the catalog's `74.6` / rev 2025-12-09 / pub 2026-05-20. A dry run with
those two keys as the baseline reports **7,227 of 7,229 stale** — both ingested cells correctly
recognised as current. Under the original `EDTN`-based design, `US1GC09M` would have been stale.

Bundle contents were checked to contain only `ENC_ROOT/<CELL>/` entries, and `ogrinfo` confirmed
GDAL applies the bundled `.001`–`.006` updates (`UPDN = 6`).

---

## Operational notes

**Cold seed.** With `maxCellsPerRun = 500` an empty catalog converges over ~15 nightly runs
(7,229 cells / ~818 MB). Raise it temporarily via `ENC_CRON_OPTS` for a one-off seed. `maxQueuedBundles`
independently stops the job from dumping ~145 bundles into `save/` faster than ingest can drain them.

**Steady state.** A typical nightly delta is a handful to a few dozen cells — usually one bundle —
so the caps never bind.

**Failure modes.**

- Editions endpoint unreachable → run aborts, downloads nothing, exits non-zero.
- A single cell 404s → logged and skipped; the rest of the batch proceeds. Because nothing is
  recorded locally, the cell is simply retried next run.
- Truncated catalog → retried from scratch, up to `maxRetries`.
- NOAA changes its namespace bindings or root element → immediate, named failure rather than a
  silent zero-record run.

**Debugging.** `--dry-run` shows the diff without fetching; `--from-file` avoids re-downloading
52 MB on every iteration.

---

## Alternatives considered

**Local JSON state file on the PVC** instead of `/v1/chart_editions`. Zero server changes, but the
state records what was *downloaded*, not what was *ingested* — a failed ingest would leave the cron
permanently convinced the cell is current. The HTTP endpoint is authoritative and self-heals.

**Direct Postgres access from the CronJob.** Equally authoritative, but requires replicating the
pgbouncer sidecar and two secrets into the job pod.

**Hand-rolled XML parser** instead of a libexpat cinterop. Avoids a fifth cinterop module, but the
document is 52 MB and entity/encoding edge cases are exactly where a hand-rolled scanner goes
wrong quietly. expat is already pulled in transitively by GDAL, so the runtime cost is ~zero.

**`zip_source_zip` raw stream copy** instead of extract-then-add. It would copy each entry's
deflate stream with no decompress/recompress and no staging to disk — genuinely attractive. Rejected
because its behaviour has shifted across the four libzip versions this project builds against (1.7
on dev boxes, 1.9 in the Debian container, 1.10 for the `.deb`, 1.11 on brew), and the wall-clock
saving is negligible: a nightly delta is single-digit MB. Worth revisiting only if cold seeding
becomes a bottleneck.

**Per-cell zips straight into `save/`.** No `ZipWriter` needed, but one full ingest cycle per cell.

---

## Possible future work

- **Record the true base-cell `EDTN` at ingest** by opening the `.000` a second time with
  `UPDATES=OFF` in `OgrS57Dataset`. Would make the edition directly available for display and
  reporting. Not needed for change detection — the current key is sufficient — and it would require
  a full re-ingest to backfill.
- **Stream `parseCatalogFile` from disk** rather than reading the whole 52 MB into a `ByteArray`.
  Only affects the `--from-file` debug path; the network path already streams.
- **Per-region tile cache invalidation.** Deleting a withdrawn chart currently drops the whole
  tile cache, the same blunt instrument ingestion uses. Fine at the rate withdrawals actually
  happen; wasteful if deletions ever become frequent.

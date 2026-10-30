# HTTP conformance suite

Black-box tests of the Njord HTTP/WebSocket API, written once in Python so they are independent
of both the Kotlin server and the Rust port ([RUST_PORT_PLAN.md §5.5](../docs/RUST_PORT_PLAN.md#55-http-conformance-suite-conformance),
checklist [§3.5](../docs/RUST_PORT_PHASE0_CHECKLIST.md)). Nothing here imports from `server/`,
`shared/` or `crates/`; the only Njord-specific inputs are the fixture cells under
`testdata/fixtures/` (uploaded through the API) and, when present, the P-SY-7 style fixtures and
the P-TE-5 tile list under `testdata/`.

```sh
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt     # Python >= 3.12
.venv/bin/pytest . --base-url=http://localhost:9000 --record             # record goldens from the Kotlin server
.venv/bin/pytest . --base-url=http://localhost:9000                      # verify a server against golden/
.venv/bin/pytest . --base-url=http://localhost:9000 --compare-url=http://localhost:9001   # diff two live servers
.venv/bin/pytest . --base-url=https://openenc.com                        # read-only verify of production
```

## Modes

| Mode | Selected by | What happens |
|---|---|---|
| **record** | `--record` | Every spec is fetched from `--base-url` (a **Kotlin** server) and written to `golden/<group>/<case>.json`. `port_only` tests are skipped. |
| **verify** | default | Every spec is fetched and compared with its golden. Fails on any difference, naming the JSON pointer. |
| **diff** | `--compare-url` | Every spec is fetched from both servers and the two masked responses are compared with each other; goldens are not touched. Read-only: mutating tests are skipped, because the two servers may share a database. |

Options: `--golden-dir` (default `conformance/golden`), `--compare auto|exact|shape` (see below),
`--admin-user/--admin-pass/--admin-key` (default `admin`/`admin`/`changeme` from
`config/application.json` for a localhost target, `$NJORD_ADMIN_USER`/`$NJORD_ADMIN_PASS`/
`$NJORD_ADMIN_KEY` otherwise), `--allow-mutating` (never accepted for openenc.com),
`--server-impl auto|kotlin|rust`, `--trust-forwarded`, `--skip-seed`.

## Layout

```
conformance/
├── conftest.py            options, session fixtures (client, signature, seeded, checker), marker handling
├── lib/
│   ├── client.py          requests.Session per server; admin login (GET /v1/admin basic auth); signed calls;
│   │                      raw uploads; verbatim-path requests for traversal probes
│   ├── engine.py          Spec -> capture -> record / verify / diff; golden file format
│   ├── mask.py            JSON-pointer masking, {BASE} normalisation, layer-id normalisation, shape(), differences()
│   ├── tiles.py           gunzip, mapbox_vector_tile.decode, canonical sort, tile maths
│   ├── ws.py              websockets.sync client for /v1/ws/enc_process; ordered WsMsg list
│   ├── sigs.py            admin signatures minted locally (HMAC-SHA256 over baseUrl‖date‖uuid)
│   └── seed.py            ingests testdata/fixtures through POST /v1/enc_save; waits for region exports
├── test_api_01_about.py … test_api_12_errors.py    one module per P-API row
├── test_api_99_nuke.py    DELETE /v1/nuke, last because everything else needs the data; re-seeds
└── golden/<group>/<case>.json
```

A golden holds the request, its masking list, the comparison policy and the masked response:

```json
{ "request": { "method": "GET", "path": "/v1/regions", "kind": "json", ... },
  "masks": ["/*/archive", "/*/archiveSize", "/*/archiveSha256", "/*/createdAt"],
  "shape_depth": 3, "data_dependent": true, "volatile": false,
  "port": null,
  "expected": { "status": 200, "content_type": "application/json", "headers": {}, "body": [...] } }
```

`port` carries the Rust port's divergent expectation for an accepted Kotlin defect
([TEST_COVERAGE_AUDIT.md §3.1](../docs/TEST_COVERAGE_AUDIT.md#31-decisions-2026-10-30)), e.g.
`{"decision": "D8", "status": 400}`; it replaces the recorded fields when `--server-impl` is (or
auto-detects as) `rust`. Tests that can only pass on the port are marked `port_only` and skipped
against a Kotlin server. Auto-detection reads `GET /v1/about/version`: the server is Kotlin unless
the body has `"implementation": "rust"` or `rust` appears in `version`.

## Masking policy

Masks are JSON pointers (RFC 6901) with `*` matching every list index or object key, applied to the
parsed body before it is stored or compared; the server's external base URL is replaced by `{BASE}`
in every string. The mask list is stored in the golden, so a new volatile field is a visible,
reviewed decision rather than a silent skip. Masked today:

| Route | Masked | Why |
|---|---|---|
| `about/version` | every field | identifies the build |
| `admin`, `admin/verify` | `signature.*`, `signatureEncoded` | random nonce and clock |
| `feature/layer/*`, `feature/lnam/*` | `lastId`, `items[*].id`, `id` | database sequence values |
| `regions` | `archive`, `archiveSize`, `archiveSha256`, `createdAt` | change with every render |
| `cache` | whole body (`volatile`) | per-zoom counts depend on request history |
| `regions/{archive}` | bytes (`volatile`) | re-rendered archives differ; status and headers are compared |
| unknown routes, non-integer tile params | body (`html`) | the SPA index page of the deployment |
| `style/*` | layer-id counter | see "Kotlin behaviour pinned as observed" |
| ingest `CompletionReport` | `ms` | wall clock |

Response **kinds** decide how a body is reduced: `json` (parsed, masked), `style` (plus layer-id
normalisation), `geojson`/`info` (features sorted by layer and `LNAM`, since the database returns
them in storage order), `tile` (decoded with `mapbox_vector_tile`, layers sorted, features sorted by
`LNAM`/props/geometry, encoder ids dropped; raw sha256 and size recorded, never asserted), `bytes`
(size + sha256), `text`, `html` (status and content type only).

## Exact versus shape comparison

Two servers can only be byte-compared when they hold the same data. Each spec says whether it is
**data-dependent** (chart, catalog, editions, features, GeoJSON, tiles, region manifest). With
`--compare auto` (default) a data-dependent golden is compared **exactly** when the target's
`GET /v1/chart_editions` equals the recording's, and by **shape** otherwise: status, content type and
the structural skeleton (object keys, list element shape, scalar types) down to the spec's
`shape_depth`. Everything that is not data-dependent is always exact:

| Exact everywhere | Data-dependent (exact locally, shape against another catalog) |
|---|---|
| `about/s57objects`, `about/s57attributes`, `about/expectedInput`, `about/colors`, `about/colors/custom` | `chart?name=US5WA22M.000`, `chart_catalog`, `chart_catalog?name=` |
| `style/{depth}/{theme}[?c=]` for all 27 combinations, after `{BASE}` and layer-id normalisation | `chart_editions`, `feature/layer/*`, `feature/lnam/*` |
| `tile_json`, `content/*`, `icon/*`, `sector_svg/*` | `tile/{z}/{x}/{y}` (decoded), `tile/…?info=true` |
| every mutating route × {no, wrong signature} → 401; `admin` 401s; 405/404/CORS | `geojson?chart_name=…` (existing layer), `regions` manifest |
| `enc_save`, `chart`, `geojson` POST, `cache` DELETE, `regions` POST responses (local only) | |

So `pytest . --base-url=https://openenc.com` runs the exact routes byte-for-byte and the data
routes by shape, because production holds the whole NOAA catalog; a local server seeded with the
three fixture cells compares everything exactly. `--compare exact|shape` overrides the automatic
choice. `volatile` specs (counts, archive bytes, the SPA page) are always compared by shape.

Diff mode (`openenc.com` vs `njord.mxmariner.com` in §10.5 of the plan) applies the same rule
between the two live catalogs, so catalog drift shows up as a per-route failure of the data routes,
never as a false alarm on the exact routes.

## Markers

- `mutating` — changes server state (ingest, chart/geojson POST, cache DELETE, regions POST, nuke).
  Runs only when `--base-url` is localhost, or with `--allow-mutating` (refused for openenc.com).
  Always skipped in diff mode.
- `signed` — needs an admin signature; skipped when no credentials are available.
- `port_only` — passes only on the Rust port (D2 traversal filename, D6 upload during ingest, D9).
- `local_only` — `X-Forwarded-*` reflection, which a production proxy overrides (`--trust-forwarded`).

## Seeding and the P-API-8 ingest

The `seeded` session fixture checks `GET /v1/chart_editions` for `US5WA22M.000`, `US4ZZ01M.000`
and `US5ZZ02M.000` and, on a local server, ingests the missing ones through the API itself
(`lib/seed.py`: zip `testdata/fixtures/<cell>/*` as `ENC_ROOT/<cell>/…`, `POST /v1/enc_save`,
follow `/v1/ws/enc_process` to the `CompletionReport`, `DELETE /v1/enc_save`). Data tests skip when
the real cell is absent and cannot be seeded; tests that need the synthetic cells skip when those
are absent (production never has them). `test_api_99_nuke.py` wipes everything last and re-seeds.

Ingests wait for the region export worker to go quiet before uploading and for every region to be
re-rendered afterwards (`seed.wait_for_exports_to_settle` / `wait_for_exports_after`): the Kotlin
server shares one lock between ingest and export and clears it without checking ownership, so an
upload during an export aborts the export and the export's cleanup then aborts the ingest (D6).

## Kotlin behaviour pinned as observed (candidate defects)

Recorded as the Kotlin server does it; none of these has a port decision in TEST_COVERAGE_AUDIT.md
§3.1 yet, so the goldens carry no `port` field for them:

1. **Style layer ids are not stable.** `Layerable.nextId` is never reset and `LayerFactory.layers`
   re-evaluates its lazy `Sequence` on every call, so `DEPARE_fill_1` becomes `DEPARE_fill_17` on
   the next style request. `lib/mask.normalize_style` rewrites the counter to the layer's ordinal
   among same-named layers (`DEPARE_fill#1`); layer order and everything else stay exact.
2. **`GET /v1/geojson?chart_name=&layer_name=` answers 404 for every layer that has features**
   while an unknown chart (zero rows) gets an empty `FeatureCollection`. `GeoJsonDao.fetchAsync`
   decodes its `row_to_json` rows into `geojson.Feature`, whose `id` is `String?` while the row's
   `id` is numeric and whose serializer rejects the row's extra `layer` key; the DAO retries exhaust
   (about two seconds of back-off) and the handler sees null. Reproduced on openenc.com too.
3. **`GET /v1/style/{bad depth}/…` and `/{bad theme}` answer 500**, not the intended 404
   (`call.respond(NotFound, Any())` fails to serialise).
4. **An aborted ingest leaves `status.json` at its last progress message**: `IngestStatus.writeMsg`
   needs the lock the abort just lost, so `Idle` is never written and `/v1/ws/enc_process` reports a
   running ingest until the next `DELETE /v1/enc_save`.
5. **Unknown routes and non-integer tile coordinates answer 200 `text/html`** (the SPA index via
   Ktor's `unhandled` page), never 404; the audit's "non-integer stays 404" for D8 is actually this.
6. `CompletionReport.totalFeatureCount` (5163 for US5WA22M) exceeds the per-chart `items` count
   (5158): features whose insert returned 0 rows are counted in the total, not per chart.

The goldens record the build they came from in `golden/_recorded_build.json`; verify mode prints
a warning when the target runs another build, because the exact routes (style, sprites) then
legitimately differ. Against `https://openenc.com` (master `505032bc`, 2026-10-18) everything
passes except the 28 style and 4 sprite goldens recorded from this branch, which carries the
drying-height sounding style and a regenerated sprite sheet; they will pass once production runs
the same build, or when the goldens are recorded from production's build.

Already decided divergences recorded with `port` fields or `port_only` tests: D2 (traversal upload
filename), D4 (malformed signature → Kotlin 500, port 401), D6 (upload during ingest), D8
(out-of-range tile → Kotlin 200 empty tile, port 400).

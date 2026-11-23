# Support for license-encumbered cartography

Status: a plan. Nothing here is built. The code references were audited against the Rust
workspace (`crates/`) on 2026-11-23.

## Context

Njord ingests and serves cartography that is in the public domain in the United States. It was
designed for NOAA Electronic Navigational Chart (ENC) data, which NOAA dedicates to the public
domain (https://nauticalcharts.noaa.gov/data/data-licensing.html). Njord has no support for
license-encumbered data: every chart's S-57 feature data is served to anyone who asks.

This document describes how njord serves such data only to authorized users, while leaving
authentication and authorization to a separate server. Njord stays stateless about users: it
verifies an entitlement claim attached to a request and never manages accounts.

Licensed data leaves njord by two paths, each with its own gate:

- **Online** (`/v1/tile` and the other routes that return feature data): a restricted chart's
  features are refused unless the request carries an admin signature, or a token that names an
  unexpired licensed region the tile lies in.
- **Offline region archives** (`/v1/regions/{archive}`): njord encrypts every tile of a licensed
  region when it exports it, and a separate key issuer gives the key to purchasers. Njord's part
  is in [Offline region archives](#offline-region-archives). The whole design (key issuance,
  device key storage, expiry) is MX Mariner's `docs/explorations_unimplemented/encryption.md`.

A chart's coverage outline and its metadata stay public on both paths.

## Order of work

The three stages are independent releases. The first must be deployed before any licensed cell is
ingested.

1. **Restriction, refusal and admin access.** The `restrictedProducers` configuration, the
   `charts.restricted` column, every route that returns feature data refusing restricted charts
   to a request without an admin signature, and the tile cache keyed by what a request may see
   ([Configuration](#1-configuration) to
   [The coverage outline's color](#8-the-coverage-outlines-color), without the token half of
   [Request access](#6-request-access)). No token exists yet, so the admin is the only one who
   sees a restricted chart online.
2. **Encrypted archives.** The `restricted` flag on a region, the export's two chart sets, the
   sealed blobs and `producerCodes` ([Offline region archives](#offline-region-archives)).
   Requires stage 1.
3. **Region tokens.** JWT verification as a second source of what a request may see, and the
   CORS change that lets a browser send the token ([Request access](#6-request-access),
   [Clients](#9-clients)). Requires stage 1. Offline archives do not need it.

## What the code does today

These are the facts the plan builds on.

- **Charts are keyed by name.** `charts.name` (`DSID_DSNM`, such as `US5WA22M.000`) is the
  primary key, and `features.chart_name` references it. There is no numeric chart id.
- **The producer is already stored.** `charts.dsid_props` is the cell's `DSID` record as JSONB,
  and `DSID_AGEN` in it is a JSON number: the IHO producer code of the hydrographic office that
  produced the cell (550 in the NOAA fixtures).
- **One function decides which charts a tile draws.** `chart_inputs` in
  `crates/njord-server/src/tile_encoder.rs` asks `Db::find_chart_info` for the charts whose
  coverage intersects the tile, smallest scale first, and fetches the features of those that pass
  `eligible` (`0 ≤ chart.zoom ≤ z`). `add_charts` then draws each eligible chart and subtracts its
  coverage from the area still to fill, so a smaller-scale chart never paints under a larger-scale
  one. Every intersecting chart, eligible or not, contributes its coverage boundary to the `PLY`
  layer, with no properties.
- **Three entry points share that function.** `tile` (`/v1/tile/{z}/{x}/{y}`), `info` (the same
  route with `info=true`) and `archive_tile` (the region export).
- **The tile cache is keyed by `z/x/y` alone.** `TileCache`
  (`crates/njord-server/src/tile_cache.rs`) stores `<chartTempData>/tiles/{z}/{x}/{y}.mvt`. Only
  the tile handler reads and writes it; `info` and the region export never touch it. `counts`
  (`GET /v1/cache`) counts the files under each directory whose name is an integer.
- **HMAC-SHA256 is already a dependency.** `crates/njord-server/src/signature.rs` signs and
  verifies admin signatures with the `hmac`, `sha2`, `subtle` and `base64` crates, and compares in
  constant time.
- **A request extractor guards the admin routes.** `Signed`
  (`crates/njord-server/src/handlers/mod.rs`) rejects a request without a valid admin signature.
- **CORS allows one request header.** `ALLOW_HEADERS` in `crates/njord-server/src/cors.rs` is
  `Content-Type`; a cross-origin preflight asking for `Authorization` is answered with an empty
  403.
- **The region export** (`crates/njord-server/src/regions/export.rs`) takes the charts whose
  coverage intersects the region (`Db::find_charts_in_region`), writes them as the archive's
  `charts` catalog, derives the candidate tiles and the `chart_tiles` edges from those charts'
  features, renders each candidate with `archive_tile`, and stores it gzipped. An archive is named
  `<region>_<yyyy-MM-ddTHH-mm-ss>.mbtiles` in the server's local time. The manifest
  (`Regions::manifest`, `GET /v1/regions`) is rebuilt from the archive directory on every call and
  lists each region's newest archive; the two newest are kept on disk (`Regions::MAX_ARCHIVES`).
- **A region is a name and a polygon.** `regionExports[].name` identifies it in the manifest and
  in archive file names, and `regionExports[].coverage` is its WKT polygon, which
  `Regions::manifest` already parses (`wkt_geometry` in `crates/njord-server/src/regions/mod.rs`).
- **A region is re-exported when a chart in it is written.** `Db::region_needs_rebuild` compares
  each intersecting chart's `ingested_at` with the region's `region_export_state.exported_at`.
  Deleting a chart, and `POST /v1/regions?name=`, clear that state.
- **Secrets are configuration keys.** `adminKey` and `adminPass` arrive in the same JSON as every
  other setting (`application.json` with `CHART_SERVER_OPTS` merged over it), delivered by
  `cargo xtask secrets`.

## Decisions

- **Auth transport.** A signed JWT issued by the external server and verified locally. Tile
  requests come in bursts of dozens per pan or zoom, so a network call per tile is ruled out.
- **Restriction is per chart, from its producer.** Njord is configured with the IHO producer
  codes whose charts are restricted (`restrictedProducers`). A chart whose `DSID_AGEN` is in that
  list is restricted; every other chart is public. There is no per-chart admin switch.
- **Access is two links.** A user holds regions, each until its own expiry. A region holds
  producer codes. The key issuer owns the first link, because it knows who bought what and until
  when. Njord owns the second, because it knows what a region contains. The token carries only
  the first: region names with an expiry each. It names no producer, chart or tile.
- **A licensed region holds every restricted producer.** That is what a region's `restricted`
  boolean means, online and offline. There is no per-region list of producers, so two licensed
  regions always show the same charts in a tile they share.
- **Online, a region is a place.** A token's region applies to the tiles its coverage
  intersects, at every zoom, and nowhere else.
- **Each region in a token expires on its own.** A user's regions were bought at different
  times, so one token-wide expiry cannot describe them.
- **The admin sees everything.** A request with a valid admin signature may see every restricted
  chart, on every route that returns feature data. The admin already ingests the source cells
  and downloads the archives.
- **The raw feature routes are admin only.** `GET /v1/geojson` and `GET /v1/feature` require an
  admin signature for every chart, public or restricted. They return source features unclipped
  and unstyled, and no viewing path needs them.
- **A tile request without a signature or a valid token is not rejected.** It is served the
  public tile. Anonymous browsing must keep working on the tile route.
- **Offline export has no request to authorize.** Each region definition carries a boolean saying
  whether it includes restricted charts. A region that does is a licensed region: it holds the
  charts of every restricted producer, and every tile blob in its archive is encrypted with a key
  unique to that archive. A region that does not holds no restricted chart and keeps today's
  format. Every purchaser downloads the same ciphertext.
- **The secret is symmetric.** HS256 with a secret shared between njord and the external server.
  Njord must never expose a route that mints or returns a token.

## Interface with the key issuer

Njord's contract is with one external server, the key issuer: the server that sells access,
validates purchases and gives purchasers their keys. In MX Mariner's
`docs/explorations_unimplemented/encryption.md` that is its backend. Njord knows nothing about
users, purchases or expiry.

**What crosses the boundary.**

| What | How | Used for |
| --- | --- | --- |
| The region manifest | `GET /v1/regions`, public | Each region's newest archive: its file name, size, SHA-256, creation time and `producerCodes` |
| A region archive | `GET /v1/regions/{archive}`, with an admin signature from `GET /v1/admin` | The issuer relays the archive to purchasers byte for byte |
| `regionKeyMaster` | A secret both hold | Each derives an archive's key from its file name, so no key ever crosses the boundary ([Keys](#keys)) |
| `chartAccessJwtSecret` | A secret both hold | The issuer mints online tokens, and njord verifies them |

Njord has no route that returns a key, mints, refreshes or inspects a token, or records a sale.

**Region names are the common unit.** `regionExports[].name` is the name in the manifest, in
archive file names and in a token. The issuer maps what it sells to region names; njord maps
region names to coverage and producers. Producer codes cross the boundary only in the manifest's
`producerCodes`, which the issuer uses to calculate licensing usage.

**One purchase, two deliveries.** MX Mariner sells one subscription per licensed region. The
purchase earns the region's archive key, for offline use, and a place for the region in the
user's online token. The two end at different times. The token's entry ends when the
subscription expires. The installed archive stays usable for a grace period after that, until
the app uninstalls it (`encryption.md` §10 and §14). Nothing in the offline design depends on
the token.

### The online token

The issuer mints HS256 JWTs with `chartAccessJwtSecret`, and njord verifies them locally. It
mints one when it has verified a user's subscriptions: after a purchase, and when the user's
client starts or signs in.

**The payload**, for a user who holds two licensed regions bought at different times:

```json
{
  "sub": "user_8f3a1c",
  "iss": "https://accounts.example.com",
  "iat": 1753100000,
  "exp": 1785542400,
  "regions": [
    { "name": "REGION_A", "exp": 1785542400 },
    { "name": "REGION_B", "exp": 1769904000 }
  ]
}
```

- `regions[].name` is a configured region's name.
- `regions[].exp` is when the user's access to that region ends, in seconds since the epoch like
  the JWT's own `exp`. For MX Mariner it is the subscription's expiry, with no grace period.
- The token's `exp` is the latest of its regions' `exp`. After it the token grants nothing, so
  njord need not read it further.
- A name njord does not know, and the name of a region that is not licensed, add nothing.
- The compact form is `base64url(header).base64url(payload).base64url(HMAC-SHA256(...))`, with
  the header `{"alg":"HS256","typ":"JWT"}`.

**The request.** The client sends the token in a header, never as a query parameter, so it stays
out of logs and cache keys:

```
GET /v1/tile/10/164/356 HTTP/1.1
Host: njord.mxmariner.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...<signature>
```

- When the tile intersects the coverage of a licensed region the token names, and that region's
  `exp` has not passed, the restricted charts in the tile are drawn with their features.
- Otherwise each restricted chart contributes its `PLY` outline alone, as it does for an
  anonymous request.
- A missing or tampered token, and one whose regions have all expired, behave exactly as no
  token.

**What a region's tiles hold online is what its archive holds.** An archive tile of a licensed
region draws every restricted chart within the tile's bounds
([What a tile and the catalog hold](#what-a-tile-and-the-catalog-hold)), and so does the online
tile. At low zoom a tile is wide, so a region's token shows restricted charts well beyond its
coverage polygon there, exactly as the region's archive does.

**A token cannot be revoked.** Njord verifies it with no call to the issuer, so it is honored
until each region's `exp`. Online tiles are always the current data, so a region's `exp` must
not be later than the user's right to chart updates: the issuer sets it to the subscription's
expiry, and a lapsed subscription then has no online access to end early. A refunded purchase
does keep its online access until that expiry. An issuer that cannot accept that mints
shorter-lived tokens and refreshes them, at the cost of the user needing a network to the issuer
more often.

## Implementation

### 1. Configuration

`ChartsConfig` and `RegionExportConfig` are in `crates/njord-server/src/config.rs`. Every new key
has a default, so an existing `application.json` keeps loading.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `restrictedProducers` | array of integers | `[]` | The IHO producer codes (`DSID_AGEN`) whose charts are restricted |
| `regionExports[].restricted` | boolean | `false` | Whether the region includes restricted charts, which makes it a licensed region |
| `regionKeyMaster` | string, base64 of 32 bytes | absent | The master secret region archive keys are derived from ([Offline region archives](#offline-region-archives)) |
| `chartAccessJwtSecret` | string | absent | The HS256 secret shared with the key issuer, for online tokens |
| `chartAccessJwtIssuer` | string | absent | The expected `iss` claim; not checked when absent |

- The two secrets are delivered like `adminKey`, through the secret `cargo xtask secrets` writes.
  Neither may appear in the `application.json` that is checked in.
- `ChartsConfig` derives `Debug`. The two secrets need a field type whose `Debug` does not print
  the value.
- `njord-ingest` runs the export worker with the same configuration, so `regionKeyMaster` must
  reach whichever process exports.
- With `chartAccessJwtSecret` absent, no token is ever valid, and only a request with an admin
  signature may see a restricted chart. That is stage 1.
- `restrictedProducers` must be set **before** a cell from one of those producers is ingested.
  Archives and cached tiles made earlier hold the cell's data in the clear until they are replaced
  (see [Reconciling at startup](#reconciling-at-startup)).

### 2. Schema: `charts.restricted`

`crates/njord-db/src/migrations.rs`:

- Migration 5, and the same column in `INITIAL_SCHEMA`, since a fresh database and a migrated one
  must end with the same schema (`crates/njord-db/tests/migrations.rs`):

  ```sql
  ALTER TABLE charts ADD COLUMN IF NOT EXISTS restricted BOOLEAN NOT NULL DEFAULT false;
  ```

- `CURRENT_VERSION` becomes 5.

**Writing the column.** Restriction is not ingested. It is derived from the row's own
`dsid_props` and the configured list, in the one statement that writes a chart row (the
`INSERT INTO charts` in `crates/njord-db/src/charts.rs`, which both ingest and `POST /v1/chart`
go through):

```sql
restricted = COALESCE((dsid_props->>'DSID_AGEN')::int = ANY($n), false)
```

A chart with no `DSID_AGEN` is public. `Db` is given the list when it is built, so no call site
passes it and no writer can forget it. `ChartInsert` does not change.

#### Reconciling at startup

After `migrate`, each process sets the column for every chart from the configured list:

```sql
UPDATE charts
SET restricted = COALESCE((dsid_props->>'DSID_AGEN')::int = ANY($1), false)
WHERE restricted IS DISTINCT FROM COALESCE((dsid_props->>'DSID_AGEN')::int = ANY($1), false);
```

When it changes a row, the configured list differs from the one the stored data was made with,
and everything derived from the old list is stale:

- clear the tile cache (`TileCache::clear`);
- clear the export state of every region except `WORLD` (`Db::clear_region_export_state`) and
  wake the export worker.

The statement is idempotent, so replicas starting together are harmless. It is a safety net, not
a procedure: between the restart and the end of the re-export, the manifest still lists an
archive made under the old list.

### 3. Models

`crates/njord-model/src/chart.rs` and `region.rs` (the contract with the web UI and other
clients), and `crates/njord-db/src/charts.rs`:

- `njord_db::ChartInfo` gains `restricted: bool` and `producer: Option<i32>` (the chart's
  `DSID_AGEN`), both selected by `Db::find_chart_info`, so the tile encoder filters without a
  second query.
- `njord_model::Chart` gains `restricted: bool` (`CHART_COLUMNS` and `chart_from_row`), so
  `GET /v1/chart?name=` tells a client the chart needs a license. Its producer is already there,
  in `dsid_props`.
- `njord_model::RegionManifestEntry` gains `producer_codes`
  ([Offline region archives](#offline-region-archives)).

`njord-model` must still build for `wasm32-unknown-unknown`.

### 4. The tile encoder

`crates/njord-server/src/tile_encoder.rs`. One value, `allowed_producers`, is threaded from the
three entry points into `chart_inputs` and `add_charts`. It is the set of restricted producer
codes whose charts may be drawn:

| Entry point | `allowed_producers` |
| --- | --- |
| `tile` | What the request may see in this tile ([Request access](#6-request-access)): every restricted producer for the admin and, in stage 3, for a token with a region the tile lies in; otherwise empty |
| `info` | The same set as `tile` for that request |
| `archive_tile` | Every code in `restrictedProducers` for a licensed region; empty for a public one |

- `eligible` becomes `0 ≤ chart.zoom ≤ z` **and** the chart is public or its `producer` is in
  `allowed_producers`. It is called in both `chart_inputs` (which features are fetched) and
  `add_charts` (which charts are drawn), and the two must agree.
- In `add_charts`, drawing a chart and subtracting its coverage from the area still to fill sit
  under the same `eligible` test. That is required: a restricted chart that is not drawn must not
  claim its area, or the tile would have a hole where smaller-scale public charts and the base
  map should show.
- The `PLY` boundary is still added for every intersecting chart. Its properties change from
  empty to `{"restricted": <chart.restricted>}`. The value is the chart's flag, not the
  requester's access.
- `archive_tile` keeps its emptiness test (`has_features(&["PLY"])`): a tile whose only content
  is outlines is still left out of an archive.

### 5. Routes that return feature data

Every route that returns S-57 feature data must refuse restricted charts to a request that may
not see them. This is the complete list, from the router in `crates/njord-server/src/lib.rs`:

| Route | Handler | Returns | Change |
| --- | --- | --- | --- |
| `GET /v1/tile/{z}/{x}/{y}` | `handlers/tile.rs` | The vector tile | `allowed_producers` from the request ([The tile encoder](#4-the-tile-encoder)) |
| `GET /v1/tile/{z}/{x}/{y}?info=true` | `handlers/tile.rs` | The tile's chart features as GeoJSON | The same set as the tile |
| `GET /v1/geojson?chart_name=&layer_name=` | `handlers/geojson.rs`, `Db::geojson_layer` | Every feature of one layer of one chart | Signed: the handler takes `Signed` |
| `GET /v1/feature/layer/{layer}` | `handlers/feature.rs`, `Db::find_layer_positions_page` | Position and full props of a layer's features, across all charts | Signed: the handler takes `Signed` |
| `GET /v1/feature/lnam/{lnam}` | `handlers/feature.rs`, `Db::find_feature` | Geometry and props of one feature | Signed: the same handler |

**`/v1/geojson` and `/v1/feature` become admin routes.** They take the `Signed` extractor, as
`POST /v1/geojson` and the change reports already do:

- A request without a valid admin signature gets an empty 401, for a public chart as for a
  restricted one. The signature check runs before anything else, so it also comes before the
  routes' existing 404s.
- A signed request gets what the routes return today, restricted charts included. Their queries
  need no restriction filter.
- A token never opens them. Only the tile route reads a token.
- Each route's `#[utoipa::path]` gains `security(("admin_signature" = []))` and the 401 response.
- The web UI's layer browser (`crates/njord-web/src/ui/layer.rs`, through
  `api::features_by_layer`) is the one caller. It must send the stored signature, as
  `api::change_report_page` does, and ask the user to sign in when there is none. The web UI
  does not call `/v1/geojson`.

**Routes that need no change**, and why:

| Route | Why |
| --- | --- |
| `GET /v1/chart?name=` | Chart metadata, treated as public: coverage, scale, dates, `dsid_props`, `chart_txt`, layer names and the feature count. Gains `restricted` |
| `GET /v1/chart_catalog`, `GET /v1/chart_editions` | Chart names and edition keys |
| `GET /v1/regions` | The manifest; no chart data |
| `GET /v1/regions/{archive}` | Signed. A licensed region's archive is encrypted |
| `GET /v1/change_reports`, `GET /v1/change_reports/{date}` | Signed. Chart names and feature counts |
| `POST`/`DELETE /v1/chart`, `POST /v1/geojson`, `/v1/enc_save`, `/v1/enc_url`, `/v1/nuke`, `DELETE /v1/cache`, `/v1/ws/enc_process` | Signed writes and ingest status |
| `/v1/style`, `/v1/tile_json`, `/v1/icon`, `/v1/sector_svg`, `/v1/content`, `/v1/about`, `GET /v1/cache` | No chart data |

A new route that returns feature data is added to the first table, with its restriction check,
in the same change.

### 6. Request access

A new module, `crates/njord-server/src/chart_access.rs`, answers one question for the tile route:
which restricted producers may this request see in this tile?

```rust
/// What a request may see of the restricted charts.
pub enum ChartAccess {
    /// A valid admin signature: everything.
    Admin,
    /// The licensed regions a valid token names whose `exp` has not passed; empty for an
    /// anonymous request.
    Regions(Vec<String>),
}

impl ChartAccess {
    /// The restricted producers whose charts the request may see in tile `t`.
    pub fn producers_in(&self, state: &AppState, t: TileXyz) -> BTreeSet<i32>;
}
```

`ChartAccess` is an extractor like `Signed`, except that it cannot fail: its `FromRequestParts`
has `Rejection = Infallible`. `handlers/tile.rs` takes it and passes `producers_in` for the
requested tile to `tile_encoder::tile` and `tile_encoder::info`. The access comes from the first
of two sources that applies.

**The admin signature (stage 1).**

- The `signature` query parameter is verified exactly as `Signed` verifies it
  (`Verifier::verify_query` against the external base URL).
- A valid signature is `ChartAccess::Admin`, and `producers_in` is every code in
  `restrictedProducers` for every tile.
- A missing, malformed or expired signature is not a 401 here. The tile route is public, so the
  request falls through to the token, and then to no access. A map whose signature expires
  (`adminExpirationSeconds`) goes back to public tiles.

**The token (stage 3).**

- **Verification.** Read `Authorization: Bearer <jwt>`, split the compact form on `.`,
  base64url-decode the three parts, recompute HMAC-SHA256 over `header.payload` with
  `chartAccessJwtSecret`, and compare with `subtle::ConstantTimeEq`. `signature::hmac` is the
  primitive and `signature::Verifier::verify` is the model for the comparison. The header's
  `alg` must be `HS256`. `exp` must be in the future, and `iss` must equal
  `chartAccessJwtIssuer` when that is configured.
- **Claims.** `regions`, an array of `{ "name", "exp" }`. `sub` is informational.
- **Which regions are kept.** An entry is kept when its own `exp` is in the future and its
  `name` is a `regionExports` entry whose `restricted` is true. Every other entry is dropped:
  an unknown name, a public region, an expired entry.
- **From regions to producers.** `producers_in` returns every code in `restrictedProducers` when
  the tile intersects the coverage of a kept region, and the empty set otherwise. The tile is
  its unexpanded envelope (`TILE_SYSTEM.create_tile_clip_polygon(x, y, z, 0)`), the same one
  `chart_inputs` tests chart coverage against. A shared boundary counts as intersecting.
- **Region coverage is parsed once.** The licensed regions' polygons are parsed from their WKT
  at startup and kept in `AppState`, so the test is an in-memory intersection with no query. A
  licensed region whose coverage does not parse fails startup, since its tokens would silently
  grant nothing.
- A missing header, a malformed or expired token, a bad signature and an unconfigured secret all
  yield no regions. Malformed input must never be a 500.
- **CORS.** `ALLOW_HEADERS` in `cors.rs` gains `Authorization`, or a browser on another origin
  cannot send the token. The conformance goldens pin the exact preflight header values, so the
  specs for those cases get a `port` expectation.

**Response headers (stage 1).** A tile response has no `Cache-Control` today. A response made
with a non-empty set carries `Cache-Control: private` and `Vary: Authorization`, so a cache
between njord and the client never serves it to another request.

### 7. The tile cache

`crates/njord-server/src/tile_cache.rs`. Once a tile's content depends on the request's
producers, the cache key must carry them, or one request's tile is served to another. This is a
correctness and security requirement.

- `get` and `put` take the request's `allowed_producers` beside `z`, `x`, `y`.
- With an empty set, the tile is the public one and stays where it is,
  `<chartTempData>/tiles/{z}/{x}/{y}.mvt`. That layout is the one the Debian package and the
  operations documents describe.
- With a non-empty set, the tile is stored at
  `<chartTempData>/tiles/auth/<codes>/{z}/{x}/{y}.mvt`, where `<codes>` is the set's codes in
  ascending order joined by `-` (`tiles/auth/550/…`). Each distinct set is its own variant.
- `clear` removes the whole base directory, so it covers every variant. `counts` skips a
  directory whose name is not an integer, so `GET /v1/cache` keeps reporting the public tiles
  alone.
- The key is the producer set, not the token: what a request may see is resolved to one set per
  requested tile before the cache is read ([Request access](#6-request-access)).
- A licensed region holds every restricted producer, so the set is empty or full, and a tile has
  two variants. The admin and every token holder with a region there share the full one. Keying
  by the set keeps the cache right if a region ever holds only some producers.
- **The region export never reads or writes the tile cache**, and that must stay true. A licensed
  region's render includes restricted charts for every tile, with no request behind it.

This is part of stage 1: the admin's tiles hold restricted charts as soon as one is ingested.

### 8. The coverage outline's color

`crates/njord-symbology/src/layers/ply.rs` draws every chart's outline in `CURSR`. A restricted
chart's outline is drawn in `CHRED` (already in `njord_model::Color`), picked by the new feature
property with a `case` expression, the pattern `text_color` in `layers/soundg.rs` uses:

```rust
line_color: Some(json!([
    "case",
    ["==", ["get", "restricted"], true],
    b.color(Color::CHRED),
    b.color(Color::CURSR),
])),
```

The color follows the chart's flag, never the viewer's access. The style goldens for the `PLY`
layer change with it.

### 9. Clients

- **Njord's web UI** (`crates/njord-web`) has one kind of sign-in, the admin's, and sends no
  token. To an anonymous visitor it shows a restricted chart as an outline in `CHRED`, and its
  chart page can show `restricted` from `GET /v1/chart?name=`.
- **The web UI signed in as admin** holds an admin signature. Its layer browser sends it
  ([Routes that return feature data](#5-routes-that-return-feature-data)). To show the admin
  the restricted charts on the map, it adds the `signature` query parameter to tile requests
  with maplibre-gl-js's `transformRequest` map option; `Controller` in
  `crates/njord-web/src/controller.rs` builds the options object where it goes.
- **A client that holds a token** attaches it to tile requests as an `Authorization` header,
  through the same `transformRequest` option in a browser, and through the HTTP hooks of the
  MapLibre native SDKs.
- **MX Mariner's apps** read licensed charts from encrypted region archives when offline, and
  send the token when they load njord's online style and tiles (`encryption.md` §14).

## Offline region archives

This is njord's part of MX Mariner's `docs/explorations_unimplemented/encryption.md`. The key
endpoint, purchase validation, device key storage and expiry belong to the key issuer and are
described there. The code is `crates/njord-server/src/regions/export.rs` unless another file is
named.

### Which charts and which regions

- **Restricted chart.** A chart whose `charts.restricted` is true
  ([Schema](#2-schema-chartsrestricted)).
- **Licensed region.** A `regionExports` entry whose `restricted` is true. It includes the
  restricted charts of every restricted producer, and every tile blob in its archive is
  encrypted.
- **Public region.** An entry whose `restricted` is false. It is exported exactly as today and
  holds no restricted chart.
- `WORLD` is the base map alone and is always public.

### What a tile and the catalog hold

An archive tile is the live tile: `archive_tile` draws every chart within the tile's bounds, not
only the charts of the region.

| | Licensed region | Public region |
| --- | --- | --- |
| Tiles | Every chart within the tile's bounds, restricted ones included (`allowed_producers` is every restricted producer) | Every public chart within the tile's bounds; restricted charts are left out, and the public charts and base map beneath them show |
| `charts` catalog | Every chart whose coverage intersects the region | The public charts whose coverage intersects the region |
| `chart_tiles`, candidate tiles | Derived from that catalog's charts | Derived from that catalog's charts |

- `Db::find_charts_in_region` (`crates/njord-db/src/regions.rs`) takes the region's flag and adds
  `AND (NOT restricted OR $2)`. The catalog, the candidate tiles and the `chart_tiles` edges all
  follow from its result (`compile_tile_coordinates`), so they need no separate filter.
- `Db::region_needs_rebuild` takes the same flag and the same clause, so ingesting a restricted
  chart does not re-export a public region whose archive it cannot change.
  `Db::regions_containing_chart` likewise, for a deleted chart.
- A region's flag is not recorded with its export. After changing `regionExports[].restricted`,
  re-render the region with `POST /v1/regions?name=`. Forgetting to is safe in both directions:
  the old archive is either still encrypted or holds no restricted chart.

### Keys

- **Master secret.** `regionKeyMaster`, 32 random bytes, shared with the key issuer (MX Mariner's
  document calls it `REGION_KEY_MASTER`). It is not `chartAccessJwtSecret`. Njord stores no
  per-archive key and has no route that returns one.
- **Archive key.** `HKDF-SHA256(ikm = master, salt = none, info = "mxmariner/tile-key/v1/" +
  archive file name)`, 32 bytes. The archive file name is the final name,
  `<region>_<yyyy-MM-ddTHH-mm-ss>.mbtiles`, not the `.tmp` name the export writes to. Names are
  timestamped and never reused, so every export gets a new key and the issuer derives the same
  key independently.
- **Key id.** The first 4 bytes of `SHA-256(archive file name)`. Public; it only selects a key.
- Exporting a licensed region with `regionKeyMaster` absent, or not 32 bytes, fails the export
  and installs no archive. It never falls back to writing plain tiles.

### Blob format

Each tile is gzipped as today (`gzip`), then sealed with XChaCha20-Poly1305 (the RustCrypto
`chacha20poly1305` crate; the key derivation uses `hkdf`):

```
"MXT1" (4) | key id (4) | nonce (24, random) | ciphertext of the gzipped tile | tag (16)
```

- The 8-byte header (magic and key id) is the AEAD's associated data.
- The nonce comes from the operating system's random generator, fresh for every blob.
- The tile coordinate is deliberately not bound.
- The magic cannot be confused with gzip (`1f 8b`), so a reader decides per blob.
- Test vectors (master, archive file name, nonce, plaintext, blob) are checked in under
  `testdata/regions/` and shared with the two decryptors: MX Mariner's MapLibre fork and its
  depth reader.

### Archive metadata

`metadata_rows` writes the MBTiles `metadata` table. For a licensed region:

| Name | Value |
| --- | --- |
| `njord:schema_version` | `2`. A client that knows only version 1 refuses the archive instead of installing tiles it cannot read |
| `njord:encryption` | `MXT1`, the blob format |
| `njord:key_id` | The key id as 8 lowercase hexadecimal digits |

A public region's metadata does not change: `njord:schema_version` stays 1, and the two new rows
are absent. `CATALOG_SCHEMA_VERSION` is one constant today and becomes a value chosen per
archive. `testdata/regions/metadata.json`, `schema.sql` and `archive.json` keep pinning the public
layout.

### Producer codes in the manifest

`GET /v1/regions` reports, for each region's newest archive, the IHO producer codes within it:

```json
{ "name": "…", "archive": "…", "archiveSha256": "…", "producerCodes": [550] }
```

- **What it holds.** The `DSID_AGEN` of every chart that contributed a feature to a tile of the
  archive, public producers included, sorted ascending, without duplicates.
- **Why from the tiles.** A tile draws charts from beyond the region, so the region's catalog
  undercounts. `archive_tile` reports which charts contributed a feature to each tile it
  renders, and the export collects their producer codes.
- **Where it is kept.** The manifest is rebuilt from the archive directory on every call, so the
  set is written beside the archive as `<archive>.producers` (a JSON array), as the checksum is
  written to `<archive>.sha256`. `Regions::prune_old_archives` deletes it with the archive.
- **Before the first export**, and for an archive with no sidecar, `producerCodes` is `null`, like
  the other archive fields.
- The seller calculates licensing usage from it.

### What stays plain

- `charts`, `chart_tiles` and `metadata`: outlines, names, scales and editions, the same
  metadata the online routes treat as public.
- `chart_txt` and `dsid_props` come from the licensed cells and stay plain too. The license terms
  do not name chart metadata as protected.

### Unchanged

- The archive is still a valid MBTiles file, served by the signed `GET /v1/regions/{archive}`
  with the same `Range` behavior and one SHA-256 in the manifest.
- A leaked archive is inert without its key.
- Bulk encryption happens once per export, never per download.

### Consequences

- A new key and fresh nonces per export mean an unchanged tile no longer has the same bytes in
  two exports or in two regions. That rules out any delta update or boundary-tile shortcut that
  compares blobs.
- Njord keeps a region's two newest archives and lists only the newest. The key issuer can derive
  the key of any archive from its name, including one njord no longer lists.

### Njord's own storage

Njord's storage is not encrypted at rest. PostGIS holds the decoded S-57, the tile cache holds
plain tiles (the variants made for a token among them), and uploaded cells sit in
`<chartTempData>/save` and `<chartTempData>/ingest` until they are ingested. Licensed data there
is protected by access control alone: who can reach the database and the server's disk, and the
routes above that refuse restricted charts.

## Verification

Tests run with `cargo test --workspace`; the `njord-db` and `njord-server` suites need the dev
PostGIS (`chart_server_db/`). A restricted chart in a test is a fixture chart whose `DSID_AGEN`
is put in `restrictedProducers`: the fixtures are NOAA cells with `DSID_AGEN` 550.

**Stage 1**

- `crates/njord-db/tests/migrations.rs`: a fresh database and a migrated version 4 database both
  end at version 5 with `charts.restricted`.
- `crates/njord-db/tests/charts.rs`: a chart is written `restricted` when its `DSID_AGEN` is in
  the list and public otherwise, through ingest and through `POST /v1/chart`; a chart with no
  `DSID_AGEN` is public; the startup reconcile flips rows when the list changes and reports that
  it did.
- `crates/njord-server/tests/tiles.rs`: with a restricted chart in the tile, an anonymous tile
  holds its `PLY` outline with `restricted = true`, none of its features, and the smaller-scale
  public chart or base map where it would have been drawn. `info=true` lists none of its
  features.
- `GET /v1/geojson`, `GET /v1/feature/layer/{layer}` and `GET /v1/feature/lnam/{lnam}` answer an
  empty 401 without a valid admin signature, for a public chart and for a restricted one. With
  one they answer as they do today, restricted charts included.
- A tile requested with a valid admin signature holds the restricted chart's features and
  carries `Cache-Control: private` and `Vary: Authorization`; `info=true` lists them. The same
  tile with an expired or malformed signature is the public tile, not a 401.
- The same `z/x/y` requested with an admin signature and anonymously produces two cache files,
  `tiles/auth/<codes>/{z}/{x}/{y}.mvt` and `tiles/{z}/{x}/{y}.mvt`, and neither is served for the
  other. `GET /v1/cache` counts the public one only, and `DELETE /v1/cache` removes both.
- The web UI's layer browser loads when signed in and asks for a sign-in otherwise.
- `GET /v1/chart?name=` reports `restricted` for both kinds of chart.
- The `PLY` style layer draws `CHRED` for `restricted = true` and `CURSR` otherwise, in every
  theme.
- `crates/njord-server/tests/openapi.rs`: the path list is unchanged, and the new fields appear
  in the schemas.
- The conformance goldens (`conformance/`) were recorded from the retired Kotlin server and are
  frozen. `Chart` gaining `restricted`, the `PLY` style, the `PLY` tile property and the 401 from
  the unsigned `/v1/geojson` and `/v1/feature` specs (`test_api_04_charts.py`,
  `test_api_07_auth.py`) are decided divergences, each recorded as a spec's `port` expectation.

**Stage 2**

- `crates/njord-server/tests/export.rs`: a region with `restricted = false` exports
  byte-compatibly with today's layout (`njord:schema_version` 1, gzip blobs), and none of its
  tiles, `charts` rows or `chart_tiles` rows holds a restricted chart, including one that lies
  within a tile's bounds.
- A region with `restricted = true` holds the restricted chart in its catalog and tiles, declares
  `njord:schema_version` 2 with `njord:encryption` and `njord:key_id`, and holds only `MXT1`
  blobs in `tiles`. Each opens with the key derived from `regionKeyMaster` and the archive file
  name, gunzips to the tile `archive_tile` renders, and fails with another archive's key.
- The shared test vectors seal and open.
- Exporting a licensed region without `regionKeyMaster` fails and leaves no archive and no
  `.tmp` file.
- `crates/njord-server/tests/regions.rs`: the manifest reports `producerCodes` for an exported
  region, including the producer of a chart that lies within a tile's bounds but outside the
  region's coverage; it is `null` before the first export; pruning an archive removes its
  `.producers` sidecar.
- Ingesting a restricted chart does not mark a public region stale.
- The export leaves the tile cache untouched.

**Stage 3**

- Unit tests for the token: valid, expired, tampered, signed with another secret, `alg` other
  than `HS256`, wrong `iss`, malformed in every part, and no secret configured. Each failure
  yields no regions and never an error response.
- A token entry is dropped when its region is unknown, is not licensed, or has an `exp` in the
  past, and the rest of the token still counts.
- With a token for one licensed region: a tile inside its coverage holds the restricted charts'
  features, a tile outside it is the public tile, and a tile sharing only its boundary counts as
  inside.
- With a token for two regions whose `exp` differ, after the earlier one passes: tiles in that
  region are public and tiles in the other still hold restricted charts.
- A low-zoom tile that intersects the region draws restricted charts beyond the region's
  polygon, as the region's archive tile at that coordinate does.
- A licensed region whose coverage does not parse fails startup.
- A request with a token gets `Cache-Control: private` and `Vary: Authorization` on a tile in
  its region; the same tile requested anonymously gets the public tile.
- A tile in a token's region is served from the cache file the admin's request wrote.
- `info=true` lists the features of exactly the charts the tile draws for the same request.
- `GET /v1/geojson` and `GET /v1/feature` answer 401 to a request with a valid token and no
  admin signature.
- A cross-origin preflight asking for `Authorization` is allowed.

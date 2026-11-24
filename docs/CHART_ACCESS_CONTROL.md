# Support for license-encumbered cartography

Status: a plan. Nothing here is built. The code references were audited against the Rust
workspace (`crates/`) on 2026-11-24. Every chart in staging and in production is a NOAA chart,
which is unrestricted, and no restricted chart is ingested in either until all three stages
below are deployed.

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

The three stages are separate releases. Staging and production hold only NOAA charts today, all
unrestricted, and no restricted cell is ingested in either until all three stages are deployed
there. So no release has to protect data that is already present, and between releases every
chart is still public. Until the first restricted cell, the restricted paths run only in the
tests ([Verification](#verification)).

1. **Restriction, refusal and admin access.** The `restrictedProducers` configuration, the
   `charts.restricted` column, every route that returns feature data refusing restricted charts
   to a request without an admin signature, and the tile cache keyed by what a request may see
   ([Configuration](#1-configuration) to
   [The coverage outline's color](#8-the-coverage-outlines-color), without the token half of
   [Request access](#6-request-access)). No token exists yet, so the admin is the only one who
   sees a restricted chart online. Every region archive is public: the export leaves restricted
   charts out of each archive's tiles and catalog, and a region flagged `restricted` fails
   startup ([Stage 1: every archive is public](#stage-1-every-archive-is-public)).
2. **Encrypted archives.** A region's `restricted` flag taking effect, the licensed region's
   chart set, the sealed blobs and `producerCodes`
   ([Offline region archives](#offline-region-archives)). Requires stage 1.
3. **Region tokens.** JWT verification as a second source of what a request may see
   ([Request access](#6-request-access), [Clients](#9-clients)). Njord's CORS rules do not
   change: a browser client on another origin sends its token through a proxy on its own origin.
   Requires stage 1. Offline archives do not need it.

### What each release changes for the charts there today

Every chart is unrestricted and `restrictedProducers` is empty, so each release is a no-op for
the data, apart from the API changes listed here.

| Release | What changes in staging and production |
| --- | --- |
| Stage 1 | Migration 5 adds `charts.restricted`, false for every chart. The startup reconcile changes no row, so no tile cache is cleared and no region is re-exported. `GET /v1/chart?name=` reports `restricted: false`. A page of `GET /v1/feature/layer/{layer}` gains its totals, with `totalRestricted` 0. A `PLY` outline gains `restricted: false` and keeps its color. An unsigned `GET /v1/geojson` is now a 401: the one change that is not an added field |
| Stage 2 | Every region stays public and its archive keeps today's layout. `GET /v1/regions` gains `restricted: false` on every region, and `producerCodes`: `null` until a region is next exported, then `[550]` for a chart region and `[]` for `WORLD`, which draws no chart. A file in the region directory whose name is not `<region>_<timestamp>.mbtiles` is no longer listed or pruned as an archive |
| Stage 3 | Every tile response gains `Vary: Authorization`. Nothing else changes: no token exists until the issuer holds one of `chartAccessJwtSecrets` |

A tile cached before stage 1 has no `restricted` property on its `PLY` outlines. The style draws
a missing value as it draws `false`, so the cache need not be cleared for the release.

MX Mariner's apps, backend and web are unaffected by all three. They call neither `/v1/geojson`
nor `/v1/feature`, and their parsers of `/v1/regions` and `/v1/chart` ignore unknown keys.

### The first restricted cell

A restricted cell is ingested only when all of this is true in the environment it goes to,
staging first:

1. All three stages are deployed.
2. `restrictedProducers` holds the cell's producer code, and every `njord-server` and
   `njord-ingest` process was started with it.
3. A new region is defined to hold the producer's charts offline, flagged `restricted`
   ([Which charts and which regions](#which-charts-and-which-regions)), and `regionKeyMaster` is
   set for the process that exports and held by the key issuer. No region defined today is
   flagged, and none is changed.
4. `chartAccessJwtSecrets` holds a secret the key issuer mints with.

The first two protect the data. Without the third the chart is in no archive, and without the
fourth only the admin sees it online; neither exposes it.

### Rollback

`Db::migrate` accepts a schema newer than the one a binary knows, so an image from before stage 1
starts on a version 5 database, ignores `charts.restricted` and serves every chart's features to
anyone.

- **Before the first restricted cell is ingested**, that is harmless: every chart is public, and
  the older image serves what it always served.
- **After it, rolling back below stage 1 is not supported.** A defect is fixed by a new release,
  never by deploying an older image.

### Open before stage 1 ships

One decision waits on a measurement: whether every page of `GET /v1/feature/layer/{layer}`
carries the layer's two totals, or the first page alone. Stage 1 starts by timing the totals
query on the largest layer in production, because the second outcome makes two fields of
`LayerQueryResultPage` optional on the wire
([Routes that return feature data](#5-routes-that-return-feature-data)).

### Work outside njord

Njord's three stages need this from MX Mariner. Its offline half (the decryptor, the key
endpoint, the apps' key storage) is in `encryption.md` §12.

- **The key issuer checks its product-to-region mapping against the manifest's `restricted`**
  ([Interface with the key issuer](#interface-with-the-key-issuer)).
- **The key issuer mints tokens with one of `chartAccessJwtSecrets`**, and replaces a secret in
  the order given there.
- **MX Mariner's web app needs a proxy on its own origin** before it can show a licensed chart
  online. It reads `njord.mxmariner.com` directly today, and njord allows no cross-origin
  `Authorization` ([Clients](#9-clients)). The native apps need none.

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
- **`GET /v1/geojson` is admin only.** It requires an admin signature for every chart, public or
  restricted. It returns a chart's source features unclipped and unstyled, and no viewing path
  needs it.
- **`GET /v1/feature` is public, without the restricted charts.** It requires no signature. The
  features of restricted charts are left out of its responses unless the request carries a valid
  admin signature. A page of a layer also reports, to every request, how many of the layer's
  features are in unrestricted charts and how many are in restricted ones. A feature count is
  chart metadata, which is public: `GET /v1/chart?name=` already reports each chart's.
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
| The region manifest | `GET /v1/regions`, public | Each region, whether it is licensed (`restricted`), and its newest archive: the file name, size, SHA-256, creation time and `producerCodes` |
| A region archive | `GET /v1/regions/{archive}`, with an admin signature from `GET /v1/admin` | The issuer relays the archive to purchasers byte for byte |
| `regionKeyMaster` | A secret both hold | Each derives an archive's key from its file name, so no key ever crosses the boundary ([Keys](#keys)) |
| `chartAccessJwtSecrets` | Secrets both hold | The issuer mints online tokens with one, and njord verifies a token against each |

Njord has no route that returns a key, mints, refreshes or inspects a token, or records a sale.

**Region names are the common unit.** `regionExports[].name` is the name in the manifest, in
archive file names and in a token. The issuer maps what it sells to region names; njord maps
region names to coverage and producers. Producer codes cross the boundary only in the manifest's
`producerCodes`, which the issuer uses to calculate licensing usage.

**The manifest says which regions are licensed.** Each entry carries `restricted`, the region's
`regionExports[].restricted`. The issuer keeps its own mapping from products to regions, and
checks it against this field: a region it sells that njord exports as public has a plain archive
and no key to give, and a region njord encrypts that the issuer treats as public has an archive
nobody can open. Neither can be seen from the archives' names.

**Replacing a secret.**

- **`chartAccessJwtSecrets` is a list**, and a token is valid when it verifies against any
  entry. To replace a secret: add the new one to njord's list, have the issuer mint with it, and
  remove the old one from njord's list. A token minted with the old secret stops working when
  that secret is removed, and its holder sees public tiles until the client next asks the issuer
  for a token (at its next start or sign-in). Removing the old secret after the latest `exp` the
  issuer minted with it disturbs nobody. After a leak it is removed at once.
- **`regionKeyMaster` is not rotated.** Every archive key is derived from it, by njord and by the
  issuer, so there is no moment at which one side can change it alone. After a leak, njord and
  the issuer are given a new master together, and every licensed region is re-exported
  (`POST /v1/regions?name=`), which seals its new archive under the new master. The issuer keeps
  the old master only until each licensed region's listed archive is one exported after the
  change. The archives already downloaded stay readable by anyone who holds the old master;
  nothing njord does can change that. The `v1` in the key derivation's `info` string
  ([Keys](#keys)) is where a master generation would go if rotation is ever built.

**One purchase, two deliveries.** MX Mariner sells one subscription per licensed region. The
purchase earns the region's archive key, for offline use, and a place for the region in the
user's online token. The two end at different times. The token's entry ends when the
subscription expires. The installed archive stays usable for a grace period after that, until
the app uninstalls it (`encryption.md` §10 and §14). Nothing in the offline design depends on
the token.

### The online token

The issuer mints HS256 JWTs with one of `chartAccessJwtSecrets`, and njord verifies them
locally. It mints one when it has verified a user's subscriptions: after a purchase, and when
the user's client starts or signs in.

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
| `chartAccessJwtSecrets` | array of strings | `[]` | The HS256 secrets shared with the key issuer, for online tokens. A token is valid when it verifies against any of them; more than one only while a secret is being replaced |
| `chartAccessJwtIssuer` | string | absent | The expected `iss` claim; not checked when absent |

- `regionKeyMaster` and `chartAccessJwtSecrets` are delivered like `adminKey`, through the secret
  `cargo xtask secrets` writes. Neither may appear in the `application.json` that is checked in.
  `cargo xtask secrets` writes a fixed set of keys today (`crates/xtask/src/main.rs` reads
  `ADMIN_KEY` and its siblings and masks them in a dry run), so each of the two is added there,
  optional and masked the same way, in the stage that introduces it.
- `ChartsConfig` derives `Debug`. Both need a field type whose `Debug` does not print the value.
- `njord-ingest` runs the export worker with the same configuration, so `regionKeyMaster` must
  reach whichever process exports. From stage 2, a process that runs the export worker fails to
  start when a region is flagged `restricted` and `regionKeyMaster` is absent or not 32 bytes
  ([Keys](#keys)).
- With `chartAccessJwtSecrets` empty, no token is ever valid, and only a request with an admin
  signature may see a restricted chart. That is stage 1.
- `regionExports[].restricted` is read from stage 1 on, but a stage 1 build cannot encrypt. With
  the flag true on any region, `njord-server` and `njord-ingest` fail to start
  ([Stage 1: every archive is public](#stage-1-every-archive-is-public)).
- `restrictedProducers` must be set **before** a cell from one of those producers is ingested.
  Archives and cached tiles made earlier hold the cell's data in the clear until they are replaced
  (see [Reconciling at startup](#reconciling-at-startup)). It is empty in staging and production
  today and stays empty until all three stages are deployed
  ([The first restricted cell](#the-first-restricted-cell)).

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

**A `DSID_AGEN` that is not an integer is an error, never a public chart.** The cast to `int`
fails for a string such as `"NOAA"` and for a number such as `550.5`, and the statement fails
with it:

- The insert fails and the chart is not written, as for any failed insert. Ingest writes
  `DSID_AGEN` as a JSON integer, so only a hand-made `POST /v1/chart` body can do this.
- A row like that can then exist only if it was written before stage 1. None is expected: every
  chart in staging and production was ingested from a NOAA cell. The startup reconcile reads
  every row, so it would find one, and the process would not start
  ([Reconciling at startup](#reconciling-at-startup)).
- So `Db::find_chart_info`, which makes the same cast for every tile, never meets one.

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

**In the planned rollout it never changes a row.** The stage 1 deploy runs it with an empty list
against rows that migration 5 just set to false. Adding a producer's code later, before any of
its cells is ingested, matches no chart either. It changes a row only when the list is changed
after cells from the producer are already there, which
[The first restricted cell](#the-first-restricted-cell) rules out.

**A failed reconcile is fatal.** `njord-server` and `njord-ingest` run it right after `migrate`
and treat its error as they treat a failed migration: the process logs it and exits before it
serves a request or starts a worker. A process that cannot establish which charts are restricted
must not serve any. The causes are a database failure and a chart whose `DSID_AGEN` is not an
integer ([Schema](#2-schema-chartsrestricted)); the fix for the second is to correct or delete
that chart's row.

### 3. Models

`crates/njord-model/src/chart.rs` and `region.rs` (the contract with the web UI and other
clients), and `crates/njord-db/src/charts.rs`:

- `njord_db::ChartInfo` gains `restricted: bool` and `producer: Option<i32>` (the chart's
  `DSID_AGEN`), both selected by `Db::find_chart_info`, so the tile encoder filters without a
  second query.
- `njord_model::Chart` gains `restricted: bool` (`CHART_COLUMNS` and `chart_from_row`), so
  `GET /v1/chart?name=` tells a client the chart needs a license. Its producer is already there,
  in `dsid_props`.
- `njord_model::LayerQueryResultPage` gains `total_unrestricted: i64` and `total_restricted: i64`
  (`totalUnrestricted` and `totalRestricted` on the wire): the number of the layer's features in
  unrestricted charts and in restricted charts
  ([Routes that return feature data](#5-routes-that-return-feature-data)).
- `njord_model::RegionManifestEntry` gains `restricted: bool`, the region's configured flag, and
  `producer_codes` ([Offline region archives](#offline-region-archives)). Both arrive in stage 2.

`njord-model` must still build for `wasm32-unknown-unknown`.

### 4. The tile encoder

`crates/njord-server/src/tile_encoder.rs`. One value, `allowed_producers`, is threaded from the
three entry points into `chart_inputs` and `add_charts`. It is the set of restricted producer
codes whose charts may be drawn:

| Entry point | `allowed_producers` |
| --- | --- |
| `tile` | What the request may see in this tile ([Request access](#6-request-access)): every restricted producer for the admin and, in stage 3, for a token with a region the tile lies in; otherwise empty |
| `info` | The same set as `tile` for that request |
| `archive_tile` | Every code in `restrictedProducers` for a licensed region; empty for a public one. In stage 1 every region is public, so it is always empty |

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
| `GET /v1/feature/layer/{layer}` | `handlers/feature.rs`, `Db::find_layer_positions_page` | Position and full props of a layer's features, across all charts | Restricted charts' features are left out unless the request has an admin signature; the page gains the layer's two totals |
| `GET /v1/feature/lnam/{lnam}` | `handlers/feature.rs`, `Db::find_feature` | Geometry and props of one feature | A restricted chart's feature is not found unless the request has an admin signature |

**`/v1/geojson` becomes an admin route.** It takes the `Signed` extractor, as `POST /v1/geojson`
and the change reports already do:

- A request without a valid admin signature gets an empty 401, for a public chart as for a
  restricted one. The signature check runs before anything else, so it also comes before the
  route's existing 404.
- A signed request gets what the route returns today, restricted charts included.
  `Db::geojson_layer` needs no restriction filter.
- A token never opens it.
- Its `#[utoipa::path]` gains `security(("admin_signature" = []))` and the 401 response.
- The web UI does not call it.

**`/v1/feature` stays public and leaves the restricted charts out.** The handler takes
`ChartAccess` ([Request access](#6-request-access)) and passes `is_admin()` to both queries as
`include_restricted`:

- Without a valid admin signature, both queries add `AND NOT charts.restricted`. With one, they
  run as they do today.
- A missing, malformed or expired signature is not a 401. The route is public, so the request is
  answered as an anonymous one, as on the tile route.
- A token never opens the restricted charts here: `is_admin()` is false for
  `ChartAccess::Regions`. Only the tile route reads a token's regions.
- A response made for the admin carries `Cache-Control: private`, as a tile made for the admin
  does.
- The `#[utoipa::path]` gains the optional `signature` parameter
  (`security((), ("admin_signature" = []))`) and the two new fields. It gains no 401.

`layer/{layer}` (`Db::find_layer_positions_page(layer, start_id, include_restricted)`):

- The page is the next 5 features after `start_id` that the request may see. `last_id` is still
  the largest id served, so paging passes over the restricted charts' rows with no change to the
  client.
- The page gains the layer's two totals:

  ```json
  { "lastId": 48213, "items": [ … ], "totalUnrestricted": 812, "totalRestricted": 96 }
  ```

- `totalUnrestricted` is the number of the layer's features in unrestricted charts, and
  `totalRestricted` the number in restricted charts. Both count the whole layer, not what is left
  after `start_id`, so they are the same on every page.
- Both are the same for every request, signed or not. `totalRestricted` is how an anonymous
  client learns that features were left out. A signed request pages through
  `totalUnrestricted + totalRestricted` features, an anonymous one through `totalUnrestricted`.
- They come from one aggregate beside the page query, over the rows `features_layer_id_idx`
  finds for the layer:

  ```sql
  SELECT count(*) FILTER (WHERE NOT charts.restricted),
         count(*) FILTER (WHERE charts.restricted)
  FROM features JOIN charts ON features.chart_name = charts.name
  WHERE features.layer = $1;
  ```

- An unknown layer is still an empty page, with both totals 0.
- **Whether every page carries the totals is decided by a measurement, before the wire type
  ships.** The aggregate reads every row of the layer for each 5 features served: the index finds
  the rows, but the join needs `chart_name`, a heap fetch per row. Stage 1 starts by timing it
  on the largest layer in production, which holds every NOAA chart. If the time is acceptable,
  the totals are on every page, as written here. If not, they are on the first page only
  (`start_id` 0) and the two fields are optional on the wire, absent from later pages. The
  layer browser reads them from the first page either way.
- A TOPMAR's `_PTFM` and `_ASOC` are still computed from every chart's features
  (`associated_layer_names`). They name layers, never a feature.

`lnam/{lnam}` (`Db::find_feature(lnam, include_restricted)`):

- The query joins `charts`. When several charts carry the `LNAM`, the lowest id among the charts
  the request may see wins.
- An `LNAM` that only restricted charts carry is, to an unsigned request, the same empty 404 as
  an unknown one.
- `FeatureRecord` does not change. The totals are the layer page's alone.

The web UI's layer browser (`crates/njord-web/src/ui/layer.rs`, through
`api::features_by_layer`) is the web UI's one caller, and it keeps working without a sign-in.
When a signature is stored it sends it, as `api::change_report_page` does, so the admin's list
includes the restricted charts. It shows both totals, and when `totalRestricted` is not 0 and
there is no signature, it says that many features are in restricted charts and need a sign-in.

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
which restricted producers may this request see in this tile? For `/v1/feature` it answers a
narrower one: is this request the admin's?

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

    /// Whether the request carries a valid admin signature.
    pub fn is_admin(&self) -> bool;
}
```

The tile route's `#[utoipa::path]` documents both: the optional `signature` parameter in stage 1
(`security((), ("admin_signature" = []))`), and in stage 3 an optional bearer token, as a second
security scheme in `crates/njord-server/src/openapi.rs`. It gains no 401.

`ChartAccess` is an extractor like `Signed`, except that it cannot fail: its `FromRequestParts`
has `Rejection = Infallible`. `handlers/tile.rs` takes it and passes `producers_in` for the
requested tile to `tile_encoder::tile` and `tile_encoder::info`. `handlers/feature.rs` takes it
and reads `is_admin()` alone
([Routes that return feature data](#5-routes-that-return-feature-data)). The access comes from
the first of two sources that applies.

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
  base64url-decode the three parts, recompute HMAC-SHA256 over `header.payload` with each entry
  of `chartAccessJwtSecrets` in turn, and compare with `subtle::ConstantTimeEq`. The token is
  valid when one matches. `signature::hmac` is the primitive and `signature::Verifier::verify`
  is the model for the comparison. The header's `alg` must be `HS256`. `exp` must be in the
  future, and `iss` must equal `chartAccessJwtIssuer` when that is configured.
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
- **CORS does not change.** `cors.rs` keeps refusing a preflight that asks for `Authorization`,
  so a browser on another origin cannot send a token to njord directly. That is deliberate. A
  request with `Authorization` is never a simple request, and a browser caches a preflight per
  URL, so allowing it would cost one `OPTIONS` round trip for every distinct tile. A browser
  client on another origin instead reaches njord through a reverse proxy on its own origin
  ([Clients](#9-clients)), where no preflight is sent.

**Response headers.** A tile response has no `Cache-Control` and no `Vary` today. Both rules
cover every response of the tile route, the `info=true` JSON as well as the tile.

- **Stage 1.** A response made with a non-empty set carries `Cache-Control: private`, so a cache
  between njord and the client never stores it. The admin's signature is part of the URL, so no
  cache can confuse the admin's tile with the public one.
- **Stage 3.** Every tile response carries `Vary: Authorization`, whatever the request may see.
  A token is a header, so the public tile and a token holder's tile have the same URL. Without
  `Vary` on the public response, a cache that holds it may serve it to a request that carries a
  token, and a purchaser sees public tiles. A browser client's proxy is such a cache if it
  caches at all.

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

The layer keeps its zoom range, 0 to 9. A chart's outline is an overview aid, for a restricted
chart as for any other. At zoom 10 and above no outline is drawn, so nothing on the map marks a
restricted chart there: an unlicensed viewer sees the smaller-scale public chart or the base
map. `GET /v1/chart?name=` and the manifest are how a client learns a chart or a region needs a
license.

### 9. Clients

- **Njord's web UI** (`crates/njord-web`) has one kind of sign-in, the admin's, and sends no
  token. To an anonymous visitor it shows a restricted chart as an outline in `CHRED` below
  zoom 10, where every chart's outline is drawn, and its chart page can show `restricted` from
  `GET /v1/chart?name=`. Its layer browser lists the features of the unrestricted charts and
  says how many more are in restricted charts.
- **The web UI signed in as admin** holds an admin signature. Its layer browser sends it, and
  then lists the restricted charts' features too
  ([Routes that return feature data](#5-routes-that-return-feature-data)). To show the admin
  the restricted charts on the map, it adds the `signature` query parameter to tile requests
  with maplibre-gl-js's `transformRequest` map option; `Controller` in
  `crates/njord-web/src/controller.rs` builds the options object where it goes.
- **A client that holds a token** attaches it to tile requests as an `Authorization` header:
  through the HTTP hooks of the MapLibre native SDKs, and in a browser through the same
  `transformRequest` option.
- **A browser client on another origin goes through a proxy on its own origin.** Njord allows no
  cross-origin `Authorization` ([Request access](#6-request-access)), so the client's own site
  forwards its style, TileJSON and tile requests to njord. The proxy is part of that client's
  deployment, not of njord. It must:
  - forward the `Authorization` header;
  - pass the forwarded host and scheme, so the style and the TileJSON njord builds name the
    proxy's origin (`Origin::of` in `crates/njord-server/src/forwarded.rs` reads them);
  - not cache a tile across users: honor `Cache-Control: private` and `Vary: Authorization`.

  MX Mariner's web app (`mxmariner.com/app`) reads `njord.mxmariner.com` directly today and
  needs such a proxy before it can show a licensed chart online. Its native apps do not.
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

**Today's regions stay public, and a licensed region is a new one.** The deployed regions
(`WORLD` and `REGION_01` to `REGION_09`, in `deploy/k8s/chart_server.yaml`) are all public and
are not changed. A licensed region is a new `regionExports` entry with its own name and
coverage.

- **It may overlap a public region.** Each archive is exported on its own. Where the two
  overlap, the licensed region's tiles hold the public charts too, and the public region's hold
  no restricted chart. Which tile a client shows when it has both installed is the client's rule
  (`encryption.md` §8).
- **It is listed as soon as it is configured**, with `null` archive fields until its first
  export.
- **Its first export does not wait for a restricted cell.** A region is exported when any chart
  intersects it, so a licensed region drawn over waters the NOAA charts cover is exported, and
  encrypted, with public charts alone. Ingesting the first restricted cell in it re-exports it.
  The issuer should not sell a region before that export.

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

### Stage 1: every archive is public

Stage 1 ships the public column of that table, so that the release is correct on its own: a
build that knows restricted charts never writes one into an archive. No environment holds a
restricted chart at that point, so only the tests exercise it.

- `archive_tile` always gets the empty set, so no archive tile draws a restricted chart.
- `Db::find_charts_in_region`, `Db::region_needs_rebuild` and `Db::regions_containing_chart` get
  their clause in stage 1, with the flag false for every region. No archive's `charts` catalog,
  `chart_tiles` edges or candidate tiles then come from a restricted chart, and ingesting or
  deleting one re-exports nothing.
- A region whose `restricted` is true fails startup, in `njord-server` and in `njord-ingest`,
  with an error that names the region. A stage 1 build cannot encrypt, and the two alternatives
  are both wrong: exporting the region in the clear would leak any restricted chart in it, and
  exporting it as public silently ignores the configuration.
- The refusal is at startup and not in the export, because the export worker retries a stale
  region at once: `export_next` takes the first stale region, and after a failed render
  `RegionWorker::run` loops with no wait. A region whose export always fails would spin the
  worker and keep every region configured after it from exporting.
- This plan turns its own two always-failing exports into startup checks (this one, and stage
  2's missing `regionKeyMaster`). It does not fix the worker: any other export that fails every
  time, such as a region whose coverage PostGIS cannot parse, still spins it. A wait after a
  failed render, or moving on to the next stale region, would fix the general case, and is
  separate work.
- Stage 2 passes each region's own flag, and replaces this refusal with one for a flagged region
  that has no usable `regionKeyMaster` ([Keys](#keys)).

### Region names and archive files

`Regions::archives_for_region` (`crates/njord-server/src/regions/mod.rs`) decides which files are
a region's archives, for the manifest and for pruning. Today it takes every file whose name
starts with the region's name and ends in `.mbtiles`, so a region `REGION_04` also claims the
archives of a region `REGION_04_X`: its manifest entry can name the other region's archive, and
its prune deletes them. The deployed names do not collide, but new regions are about to be
named, and a public region claiming a licensed region's archive is the worst case.

- Stage 2 changes the match to the exact form the export writes: `<region>_<stamp>.mbtiles`,
  where `<stamp>` parses as `yyyy-MM-ddTHH-mm-ss`. `Regions::archive_timestamp` returns `None`
  for a name that lacks the `<region>_` prefix, instead of parsing the whole name.
- Any region name is then safe beside any other. The plan sets no naming rule.
- A file with an unparsable stamp is no longer a region's archive: it is not listed and not
  pruned. Today it sorts last and is pruned as the oldest.
- `testdata/regions/timestamps.json` pins the old behavior on purpose ("the WA/WA_SOUND prefix
  overlap is pinned as is", "unparsable stems last"), as recorded from the Kotlin server. Those
  cases change with the code, as a decided divergence.

### Keys

- **Master secret.** `regionKeyMaster`, 32 random bytes, shared with the key issuer (MX Mariner's
  document calls it `REGION_KEY_MASTER`). It is not one of `chartAccessJwtSecrets`. Njord stores
  no per-archive key and has no route that returns one.
- **Archive key.** `HKDF-SHA256(ikm = master, salt = none, info = "mxmariner/tile-key/v1/" +
  archive file name)`, 32 bytes. The archive file name is the final name,
  `<region>_<yyyy-MM-ddTHH-mm-ss>.mbtiles`, not the `.tmp` name the export writes to. Names are
  timestamped and never reused, so every export gets a new key and the issuer derives the same
  key independently.
- **Key id.** The first 4 bytes of `SHA-256(archive file name)`. Public; it only selects a key.
- **A licensed region without a usable master fails startup.** With any region flagged
  `restricted` and `regionKeyMaster` absent or not 32 bytes, a process that runs the export
  worker does not start, and the error names the region. That is `njord-ingest`, and
  `njord-server` with `enableIngestion`, in both cases unless `enableRegionExport` is off. A
  process that runs no export worker needs no master and starts without one.
- The check is at startup for the reason stage 1's is
  ([Stage 1: every archive is public](#stage-1-every-archive-is-public)): an export that fails
  every time spins the export worker and keeps the regions after it from exporting.
- The export checks again and fails rather than write a plain tile. It never falls back to
  writing plain tiles.

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

### The manifest: `restricted` and `producerCodes`

`GET /v1/regions` reports whether each region is licensed and, for its newest archive, the IHO
producer codes within it:

```json
{ "name": "…", "restricted": false, "archive": "…", "archiveSha256": "…", "producerCodes": [550] }
```

- **`restricted`** is the region's `regionExports[].restricted`, read from the configuration on
  every call. It is present before the region's first export. It describes the configuration,
  not the listed archive: after the flag is changed and until the region is re-exported, the
  listed archive was made under the old value
  ([What a tile and the catalog hold](#what-a-tile-and-the-catalog-hold)). The archive's own
  `njord:encryption` metadata row is what says a given file is encrypted.

`producerCodes`:

- **What it holds.** The `DSID_AGEN` of every chart that contributed a feature to a tile of the
  archive, public producers included, sorted ascending, without duplicates.
- **Why from the tiles.** A tile draws charts from beyond the region, so the region's catalog
  undercounts. `add_charts` knows which charts it drew a feature for, and `archive_tile` returns
  their producer codes beside the tile's bytes (today it returns the bytes alone). The export
  collects them over every tile it renders.
- **Where it is kept.** The manifest is rebuilt from the archive directory on every call, so the
  set is written beside the archive as `<archive>.producers` (a JSON array), as the checksum is
  written to `<archive>.sha256`. `Regions::prune_old_archives` deletes it with the archive.
- **When it is written.** Before the archive's `.tmp` file is renamed into place, under the final
  archive name. `Regions::manifest` lists an archive as soon as the renamed file exists, so the
  archive must never exist without its sidecar. A failed export removes the sidecar with the
  `.tmp` file.
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
PostGIS (`chart_server_db/`).

Staging and production hold no restricted chart while the stages are built, so nothing deployed
exercises a restricted path. The suites below are the only check of those paths until the first
restricted cell is ingested in staging. What a deploy can confirm is the opposite: that the
unrestricted charts are served as before
([What each release changes](#what-each-release-changes-for-the-charts-there-today)).

**The restricted chart in a test is synthetic.** Every fixture is a NOAA cell with `DSID_AGEN`
550, so restricting 550 would leave no public chart to compare with.

- The public chart is a fixture cell, with `DSID_AGEN` 550.
- The restricted chart is written by the test through `Db`, with a `DSID_AGEN` no fixture
  carries (9999 here) and a handful of features in layers the fixture cell also has. Its coverage
  lies inside the fixture cell's and its scale is larger, so in a tile they share it is drawn
  over the fixture cell and claims its area.
- `restrictedProducers` is `[9999]`: the `Db` under test is built with that list.
- **These suites own their database.** `charts.restricted` is a column, so a restricted chart in
  the shared `njord_rust_server_test` database would change the tiles, catalogs and counts every
  other `njord-server` test pins. The access suites seed a database of their own once per test
  binary and build their state on it with `state_with`. The `njord-db` tests already create one
  database per test (`migrated_db`).
- A test of the startup reconcile changes the list between two `Db` values on a database no
  other test uses.

**Real restricted cells are tested locally and never committed.** Njord is open source, and a
licensed S-57 cell cannot be in the repository. The synthetic chart above is the check that is
committed and runs everywhere. Real cells add what it cannot cover: the synthetic chart is
written through `Db`, so it never passes through `njord-s57` and ingest.

- `NJORD_RESTRICTED_CELLS` names a directory of restricted S-57 cells on the developer's machine.
  The suites that need it report that they were skipped and pass when it is unset.
- The directory must be outside the repository. A suite refuses a path inside it, so no
  `git add` can pick the cells up.
- Nothing derived from the cells is committed: no golden, no tile or archive, and no chart name,
  coordinate or attribute value in test code, fixtures or documents. The assertions are
  structural (a count is zero or not, a layer is present or absent, a blob opens or does not),
  and each suite reads the cells' `DSID_AGEN` and builds `restrictedProducers` from it rather
  than naming a producer code.
- Each suite ingests into a database and a `chartTempData` of its own and removes both when it
  ends, pass or fail, so no decoded licensed data stays in the dev PostGIS or under `target/`.
- What these suites check: ingest reads the cell's `DSID_AGEN` as an integer and writes the chart
  `restricted`; an anonymous tile over the cell holds its outline and none of its features, and
  the admin's holds them; a licensed region over the cell exports encrypted with the cell in its
  catalog, and the manifest's `producerCodes` includes its producer; a public region over the
  same waters holds none of it.
- The conformance suite never runs against these cells. The first deployed check is the first
  restricted cell in staging ([The first restricted cell](#the-first-restricted-cell)).

**Stage 1**

- `crates/njord-db/tests/migrations.rs`: a fresh database and a migrated version 4 database both
  end at version 5 with `charts.restricted`.
- `crates/njord-db/tests/charts.rs`: a chart is written `restricted` when its `DSID_AGEN` is in
  the list and public otherwise, through ingest and through `POST /v1/chart`; a chart with no
  `DSID_AGEN` is public; the startup reconcile flips rows when the list changes and reports that
  it did.
- A chart whose `DSID_AGEN` is a string or a fraction is not written by `POST /v1/chart`. With
  such a row put in the table directly, the reconcile returns an error, and both binaries exit
  on it without serving.
- `crates/njord-server/tests/export.rs`: with a restricted chart inside a region, the region's
  archive holds it in no tile, `charts` row or `chart_tiles` row, and the public chart beneath it
  is drawn where it would have been. Ingesting or deleting the restricted chart does not mark
  the region stale.
- A configuration with `regionExports[].restricted` true fails startup with an error naming the
  region, and nothing is exported.
- `crates/njord-server/tests/tiles.rs`: with a restricted chart in the tile, an anonymous tile
  holds its `PLY` outline with `restricted = true`, none of its features, and the smaller-scale
  public chart or base map where it would have been drawn. `info=true` lists none of its
  features.
- `GET /v1/geojson` answers an empty 401 without a valid admin signature, for a public chart and
  for a restricted one. With one it answers as it does today, restricted charts included.
- `GET /v1/feature/layer/{layer}`, for a layer with features in a public chart and in a
  restricted one: an unsigned request pages through the public chart's features alone, and a
  signed one through both charts'. Every page of both reports the same `totalUnrestricted` and
  `totalRestricted`, which are each chart's count of that layer. An expired or malformed
  signature gets the unsigned pages, not a 401. An unknown layer is an empty page with both
  totals 0.
- `GET /v1/feature/lnam/{lnam}` for a restricted chart's feature is an empty 404 unsigned and
  the feature when signed. For an `LNAM` that a public and a restricted chart both carry, an
  unsigned request gets the public chart's feature.
- A signed `/v1/feature` response carries `Cache-Control: private`, and an unsigned one does
  not.
- `crates/njord-db/tests/charts.rs`: `find_layer_positions_page` and `find_feature` with
  `include_restricted` false and true, and the two totals.
- A tile requested with a valid admin signature holds the restricted chart's features and
  carries `Cache-Control: private`; `info=true` lists them, with the same header. The same tile
  with an expired or malformed signature is the public tile, not a 401.
- The same `z/x/y` requested with an admin signature and anonymously produces two cache files,
  `tiles/auth/<codes>/{z}/{x}/{y}.mvt` and `tiles/{z}/{x}/{y}.mvt`, and neither is served for the
  other. `GET /v1/cache` counts the public one only, and `DELETE /v1/cache` removes both.
- The web UI's layer browser loads without a sign-in, shows both totals, and says that the
  restricted charts' features need a sign-in when there are any. Signed in, it lists them.
- `GET /v1/chart?name=` reports `restricted` for both kinds of chart.
- The `PLY` style layer draws `CHRED` for `restricted = true` and `CURSR` otherwise, in every
  theme.
- `crates/njord-server/tests/openapi.rs`: the path list is unchanged, and the new fields appear
  in the schemas.
- The conformance goldens (`conformance/`) were recorded from the retired Kotlin server and are
  frozen. `Chart` gaining `restricted`, the layer page gaining its two totals
  (`feature_layer_BOYLAT`, `feature_layer_TOPMAR`, `feature_layer_unknown`), the `PLY` style, the
  `PLY` tile property and the 401 from the unsigned `GET /v1/geojson` specs
  (`test_api_04_charts.py`, `test_api_07_auth.py`) are decided divergences, each recorded as a
  spec's `port` expectation. The unsigned `/v1/feature` specs keep their recorded status.
- `crates/njord-model/tests/golden.rs` round-trips those frozen bodies through the wire types,
  and a type with a new field no longer round-trips to the recorded JSON. The test adds the
  port's fields (`restricted`, `totalUnrestricted`, `totalRestricted`, and in stage 2 the
  manifest's `restricted` and `producerCodes`) to the recorded body first, as `unmask` already
  substitutes masked values.

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
- With a region flagged `restricted` and `regionKeyMaster` absent or not 32 bytes, a process
  that runs the export worker fails to start with an error naming the region, and a
  `njord-server` with `enableIngestion` off starts. `export_region` called directly in that
  state fails and leaves no archive, no `.producers` sidecar and no `.tmp` file.
- An archive's `.producers` sidecar exists before the archive does: an export stopped before
  the rename leaves neither once it has cleaned up.
- The manifest reports `restricted` true for a licensed region and false for a public one,
  before and after the first export.
- A region does not claim another's archives: with regions `A` and `A_B` both exported, each
  manifest entry names its own archive, and exporting `A` past `MAX_ARCHIVES` prunes none of
  `A_B`'s. A file named `A_notastamp.mbtiles` is neither listed nor pruned.
- The manifest goldens and `testdata/regions/manifest.json` gain `restricted` and
  `producerCodes`, and `testdata/regions/timestamps.json` loses its prefix-overlap and
  unparsable-stem cases: decided divergences from the recorded Kotlin behavior, handled as
  stage 1's are.
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
- With two secrets configured, a token signed with either is valid. With the first removed, a
  token signed with it yields no regions.
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
- A request with a token gets `Cache-Control: private` on a tile in its region; the same tile
  requested anonymously gets the public tile with no `Cache-Control`. Both responses, and every
  other tile response, carry `Vary: Authorization`.
- A cross-origin preflight asking for `Authorization` is still an empty 403.
- A request that carries forwarded host and scheme headers gets a style and a TileJSON whose
  URLs name that origin, and a tile request with a token made that way is served as the same
  request made directly.
- A tile in a token's region is served from the cache file the admin's request wrote.
- `info=true` lists the features of exactly the charts the tile draws for the same request.
- With a valid token and no admin signature, `GET /v1/geojson` answers 401, and
  `GET /v1/feature` answers as it does for an anonymous request, restricted charts left out.

## Implementation checklist

Each stage is one release: built, tested, deployed to staging, checked there, then promoted to
production. A box is ticked when its change is merged with its tests. The section named in
parentheses is where this document specifies the item.

### Stage 1: restriction, refusal and admin access

**Before any code**

- [ ] Time the `/v1/feature` totals query on the largest layer in production. Choose totals on
      every page or on the first page only, and write the choice into
      [Routes that return feature data](#5-routes-that-return-feature-data) and
      [Open before stage 1 ships](#open-before-stage-1-ships).

**Configuration and startup** ([1. Configuration](#1-configuration))

- [ ] `restrictedProducers` on `ChartsConfig`, default `[]`.
- [ ] `regionExports[].restricted` on `RegionExportConfig`, default `false`.
- [ ] Startup fails, naming the region, when any region is flagged `restricted`, in
      `njord-server` and `njord-ingest`
      ([Stage 1: every archive is public](#stage-1-every-archive-is-public)).

**Database** ([2. Schema](#2-schema-chartsrestricted), [3. Models](#3-models))

- [ ] Migration 5, the same column in `INITIAL_SCHEMA`, and `CURRENT_VERSION` 5.
- [ ] `Db` holds the restricted producer list, set where the server builds it (`connect`).
- [ ] The `INSERT INTO charts` statement derives `restricted`; a non-integer `DSID_AGEN` fails
      the insert.
- [ ] The startup reconcile: run after `migrate` in both binaries, fatal on error. When it
      changes a row it clears the tile cache and every region's export state except `WORLD`,
      and wakes the export worker.
- [ ] `ChartInfo` gains `restricted` and `producer`, selected by `Db::find_chart_info`.
- [ ] `Db::find_layer_positions_page` and `Db::find_feature` take `include_restricted`; the page
      query returns the two totals.
- [ ] `Db::find_charts_in_region`, `Db::region_needs_rebuild` and `Db::regions_containing_chart`
      take the region's flag, which every stage 1 caller passes as false.

**Wire types** ([3. Models](#3-models))

- [ ] `Chart.restricted`, written through `CHART_COLUMNS` and `chart_from_row`.
- [ ] `LayerQueryResultPage` totals, in the form the measurement chose.
- [ ] `cargo check -p njord-model --target wasm32-unknown-unknown` passes.

**Tiles** ([4. The tile encoder](#4-the-tile-encoder), [6. Request access](#6-request-access),
[7. The tile cache](#7-the-tile-cache))

- [ ] `chart_access.rs`: the `ChartAccess` extractor with its admin half, `producers_in` and
      `is_admin`. An anonymous request is `Regions` with no region.
- [ ] `allowed_producers` threaded through `tile`, `info`, `archive_tile`, `chart_inputs` and
      `add_charts`; `eligible` tests it in both places.
- [ ] Each `PLY` outline carries `restricted`.
- [ ] `archive_tile` always gets the empty set.
- [ ] The tile handler takes `ChartAccess` and sets `Cache-Control: private` on a tile or an
      `info=true` response made with a non-empty set.
- [ ] `TileCache::get` and `put` take the producer set; a non-empty set is stored under
      `tiles/auth/<codes>/`.
- [ ] The tile route's `#[utoipa::path]` documents the optional `signature`.

**Feature routes** ([5. Routes that return feature data](#5-routes-that-return-feature-data))

- [ ] `GET /v1/geojson` takes `Signed`, with `security` and the 401 in its `#[utoipa::path]`.
- [ ] `GET /v1/feature` takes `ChartAccess`, passes `is_admin()` to both queries, and sets
      `Cache-Control: private` for the admin. Its `#[utoipa::path]` gains the optional
      `signature` and the new fields.

**Style** ([8. The coverage outline's color](#8-the-coverage-outlines-color))

- [ ] The `PLY` layer's `line_color` is the `case` expression; the style goldens are updated.

**Web UI** ([9. Clients](#9-clients))

- [ ] The layer browser sends the stored signature, shows both totals, and says when restricted
      charts' features need a sign-in.
- [ ] The map adds `signature` to tile requests through `transformRequest` when signed in.
- [ ] The chart page shows `restricted`.
- [ ] `cargo clippy -p njord-web --target wasm32-unknown-unknown -- -D warnings` passes.

**Tests** ([Verification](#verification))

- [ ] The access suites' fixture: a database of their own, a fixture cell, and the synthetic
      chart with producer 9999.
- [ ] Every stage 1 case in [Verification](#verification).
- [ ] The local suites behind `NJORD_RESTRICTED_CELLS` for ingest and tiles, refusing a path
      inside the repository and removing their database and `chartTempData`.
- [ ] `port` expectations for the conformance divergences, and the model golden test adding the
      port's fields before its round trip.
- [ ] `cargo test --workspace`, `cargo clippy --workspace --all-targets -- -D warnings` and the
      conformance suite pass.

**Release**

- [ ] `restrictedProducers` and `regionExports[].restricted` in the configuration table of
      `README.md` and `CLAUDE.md`; the release in `RELEASE_NOTES.md`, with the unsigned
      `GET /v1/geojson` 401 called out.
- [ ] Deployed to staging. The stage 1 row of
      [What each release changes](#what-each-release-changes-for-the-charts-there-today) holds,
      and `--compare-url` against production shows only those differences.
- [ ] Promoted to production.

### Stage 2: encrypted archives

**Configuration and startup** ([1. Configuration](#1-configuration), [Keys](#keys))

- [ ] The `chacha20poly1305` and `hkdf` crates.
- [ ] `regionKeyMaster` on `ChartsConfig`, in a type whose `Debug` does not print it.
- [ ] `cargo xtask secrets` delivers `regionKeyMaster`, optional and masked in a dry run.
- [ ] Stage 1's startup refusal is removed. A process that runs the export worker fails to
      start, naming the region, when a region is flagged `restricted` without a 32-byte master.

**Archive files** ([Region names and archive files](#region-names-and-archive-files))

- [ ] `Regions::archives_for_region` matches `<region>_<stamp>.mbtiles` exactly, and
      `Regions::archive_timestamp` is `None` without the `<region>_` prefix.
- [ ] `testdata/regions/timestamps.json` loses its prefix-overlap and unparsable-stem cases.

**Export** ([Offline region archives](#offline-region-archives))

- [ ] Each region's flag reaches `Db::find_charts_in_region`, `Db::region_needs_rebuild`,
      `Db::regions_containing_chart` and `archive_tile`.
- [ ] `archive_tile` returns the producer codes of the charts it drew, beside the bytes.
- [ ] The archive key and key id are derived from `regionKeyMaster` and the final archive name.
- [ ] A licensed region's tiles are sealed as `MXT1` blobs. The export fails rather than write a
      plain one.
- [ ] A licensed region's metadata: `njord:schema_version` 2, `njord:encryption` and
      `njord:key_id`. A public region's is unchanged.
- [ ] `<archive>.producers` is written before the rename, removed when the export fails, and
      pruned with its archive.
- [ ] The blob test vectors are checked in under `testdata/regions/`.

**Manifest** ([The manifest](#the-manifest-restricted-and-producercodes))

- [ ] `RegionManifestEntry` gains `restricted` and `producerCodes`; `Regions::manifest` fills
      them; `njord-model` still builds for `wasm32-unknown-unknown`.

**Tests** ([Verification](#verification))

- [ ] Every stage 2 case in [Verification](#verification).
- [ ] The local `NJORD_RESTRICTED_CELLS` suite for a licensed and a public region over a real
      cell.
- [ ] The manifest goldens and `testdata/regions/manifest.json` updated; `cargo test --workspace`,
      both clippy runs and the conformance suite pass.

**Release**

- [ ] `regionKeyMaster` in the configuration tables and in `deploy/k8s/README.md`; the release in
      `RELEASE_NOTES.md`.
- [ ] The test vectors handed to MX Mariner for its two decryptors.
- [ ] Deployed to staging. The stage 2 row of
      [What each release changes](#what-each-release-changes-for-the-charts-there-today) holds:
      a re-exported region reports `producerCodes` `[550]` and its archive still installs in the
      app.
- [ ] Promoted to production.

### Stage 3: region tokens

**Configuration and startup** ([1. Configuration](#1-configuration))

- [ ] `chartAccessJwtSecrets` (in a type whose `Debug` does not print it) and
      `chartAccessJwtIssuer` on `ChartsConfig`.
- [ ] `cargo xtask secrets` delivers `chartAccessJwtSecrets`, optional and masked in a dry run.
- [ ] The licensed regions' coverage is parsed at startup and kept in `AppState`; one that does
      not parse fails startup.

**Request access** ([6. Request access](#6-request-access))

- [ ] Token verification against every configured secret: `alg`, `exp`, `iss`, and no 500 for
      any malformed input.
- [ ] `ChartAccess::Regions` holds the kept regions; `producers_in` intersects the tile's
      envelope with their coverage.
- [ ] Every response of the tile route carries `Vary: Authorization`.
- [ ] `cors.rs` is unchanged, and a test pins the 403 for a preflight asking for
      `Authorization`.
- [ ] The tile route's `#[utoipa::path]` documents the optional bearer token.

**Tests** ([Verification](#verification))

- [ ] Every stage 3 case in [Verification](#verification), the forwarded-origin case among
      them.
- [ ] `cargo test --workspace`, both clippy runs and the conformance suite pass.

**Release**

- [ ] `chartAccessJwtSecrets` and `chartAccessJwtIssuer` in the configuration tables and in
      `deploy/k8s/README.md`; the release in `RELEASE_NOTES.md`.
- [ ] Deployed to staging. The stage 3 row of
      [What each release changes](#what-each-release-changes-for-the-charts-there-today) holds.
- [ ] Promoted to production.

### The first restricted cell

In staging first, then the same in production
([The first restricted cell](#the-first-restricted-cell)).

- [ ] All three stages are deployed in the environment.
- [ ] `restrictedProducers` holds the producer's code, and every `njord-server` and
      `njord-ingest` process has restarted with it.
- [ ] The new licensed region is in `regionExports` with `restricted: true`, and
      `regionKeyMaster` is set for the exporting process and held by the key issuer.
- [ ] `chartAccessJwtSecrets` holds a secret the key issuer mints with.
- [ ] The cell is ingested.
- [ ] `GET /v1/chart?name=` reports it `restricted`.
- [ ] An anonymous tile over it holds its outline and none of its features, and
      `GET /v1/feature` leaves its features out and counts them in `totalRestricted`.
- [ ] The admin's tile holds its features.
- [ ] The licensed region is re-exported: the manifest reports `restricted: true` and the
      producer in `producerCodes`, and the archive declares `njord:schema_version` 2.
- [ ] The public regions over the same waters hold none of it.
- [ ] A token for the region shows the chart online, and a token for no region does not.

### Outside njord

MX Mariner's work, which the first restricted cell also waits for
([Work outside njord](#work-outside-njord), `encryption.md` §12).

- [ ] The key issuer holds `regionKeyMaster`, derives archive keys, and checks its
      product-to-region mapping against the manifest's `restricted`.
- [ ] The key issuer mints tokens with one of `chartAccessJwtSecrets`.
- [ ] The apps open `MXT1` archives with the shared test vectors passing.
- [ ] `mxmariner.com/app` reaches njord through a proxy on its own origin.

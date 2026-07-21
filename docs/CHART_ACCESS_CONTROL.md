# Support for license encumbered cartography
### Tile-range chart access control for restricted tile data

## Context

Njord supports ingesting and serving cartography data that is in the public domain and not subject to copyright in the United States. This is primarily designed for NOAA Electronic Navigational Chart (ENC) data where NOAA formally dedicates its ENC data and digital products to the public domain, allowing anyone to use, copy, and modify them for any purpose. https://nauticalcharts.noaa.gov/data/data-licensing.html However, Njord currently has no support for serving license encubered data.
This document describes a strategy to support this data in a way that only allows authorized users access to the data deligating the responsibility of authentication  & authorization to a third party.

## Technical Context

Njord currently has no concept of user identity or per-chart access control — every chart's full S-57 feature data is served to anyone who requests a tile, and `/v1/chart?id=` metadata is fully public. The goal is to support charts whose detailed data is gated behind an entitlement: unauthorized users should still see the chart's coverage outline (already rendered as a separate `PLY` layer) and its metadata via `/v1/chart?id=`, but not the actual S-57 feature data (soundings, buoys, depth areas, etc.) in the map tiles.

A separate external server is authoritative on user accounts and entitlements. Njord itself should stay stateless about users — it only needs to *verify* an entitlement claim attached to a request, not manage accounts.

Decisions made (this expands on the terse "account tiles" line in `docs/TODO.md`):
- **Auth transport**: a signed JWT issued by the external server, verified locally by njord (no per-tile network call — tile requests happen in bursts of dozens per pan/zoom, so a network round-trip per tile is a non-starter for latency/availability).
- **Restriction granularity**: geographic tile ranges, not subscription tiers. Each range is a rectangle of tiles — `top`/`left`/`bottom`/`right` tile coordinates (`x0,y0` to `x1,y1`) at a single reference zoom `z` — and the JWT carries the list of ranges a user is entitled to (one per licensed region; a user can hold several). Access to a *requested* tile is granted when that tile falls within a range's rectangle, **projected to the requested zoom level**: a licensed region is valid at every zoom, not just the zoom it was defined at. This still avoids enumerating chart ids in the token (a benefit the old tiered design also had) and maps more naturally onto "buy access to this region" than an ordinal subscription tier.
- **Zoom projection**: given a range defined at zoom `z0` with tile bounds `[x0,y0]..[x1,y1]`, and a request at zoom `z1`:
  - if `z1 >= z0` (zooming in): scale bounds up by `2^(z1-z0)` — `x0*2^d..(x1+1)*2^d-1` (same for y) — and check the requested `x,y` falls inside.
  - if `z1 < z0` (zooming out): scale bounds down by `2^(z0-z1)` — `x0/2^d..x1/2^d` (integer division) — and check the requested `x,y` falls inside.
  - Note this is a coarse approximation when zooming out: a single low-zoom tile can cover area slightly outside the original licensed rectangle if the rectangle didn't align to a power-of-2 boundary. Acceptable for v1 — flagged here so it isn't mistaken for a bug later.
- **Offline/mobile export**: region SQLite archives (`RegionExporter`) will only ever include unrestricted charts. Restricted data is never baked into offline bundles — there's no per-tile-request auth context in the bulk export path, so tile-range checks don't apply there; this is a clean line for a first version and avoids inventing per-user offline bundles right away.

Key existing facts that shape the design:
- `TileEncoder.addCharts()` (`server/src/nativeMain/kotlin/io/madrona/njord/geo/TileEncoder.kt:106-187`) already separates concerns nicely: it fetches `ChartInfo` per intersecting chart, builds an `eligibleChartIds` list (line 117) used only to fetch **feature** rows via `chartDao.findAllChartFeaturesAsync4326`, while the coverage-boundary `PLY` feature (lines 174-178) is added **unconditionally** for every intersecting chart regardless of whether it contributed features. This means restricting feature data while always keeping the outline visible is a small, surgical change: just filter `eligibleChartIds` by whether the request's tile is authorized.
- The server already links OpenSSL via cinterop and has a working `HmacSHA256` HMAC-SHA256 implementation (`server/src/nativeMain/kotlin/io/madrona/njord/endpoints/AdminHandler.kt:116-150`), used today for signing admin-mutation URLs. JWT verification should reuse this exact primitive (HS256, shared secret with the external auth server) rather than pulling in a JVM-only JWT library (Ktor's `auth-jwt`/`java-jwt` are JVM-only and unavailable on Kotlin/Native) or building RSA/JWKS verification from scratch. HS256 with a pre-shared secret is the pragmatic first version; note this means the shared secret must be kept only between njord and the external auth server (njord must never expose an endpoint that lets a client mint tokens).
- Tile responses are cached on the filesystem, keyed only by `z/x/y` (`server/src/nativeMain/kotlin/io/madrona/njord/db/TileCache.kt`). Once tile content varies by authorization outcome, the cache key **must** reflect that, or an authorized/unauthorized response will leak to the wrong request. Because the authorization check is resolved to a single boolean *per requested tile* before the cache is touched, the cache key only needs that boolean — not the user's full range list — which is actually simpler than the old per-tier cache key (a handful of ordinal values).
- `charts` table (`server/src/nativeMain/kotlin/io/madrona/njord/db/DbMigrations.kt:97-118`) has no restriction column today. The migration file already uses an idempotent `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` pattern (line 49) for prior schema evolution — follow that.
- The `PLY` coverage-boundary layer (`layers/Ply.kt:18`) today hardcodes `lineColor = colorFrom(Color.CURSR, options.theme).json` for every chart, and `TileEncoder` emits that feature with no properties at all (`vectorTileEncoder.addFeature("PLY", emptyMap(), it)`, `TileEncoder.kt:177`). Once restriction is a per-chart flag, the outline should visually distinguish restricted charts using the existing `CHRED` color (already defined per-theme in `colors.json` alongside `CURSR`) instead of `CURSR`. `Soundg.kt:153-158` already has the exact pattern for this — a MapLibre `case` expression keyed off a feature property (there, `METERS`; here, `restricted`) picking between two `colorFrom(...)` results — so this is a small, precedented change, not a new styling mechanism.

## Interface with the external auth service

Njord's only contract with the external auth server is: they mint HS256 JWTs with a shared
secret (`chartAccessJwtSecret`), njord verifies them locally. No njord endpoint issues,
refreshes, or introspects tokens — that's entirely the external server's job. Two concrete
examples of what crosses that boundary:

**1. Decoded JWT payload** the external auth server produces after a user licenses access to,
say, a Puget Sound region (zoom 8) and a separate San Juan Islands region (zoom 10):

```json
{
  "sub": "user_8f3a1c",
  "iss": "https://accounts.example.com",
  "iat": 1753100000,
  "exp": 1753113600,
  "ranges": [
    { "z": 8,  "x0": 40, "y0": 88, "x1": 42, "y1": 90 },
    { "z": 10, "x0": 163, "y0": 355, "x1": 165, "y1": 357 }
  ]
}
```

Encoded compact form (`base64url(header).base64url(payload).base64url(HMAC-SHA256(...))`),
e.g.:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzhmM2ExYyIsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZXhhbXBsZS5jb20iLCJpYXQiOjE3NTMxMDAwMDAsImV4cCI6MTc1MzExMzYwMCwicmFuZ2VzIjpbeyJ6Ijo4LCJ4MCI6NDAsInkwIjo4OCwieDEiOjQyLCJ5MSI6OTB9LHsieiI6MTAsIngwIjoxNjMsInkwIjozNTUsIngxIjoxNjUsInkxIjozNTd9XX0.<signature>
```

**2. Client tile request** — the frontend (per step 6 below) attaches this token to every tile
fetch via `transformRequest`, never as a query param:

```
GET /v1/tile/10/164/356.mvt HTTP/1.1
Host: openenc.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...<signature>
```

`chartAccessAuthorized(10, 164, 356)` finds `x=164,y=356` inside the second range
(`z=10, x0=163..x1=165, y0=355..y1=357`) directly (no projection needed, same zoom) → `true`,
so restricted charts intersecting that tile are included. A request for `/v1/tile/6/10/22.mvt`
(zoomed out from the first range) projects `z=8` range down by `2^2`: `x0/4..x1/4` = `10..10`,
`y0/4..y1/4` = `22..22` → tile `(10,22)` matches → also authorized. A request outside both
projected ranges gets `false`: the response still includes the `PLY` coverage outline for any
intersecting chart, just no feature layers for `restricted` ones.

An expired or tampered token, or one missing entirely, behaves identically to "no ranges
matched" — `chartAccessAuthorized` returns `false` rather than rejecting the request, since
anonymous/public browsing must keep working (see step 4).

## Implementation steps

### 1. Schema + config
- `db/DbMigrations.kt`: add `ALTER TABLE charts ADD COLUMN IF NOT EXISTS restricted BOOLEAN NOT NULL DEFAULT false;` — this replaces the ordinal `required_tier` from the earlier tiered design with a simple flag: a chart's feature data either requires *some* authorized tile-range match, or it doesn't.
- `db/RegionDao.kt:42` (`findChartsInRegion`): add `AND NOT restricted` so region SQLite exports only ever include unrestricted charts.
- `ChartsConfig.kt`: add a `chartAccessJwtSecret: String` (shared HMAC secret with the external auth server) and `chartAccessJwtIssuer: String` (expected `iss` claim, sanity check).

### 2. Models
- `shared/src/commonMain/kotlin/io/madrona/njord/model/Chart.kt`:
  - `ChartInfo`: add `val restricted: Boolean` — needed by `TileEncoder` to filter without a second query.
  - `Chart`: add `@SerialName("restricted") val restricted: Boolean` so `/v1/chart?id=` metadata exposes it (lets the frontend show "restricted — license this region to view" for the "basic information" the user is allowed to see).
  - `ChartInsert` stays unchanged — restriction isn't known at ingest time (S-57 files don't carry licensing info); it's set afterward via a small admin action.
- New shared model `TileRange` (e.g. `shared/src/commonMain/kotlin/io/madrona/njord/model/TileRange.kt`), the shape both njord and the external auth server agree on for the JWT `ranges` claim:
  ```kotlin
  @Serializable
  data class TileRange(
      val z: Int,   // reference zoom level
      val x0: Int,  // left tile x
      val y0: Int,  // top tile y
      val x1: Int,  // right tile x
      val y1: Int,  // bottom tile y
  ) {
      fun containsAtZoom(reqZ: Int, reqX: Int, reqY: Int): Boolean {
          val d = reqZ - z
          return if (d >= 0) {
              val scale = 1 shl d
              reqX in (x0 * scale)..((x1 + 1) * scale - 1) &&
              reqY in (y0 * scale)..((y1 + 1) * scale - 1)
          } else {
              val scale = 1 shl -d
              reqX in (x0 / scale)..(x1 / scale) &&
              reqY in (y0 / scale)..(y1 / scale)
          }
      }
  }
  ```

### 3. DAO changes (`db/ChartDao.kt`)
- `findInfoAsync` (line 183): add `restricted` to the `SELECT`, populate `ChartInfo.restricted`.
- `findAsync`/private `chart()` builder (lines 18-39, 213-239): add `restricted` to the `SELECT`, populate `Chart.restricted`.
- Add `updateRestrictedAsync(id: Long, restricted: Boolean): Boolean` — a simple `UPDATE charts SET restricted=$2 WHERE id=$1`.
- Expose `updateRestrictedAsync` via a new admin-signed route, e.g. extend `AdminHandler.kt` (or add a small new handler) with something like `PUT /v1/chart/restricted?id=&restricted=`, gated by the existing `requireSignature { ... }` (`AdminHandler.kt:152`) — reuses the existing admin-signature mechanism rather than inventing a new one.

### 4. JWT verification (new file, e.g. `server/src/nativeMain/kotlin/io/madrona/njord/auth/ChartAccessToken.kt`)
- Implement HS256 JWT verification: base64url-decode header/payload/signature, recompute HMAC-SHA256 over `header.payload` using `HmacSHA256(config.chartAccessJwtSecret)` (reuse the class from `AdminHandler.kt:116-150` — consider extracting it to a shared `crypto` file since both admin signatures and this now depend on it), compare signatures, check `exp` (and `iss` if set).
- Expected claims: `sub` (user id, informational), `ranges` (a JSON array of `TileRange` objects — the regions this user is entitled to).
- Expose as a plain suspend function, e.g. `ApplicationCall.chartAccessAuthorized(z: Int, x: Int, y: Int): Boolean` that reads the `Authorization: Bearer <jwt>` header, parses `ranges`, and returns `true` if **any** range's `containsAtZoom(z, x, y)` is true. Returns `false` if the header is absent, the token is invalid/expired, or no range matches — **do not** reject the request; anonymous/public access must keep working. (A full Ktor `Authentication` provider isn't a good fit here since both authenticated and anonymous requests must succeed on the same route — a plain header-parsing helper is simpler than fighting Ktor's reject-by-default auth model.)

### 5. Enforcement in tile serving
- `geo/TileEncoder.kt`: add a constructor parameter `val tileAuthorized: Boolean = false`. In `addCharts()` (line 117), change:
  ```kotlin
  val eligibleChartIds = charts.filter { it.zoom in 0..z && (!it.restricted || tileAuthorized) }.map { it.id }
  ```
  Leave the `charts.forEach { ... }` loop (line 124) and its unconditional `PLY` boundary emission (lines 174-178) untouched — this is what keeps chart outlines visible to everyone regardless of authorization. Do change the emission itself: `vectorTileEncoder.addFeature("PLY", emptyMap(), it)` → `vectorTileEncoder.addFeature("PLY", mapOf("restricted" to JsonPrimitive(chart.restricted)), it)`, so the tile carries per-feature restriction state for styling (independent of whether *this* requester is authorized — the outline color reflects the chart's `restricted` flag itself, not the viewer's access).
- `layers/Ply.kt`: replace the hardcoded `lineColor = colorFrom(Color.CURSR, options.theme).json` with a `case` expression on the new `restricted` property, mirroring `Soundg.textColor()` (`Soundg.kt:153-158`):
  ```kotlin
  lineColor = listOf(
      "case",
      listOf("==", listOf("get", "restricted"), true),
      colorFrom(Color.CHRED, options.theme),
      colorFrom(Color.CURSR, options.theme)
  ).json
  ```
  Restricted charts render their coverage outline in `CHRED`; unrestricted charts keep the existing `CURSR` outline.
- `db/TileDao.kt`: `getTile`/`getTileInfo` take an `authorized: Boolean = false` param, pass `tileAuthorized = authorized` into `TileEncoder(...)`.
- `db/TileCache.kt`: fold the boolean into the cache path (e.g. `$basePath/${if (authorized) "auth" else "pub"}/$z/$x/$y.mvt`) so cached tiles never leak across authorization outcomes — this is a correctness/security requirement, not an optimization detail.
- `endpoints/TileHandler.kt`: call `call.chartAccessAuthorized(z, x, y)` and pass the result into `tileDao.getTile(z, x, y, authorized)` / `getTileInfo(z, x, y, authorized)`.

### 6. Frontend token delivery (companion piece, `shared_fe`/`web`)
MapLibre GL fetches tiles via plain GET URLs. To attach the JWT without leaking it into logs/URLs/CDN cache keys, use MapLibre's `transformRequest` hook (wired in `shared_fe/src/jsMain/kotlin/io/madrona/njord/js/MapLibre.kt` and `ChartController.js.kt`) to add an `Authorization: Bearer <jwt>` header to tile and chart requests, rather than passing the token as a query parameter. This is a smaller, separate change once the server-side pieces above are in place — flagging it here so it isn't forgotten, not detailing it further since the immediate ask was the server-side access-control mechanism.

## Non Tile Data: `/v1/geojson`

`GeoJsonHandler.handleGet` (`server/src/nativeMain/kotlin/io/madrona/njord/endpoints/GeoJsonHandler.kt:20-29`) serves the raw `FeatureCollection` for a given `chart_id`/`layer_name` via `GeoJsonDao.fetchAsync`, with no restriction check at all. This is the same underlying feature data the tile-range JWT scheme gates — left as-is, this route bypasses the entire access-control mechanism for any restricted chart. It looks like a debug/ingest-inspection route today (see the curl examples in `ChartServerApp.kt:160-162`), not something `web`/`shared_fe` calls for rendering, but that should be confirmed before choosing an approach. Two options, not yet decided between:

1. **Block outright for restricted charts.** In `handleGet`, look up `chartDao.findInfoAsync(id)?.restricted` and return `HttpStatusCode.Forbidden` (or `NotFound`, to avoid confirming a restricted chart's existence) before calling `fetchAsync`, regardless of any JWT. Simplest option, and consistent with this route not being part of the authorized-viewing path — no caller needs partial/authorized access to it.
2. **Honor the same JWT.** Require `chartAccessAuthorized` to pass before returning data for a restricted chart. This is a worse fit than it looks: `TileRange`/`chartAccessAuthorized` are keyed on `z/x/y` tile coordinates, not chart ids, and the JWT was deliberately designed to avoid enumerating chart ids (see "Restriction granularity" above). Supporting this route would mean either computing which tiles a chart's coverage occupies and checking for range overlap, or adding a second, chart-id-based authorization shape — meaningfully more design work for a route that may not need it.

Option 1 is the natural default given the design intent above; only pursue option 2 if a real caller is found that needs authorized access through this route rather than tiles.

## Verification
- `./gradlew :server:allTests` for unit coverage of the new JWT verification (valid/expired/tampered/wrong-secret tokens), `TileRange.containsAtZoom` (zoom-in, zoom-out, and same-zoom cases, plus multiple ranges), and the restriction-filtering logic in `TileEncoder`.
- Manual check: ingest a chart, mark it `restricted` via the new admin route, confirm `/v1/tile/{z}/{x}/{y}` still returns the `PLY` outline for anonymous requests but omits feature layers; confirm a request with a valid JWT carrying a range covering that tile (at the same or a different zoom) returns full feature data; confirm `/v1/chart?id=` returns metadata (including `restricted`) regardless of auth.
- Confirm `RegionExporter` output for a region containing a restricted chart excludes that chart entirely (`RegionDao.findChartsInRegion` filter).
- Confirm tile cache correctness: same `z/x/y` requested once authorized and once unauthorized produces two distinct cached files, never cross-served.
- Confirm zoom projection: a range license at zoom 8 grants access to the corresponding tiles at zoom 12 (zoomed in) and at zoom 4 (zoomed out), per the scaling rules above.
- Confirm PLY styling: a chart marked `restricted` renders its coverage outline in `CHRED`, and an unrestricted chart still renders in `CURSR` — check both light and dark theme variants, and confirm the color does **not** change based on the requester's authorization (only on the chart's `restricted` flag).

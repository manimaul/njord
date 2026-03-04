# Njord Design Document

Njord is a Marine Electronic Navigational Chart (ENC) server. It ingests S-57 hydrographic chart files and serves them as MVT (Mapbox Vector Tiles) for display in a browser via MapLibre GL. It does **not** strictly follow IHO S-52 display specifications; styling is custom per S-57 object class.

Live demo: https://openenc.com

---

## Module Structure

| Module | Target | Purpose |
|--------|--------|---------|
| `server` | Kotlin/Native | HTTP server: ingestion, tile serving, API endpoints |
| `web` | Kotlin/JS | Compose frontend with MapLibre GL map |
| `shared` | Multiplatform (JVM/JS/Native) | Shared data models and serialization |
| `shared_fe` | Kotlin/JS | Frontend UI components, ViewModels, MapLibre bindings |
| `libgdal` | Kotlin/Native | C interop bindings to GDAL 3.6.2 |
| `libpq` | Kotlin/Native | C interop bindings to PostgreSQL client (`libpq`) |
| `libzip` | Kotlin/Native | C interop bindings for ZIP extraction |
| `geojson` | Multiplatform | GeoJSON RFC 7946 implementation |

---

## System Overview

```
┌───────────────────────────────────────────────────────────┐
│                     Browser Client                        │
│  (MapLibre GL + Kotlin/JS frontend compiled to JS)        │
└────────────────────────┬──────────────────────────────────┘
                         │ HTTP / WebSocket
                         ▼
┌───────────────────────────────────────────────────────────┐
│              Ktor CIO HTTP Server  :9000                  │
├───────────────────────────────────────────────────────────┤
│  /v1/tile/{z}/{x}/{y}         → MVT protobuf              │
│  /v1/style/{depth}/{theme}    → MapLibre GL style JSON    │
│  /v1/enc_save                 → Upload/delete S-57 ZIPs   │
│  /v1/chart_ws                 → WebSocket ingest progress │
│  /v1/chart*                   → Chart CRUD                │
│  /v1/content/*                → Fonts, sprites            │
│  /v1/admin                    → HMAC signature endpoint   │
│  /v1/about/*                  → S-57 object metadata      │
└──────┬───────────────────────────────┬─────────────────────┘
       │ Tile reads                    │ Writes on ingest
       ▼                               ▼
┌────────────────────────────────────────────────────────────┐
│            PostgreSQL 13 + PostGIS                         │
├────────────────────────────────────────────────────────────┤
│  charts        — chart metadata + coverage polygon (GIST)  │
│  features      — S-57 features in WGS84 (WKB + JSONB)     │
│  base_features — Natural Earth base map features           │
└────────────────────────────────────────────────────────────┘
```

---

## S-57 Ingestion

The server polls for uploaded chart ZIPs and processes them in the background.

### Flow

1. **Upload**: The user uploads a ZIP of S-57 `.000` files via the web UI. `EncSaveHandler` writes it to the uploads directory (`config.chartTempData/save/`).

2. **Claim**: `ChartIngestWorker` polls the `save/` directory every 5 seconds. When it finds a ZIP, it atomically renames it to `ingest/{uuid}/` to claim it and prevent duplicate processing.

3. **Unzip & read**: Files are extracted and opened via GDAL (`OgrS57Dataset` from `libgdal`). Each `.000` file represents one chart.

4. **Parallel processing**: Up to `config.chartIngestWorkers` (default: 5) charts are processed concurrently using coroutines.

5. **Per-chart processing**:
   - Extract chart metadata from the `DSID` layer (dataset ID, scale, dates).
   - Extract the coverage polygon from `M_COVR` — this is used for spatial indexing.
   - Calculate optimal display zoom from chart scale.
   - Convert all geometries to WGS84 (EPSG:4326).
   - Read all remaining layers and convert to GeoJSON `FeatureCollection` per layer.

6. **Persist**: In a single transaction:
   - Insert chart record into `charts` table (`ChartDao`).
   - Insert all features into `features` table (`FeatureDao`), each with its layer name, WKB geometry, and JSONB attribute properties.

7. **Progress**: `IngestStatus` broadcasts progress events to connected WebSocket clients (`ChartWebSocketHandler`).

8. **Cache invalidation**: The tile cache is cleared on ingestion completion.

### Key Files

- `server/src/nativeMain/kotlin/ingest/ChartIngest.kt` — Worker and processing logic
- `server/src/nativeMain/kotlin/db/ChartDao.kt` — Chart table queries
- `server/src/nativeMain/kotlin/db/FeatureDao.kt` — Feature table queries

---

## MVT Tile Serving

When a tile request arrives at `/v1/tile/{z}/{x}/{y}`, `TileEncoder` assembles the response.

### Flow

1. **Envelope**: Compute the WGS84 bounding box for tile `(z, x, y)`, expanded by 15 pixels to avoid clipping artifacts at tile edges.

2. **Find charts**: `ChartDao.findInfoAsync()` — spatial query (`ST_Intersects`) against `charts.covr` to find all charts whose coverage overlaps the tile, ordered by scale (most detailed first).

3. **Find features**: For each chart, `ChartDao.findChartFeaturesAsync4326()` runs `ST_Intersection` against `features.geom` filtered by `z_range` (the chart's SCAMIN/SCAMAX zoom range). This clips features to the tile envelope.

4. **Pre-encode styling**: `LayerFactory.preTileEncode()` dispatches each feature to its layer class (e.g., `Depare`, `Boyspp`). The layer mutates the feature's `props` JSONB to add computed display properties:
   - `SY` — Symbol/sprite name
   - `AC` — Area fill color
   - `LP` — Line pattern
   - `LC` — Line color

5. **Coordinate transform**: Feature geometries are converted from WGS84 degrees to MVT tile pixel coordinates (0–4096).

6. **Encode**: Features are added to a `VectorTileEncoder` (protobuf) grouped by layer name.

7. **Base map**: For tiles with no chart coverage, `BaseFeatureDao` queries Natural Earth data (`base_features`) at the appropriate scale as a fallback.

8. **Cache**: The encoded tile bytes are stored in an LRU in-memory `TileCache` (disabled if `config.debugTile = true`).

### Key Files

- `server/src/nativeMain/kotlin/geo/TileEncoder.kt`
- `server/src/nativeMain/kotlin/db/TileDao.kt`
- `server/src/nativeMain/kotlin/db/TileCache.kt`

---

## Styling

### Style JSON

`StyleHandler` returns a MapLibre GL style JSON at `/v1/style/{depth}/{theme}`. The style references:
- The tile source at `/v1/tile_json`
- Sprites from `/v1/content/sprites/...`
- Fonts (glyphs) from `/v1/content/fonts/...`
- One `Layer` object per S-57 object class per geometry type

Depth and theme variants are pre-generated by `LayerFactory` on first request and cached. Supported combinations:
- **Depth**: `meters` (meters/feet/fathoms), `feet` (feet/nautical miles), `feet_fm`
- **Theme**: `day` (light), `dark`, plus any custom themes from `ColorLibrary`

### Layer Classes

Each S-57 object class has a corresponding Kotlin class in `server/src/nativeMain/kotlin/layers/` that extends `Layerable`. A layer class has two responsibilities:

1. **`layers(options)`** — Returns the MapLibre GL `Layer` spec(s) for this object class (fill, line, symbol, etc.). These form part of the style JSON.

2. **`preTileEncode(feature)`** — Called during tile encoding. Mutates feature properties to add display hints that the style JSON reads via `["get", "SY"]`, `["get", "AC"]`, etc.

**Examples**:

- **`Depare`** (depth areas): `preTileEncode()` inspects `DRVAL1` (depth range minimum) and sets `AC` to one of `DEPIT / DEPVS / DEPMS / DEPMD / DEPDW` based on depth thresholds from config.

- **`Boyspp`** (buoys): `preTileEncode()` inspects `BOYSHP` (buoy shape) and `COLOUR` to select the correct sprite name, storing it as `SY`.

- **`Lndare`** (land areas): No `preTileEncode()` needed — uses a static fill + line style.

- **`Soundg`** (soundings): Multi-point geometry; each sounding depth is encoded as a separate symbol with the depth value formatted as the label.

### Sprite Sheets

Sprites are loaded from PNG sprite sheets embedded in resources. `IconHandler` serves individual PNG icons per theme at `/v1/icon/{name}.png` using `libgd` to crop from the sprite sheet.

---

## Database Schema

Managed by `DbMigrations.kt` (sequential SQL migrations applied at startup).

### `charts`

```sql
id          BIGSERIAL PRIMARY KEY
name        VARCHAR UNIQUE           -- DSID dataset name (e.g., US5WA46M)
scale       INTEGER                  -- chart scale denominator
file_name   VARCHAR                  -- source .000 filename
updated     VARCHAR                  -- DSID_UADT update date
issued      VARCHAR                  -- DSID_ISDT issue date
zoom        INTEGER                  -- optimal MapLibre zoom level
covr        GEOMETRY                 -- M_COVR coverage polygon (GIST indexed)
dsid_props  JSONB                    -- full DSID attributes
chart_txt   JSONB                    -- chart text file contents
```

### `features`

```sql
id          BIGSERIAL PRIMARY KEY
layer       VARCHAR                  -- S-57 object class (DEPARE, BOYSPP, …)
geom        GEOMETRY                 -- WKB in EPSG:4326 (GIST indexed)
props       JSONB                    -- S-57 attributes + computed display props
chart_id    BIGINT REFERENCES charts
lnam_refs   VARCHAR[]                -- LNAM cross-references (GIN indexed)
z_range     INT4RANGE                -- [SCAMIN, SCAMAX] zoom range (GIST indexed)
```

### `base_features`

Natural Earth data used as a base map for areas without chart coverage.

```sql
id          BIGSERIAL PRIMARY KEY
geom        GEOMETRY                 -- EPSG:4326 (GIST indexed)
props       JSONB
name        VARCHAR                  -- source shapefile name
scale       INTEGER                  -- NE scale: 10M / 50M / 110M
layer       VARCHAR                  -- mapped S-57 layer (e.g., LNDARE)
```

---

## Configuration

`ChartsConfig` is loaded from the `CHART_SERVER_OPTS` environment variable (JSON string) or a config file. Key fields:

```
pgConnectionInfo       — PostgreSQL connection string
host / port            — Ktor bind address (default 0.0.0.0:9000)
externalScheme/Host/Port — Public URL for TileJSON and sprite URLs
chartTempData          — Temp dir for uploads and caching (/mnt/njord/charts)
webStaticContent       — Absolute path to frontend build output
shallowDepth           — Depth threshold for "very shallow" coloring (default 3.0m)
safetyDepth            — Depth threshold for "moderate" coloring (default 6.0m)
deepDepth              — Depth threshold for "deep" coloring (default 9.0m)
adminKey               — HMAC-SHA256 key for admin signature generation
adminUser / adminPass  — Basic auth credentials for /v1/admin
adminExpirationSeconds — Signature TTL (default 604800 = 7 days)
chartIngestWorkers     — Concurrent S-57 processing workers (default 5)
debugTile              — Include debug envelope layer; disable tile caching
```

`webStaticContent` must be an **absolute** path because the server resolves static files relative to CWD, not the resources directory.

---

## Admin API

The admin API uses HMAC-SHA256 signatures rather than session cookies to authorize mutations.

1. Client calls `GET /v1/admin` with HTTP Basic auth (`adminUser` / `adminPass`).
2. Server returns a time-limited HMAC signature (expires after `adminExpirationSeconds`).
3. Client passes the signature as a query parameter `?signature=...` on mutating calls (`POST /v1/enc_save`, `DELETE /v1/enc_save`, etc.).

---

## Deployment (Kubernetes)

The server runs as a Deployment in the `njord` namespace with a pgbouncer sidecar.

**Pods**: 2–5 replicas (HPA scaling on 50% CPU)

**Containers per pod**:
- `njord-chart-svc` — the server binary (`server.kexe`), limits: 1 CPU / 3 GiB RAM
- `pgbouncer` — connection pooler sidecar; reachable at `localhost:5432`

**Storage**: NFS PVC (`/mnt/njord`) shared across replicas for chart upload/temp data

**Ingress**: HAProxy with cert-manager TLS termination for `openenc.com`

**Secrets**:
- `admin-secret-json` — `CHART_SERVER_OPTS` JSON with credentials
- `njord-pgbouncer-ini` / `njord-pgbouncer-userlist-txt` — pgbouncer config

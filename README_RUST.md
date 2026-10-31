# Njord (Rust)

The Rust port of Njord ([plan](docs/RUST_PORT_PLAN.md)) is a Cargo workspace at the repository root (`crates/`), built
alongside the Kotlin Gradle build. When the Kotlin code is removed this file becomes the main README.

| Crate | Purpose |
|---|---|
| `njord-model` | Shared API types; builds for native and `wasm32` (the contract with the web UI) |
| `njord-geo` | Tile system, Web Mercator math, clipping, MVT encoding |
| `njord-s57` | S-57 and shapefile reading through GDAL (the only crate that links GDAL) |
| `njord-symbology` | Style JSON generation and per-feature symbology |
| `njord-db` | PostGIS queries and schema migrations |
| `njord-server` | The axum HTTP server: tiles, API, ingest and region export workers |
| `njord-enc-cron` | Nightly NOAA updater: diffs NOAA's catalog against `/v1/chart_editions`, bundles stale cells into `save/` |
| `njord-web` | The web UI: Leptos (client-side, `wasm32`) built with Trunk; depends on `njord-model` only |
| `njord-testdata` | Locates the golden fixtures under `testdata/` for tests |

## Requirements

- A Rust toolchain (edition 2024)
- GDAL 3.8–3.13 (`brew install gdal` on macOS, `libgdal-dev` on Debian). The georust `gdal` crate is pinned to a
  commit with prebuilt bindings, so a plain `cargo build` works; no bindgen or libclang.
- The dev PostGIS from `chart_server_db/` (Podman)
- For the web UI: `rustup target add wasm32-unknown-unknown` and `cargo install --locked trunk`. Trunk downloads the
  matching `wasm-bindgen` and `wasm-opt` on first use. MapLibre GL and Bootstrap are vendored, so no npm.

## Configuration

`njord-server` takes the same configuration as the Kotlin server:

1. **`<resources dir>/config/application.json`**: the base configuration. The resources directory is the server's
   first argument; until the Kotlin code is removed it is `server/src/nativeMain/resources`, so the file is
   [`server/src/nativeMain/resources/config/application.json`](server/src/nativeMain/resources/config/application.json).
   The container image replaces it with [`deploy/mxmariner/application.json`](deploy/mxmariner/application.json).
2. **`CHART_SERVER_OPTS`**: an environment variable holding a JSON object merged over `application.json`. The merge is
   shallow, so an override replaces a whole top-level key (`regionExports` is replaced, not extended).

| Key | Meaning |
|---|---|
| `pgConnectionInfo` | PostgreSQL URL. The server creates and migrates the schema itself; the database needs the `postgis` extension |
| `host`, `port` | Listen address |
| `chartTempData` | Working directory for uploads (`save/`, `ingest/`), region archives and the tile cache |
| `webStaticContent` | Web UI build output, served for any path no route answers |
| `adminUser`, `adminPass`, `adminKey`, `adminExpirationSeconds` | Admin login and signing key |
| `shallowDepth`, `safetyDepth`, `deepDepth`, `debugTile` | Symbology |
| `chartIngestWorkers`, `enableIngestion` | Ingest worker pool |
| `useTileCache` | On-disk tile cache (default `true`; the shipped `application.json` turns it off) |
| `enableRegionExport`, `regionExports` | Region `.mbtiles` archives to build after ingest (name, description, WKT coverage) |

Logging uses `RUST_LOG` (default `info`), e.g. `RUST_LOG=njord_server=debug`.

## Running

```bash
# 1. Start the dev PostGIS (PostgreSQL on 6432, pgbouncer on 5432; admin / mysecretpassword)
podman machine start
cd chart_server_db && podman-compose up -d && cd ..

# 2. Create a database for the Rust server, separate from the Kotlin server's s57server
psql postgresql://admin:mysecretpassword@localhost:6432/postgres -c 'CREATE DATABASE njord_rust'
psql postgresql://admin:mysecretpassword@localhost:6432/njord_rust -c 'CREATE EXTENSION postgis'

# 3. Build the web UI
(cd crates/njord-web && trunk build --release)

# 4. Build and run the server
cargo build --release -p njord-server
CHART_SERVER_OPTS='{
  "pgConnectionInfo": "postgresql://admin:mysecretpassword@localhost:6432/njord_rust",
  "webStaticContent": "./crates/njord-web/dist",
  "chartTempData": "/tmp/njord_rust"
}' ./target/release/njord-server ./server/src/nativeMain/resources
```

The server listens on http://localhost:9000. For development, `cargo run -p njord-server -- ./server/src/nativeMain/resources`
works the same way. `webStaticContent` can point at any directory with an `index.html` if only the API is needed.

### Web UI (`njord-web`)

`trunk build --release` (run in `crates/njord-web`) writes the static site to `crates/njord-web/dist/`; any server
serves it through `webStaticContent`. For development with rebuild on save, run the server on :9000 and

```bash
cd crates/njord-web && trunk serve      # http://localhost:8080, /v1 proxied to :9000
```

The ingest status WebSocket goes straight to :9000 when the page is on :8080, as the Kotlin dev server did.

### NOAA update job (`njord-enc-cron`)

The Rust port of [`enc_cron`](enc_cron/README.md), with the same flags and configuration: `<resources dir>/config/enc_cron.json`
(until Phase 7, [`enc_cron/src/nativeMain/resources/config/enc_cron.json`](enc_cron/src/nativeMain/resources/config/enc_cron.json))
with the `ENC_CRON_OPTS` JSON object merged over it, and the admin credentials for orphan deletes in
`ENC_CRON_ADMIN_USER` / `ENC_CRON_ADMIN_PASS`. It talks to the server only through `chartTempData/save/`,
`GET /v1/chart_editions`, `GET /v1/admin` and `DELETE /v1/chart`, so it works against either server.

```bash
cargo build --release -p njord-enc-cron
ENC_CRON_OPTS='{"chartEditionsUrl":"http://localhost:9000/v1/chart_editions","chartTempData":"/tmp/njord_rust"}' \
  ./target/release/njord-enc-cron ./enc_cron/src/nativeMain/resources --dry-run

# Parse a catalog already on disk instead of fetching ~52 MB each time
./target/release/njord-enc-cron ./enc_cron/src/nativeMain/resources --from-file ./ENCProdCat_19115.xml --dry-run
```

`chartTempData` must be the server's own `chartTempData`: bundles are renamed into its `save/` directory, where the
ingest worker picks them up. See [`enc_cron/README.md`](enc_cron/README.md) for orphan deletion and its guards.

### Container

```bash
deploy/mxmariner/deploy.sh --execute build    # builds ghcr.io/manimaul/njord:<version> from Containerfile.rust
```

See [`deploy/mxmariner/README.md`](deploy/mxmariner/README.md) for pushing and deploying. Never tag the Rust image as
`ghcr.io/manimaul/njord-chart-server`; that repository belongs to the Kotlin deployment.

## Testing

### Unit and integration tests

```bash
cargo test --workspace                                         # round-trips testdata/ and conformance/golden/
cargo check -p njord-model --target wasm32-unknown-unknown     # the contract crate must build for the web UI
cargo clippy -p njord-web --target wasm32-unknown-unknown -- -D warnings
cargo tree -p njord-web -e normal | grep njord-                # only njord-web and njord-model
cargo clippy --workspace --all-targets -- -D warnings
```

The `njord-db` and `njord-server` tests need the dev PostGIS running. They only create and drop databases named
`njord_rust_*`, so the Kotlin databases are never touched.

| Variable | Effect |
|---|---|
| `NJORD_TEST_PG` | PostgreSQL server URL without a database name (default `postgresql://admin:mysecretpassword@localhost:6432`); the role must be allowed to create databases |
| `NJORD_RESEED=1` | Drop and reseed the shared `njord_rust_server_test` database |
| `NJORD_TESTDATA` | Fixture directory (default `testdata/`) |
| `NJORD_LAYER` | Check only these symbology layer keys, e.g. `NJORD_LAYER=BOYLAT cargo test -p njord-symbology -- --nocapture` |
| `NJORD_TILE_DUMP=1` | Write the decoded Rust tiles under the test's temp directory |

### HTTP conformance suite

[`conformance/`](conformance/README.md) is a black-box Python suite that runs against a live server and compares it
with the goldens recorded from the Kotlin server. Run the server as above, on a fresh database and with
`"useTileCache": true` (the goldens were recorded with the cache on), then:

```bash
cd conformance
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt     # Python >= 3.12
.venv/bin/pytest . --base-url=http://localhost:9000
```

The suite seeds the server through the upload API and waits for the region exports, so the first run takes a few
minutes. `--compare-url` diffs two live servers (e.g. Kotlin on 9000, Rust on 9001) instead of using the goldens.

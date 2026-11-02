# Njord

![Logo](./crates/njord-web/assets/njord.jpg "Logo")

Njord is a Marine Electronic Navigational Chart (ENC) server that ingests S-57 hydrographic chart files and serves them
as MVT (Mapbox Vector Tiles). It does **not** strictly follow IHO S-52 display specifications. Live demo: https://njord.mxmariner.com

![Screenshot](./screenshot.png "Screenshot")

## Documentation

- [Design documentation](docs/DESIGN.md) (written for the Kotlin implementation)
- [NOAA ENC update cron](docs/NOAA_ENC_UPDATE_CRON.md)
- [Rust port plan](docs/RUST_PORT_PLAN.md)
- [Release notes](RELEASE_NOTES.md)

Njord is a Cargo workspace (`crates/`): an axum server and a Leptos (WebAssembly) web UI that only meet at the HTTP
API. It was ported from Kotlin/Native and Kotlin/JS ([plan](docs/RUST_PORT_PLAN.md)); the Kotlin code was removed in
Phase 7 and remains in the git history (`master`).

| Crate | Purpose |
|---|---|
| `njord-model` | Shared API types; builds for native and `wasm32` (the contract with the web UI) |
| `njord-geo` | Tile system, Web Mercator math, clipping, MVT encoding |
| `njord-s57` | S-57 and shapefile reading through GDAL (the only crate that links GDAL) |
| `njord-symbology` | Style JSON generation and per-feature symbology |
| `njord-db` | PostGIS queries and schema migrations |
| `njord-server` | The axum HTTP server (`njord-server`: tiles, API, ingest and region export workers) and `njord-ingest` (the workers alone) |
| `njord-enc-cron` | Nightly NOAA updater: diffs NOAA's catalog against `/v1/chart_editions`, bundles stale cells into `save/` |
| `njord-web` | The web UI: Leptos (client-side, `wasm32`) built with Trunk; depends on `njord-model` only |
| `xtask` (`njord-xtask`) | `cargo xtask <task>`: container image and Kubernetes deploy of the `mxmariner` environment (dry run unless `--execute`) |
| `njord-testdata` | Locates the golden fixtures under `testdata/` for tests |

## Requirements

- A Rust toolchain (edition 2024)
- GDAL 3.8–3.13 (`brew install gdal` on macOS, `libgdal-dev` on Debian). The georust `gdal` crate is pinned to a
  commit with prebuilt bindings, so a plain `cargo build` works; no bindgen or libclang.
- The dev PostGIS from `chart_server_db/` (Podman)
- For the web UI: `rustup target add wasm32-unknown-unknown` and `cargo install --locked trunk`. Trunk downloads the
  matching `wasm-bindgen`, `wasm-opt` and the Tailwind CSS CLI on first use. MapLibre GL is vendored, so no npm.

## Configuration

`njord-server` takes the same configuration as the former Kotlin server:

1. **`<resources dir>/config/application.json`**: the base configuration. The resources directory is the server's
   first argument, [`crates/njord-server/resources`](crates/njord-server/resources), so the file is
   [`crates/njord-server/resources/config/application.json`](crates/njord-server/resources/config/application.json).
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
# 1. Start the dev PostGIS (PostgreSQL on 6432; admin / mysecretpassword)
podman machine start
cd chart_server_db && podman-compose up -d && cd ..

# 2. Create a database
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
}' ./target/release/njord-server ./crates/njord-server/resources
```

The server listens on http://localhost:9000. For development, `cargo run -p njord-server -- ./crates/njord-server/resources`
works the same way. `webStaticContent` can point at any directory with an `index.html` if only the API is needed.

### Web UI (`njord-web`)

`trunk build --release` (run in `crates/njord-web`) writes the static site to `crates/njord-web/dist/`; any server
serves it through `webStaticContent`. For development with rebuild on save, run the server on :9000 and

```bash
cd crates/njord-web && trunk serve      # http://localhost:8080, /v1 proxied to :9000
```

The ingest status WebSocket goes straight to :9000 when the page is on :8080.

### NOAA update job (`njord-enc-cron`)

See [`crates/njord-enc-cron/README.md`](crates/njord-enc-cron/README.md). It talks to the server only through
`chartTempData/save/`, `GET /v1/chart_editions`, `GET /v1/admin` and `DELETE /v1/chart`.

### Ingest-only process (`njord-ingest`)

`njord-ingest <resources dir>` runs the ingest and region export workers without the HTTP server, with the same
configuration as `njord-server` (it ignores `enableIngestion`). The Kubernetes `njord-ingest-rs` pod runs it beside
`njord-chart-dep` pods that have `enableIngestion: false`.

### Container

```bash
cargo xtask build              # prints the podman command (every task is a dry run by default)
cargo xtask --execute build    # builds ghcr.io/manimaul/njord:<version> from Containerfile
cargo xtask help               # all tasks: secrets, nfs, build, push, apply, rollout, deploy, render, ...
```

See [`deploy/mxmariner/README.md`](deploy/mxmariner/README.md) for pushing and deploying. Never tag this image as
`ghcr.io/manimaul/njord-chart-server`; that repository belongs to the Kotlin deployment of `openenc.com`, which runs
from the `master` branch until the cutover (docs/RUST_PORT_PLAN.md §10.6).

### Debian package

See [`debpkg/README.md`](debpkg/README.md).

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
`njord_rust_*`.

| Variable | Effect |
|---|---|
| `NJORD_TEST_PG` | PostgreSQL server URL without a database name (default `postgresql://admin:mysecretpassword@localhost:6432`); the role must be allowed to create databases |
| `NJORD_RESEED=1` | Drop and reseed the shared `njord_rust_server_test` database |
| `NJORD_TESTDATA` | Fixture directory (default `testdata/`) |
| `NJORD_LAYER` | Check only these symbology layer keys, e.g. `NJORD_LAYER=BOYLAT cargo test -p njord-symbology -- --nocapture` |
| `NJORD_TILE_DUMP=1` | Write the decoded Rust tiles under the test's temp directory |

### HTTP conformance suite

[`conformance/`](conformance/README.md) is a black-box Python suite that runs against a live server and compares it
with the goldens recorded from the former Kotlin server. Run the server as above, on a fresh database and with
`"useTileCache": true` (the goldens were recorded with the cache on), then:

```bash
cd conformance
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt     # Python >= 3.12
.venv/bin/pytest . --base-url=http://localhost:9000
```

The suite seeds the server through the upload API and waits for the region exports, so the first run takes a few
minutes. `--compare-url` diffs two live servers (e.g. `https://njord.mxmariner.com` against `https://njord.mxmariner.com`) instead of
using the goldens.

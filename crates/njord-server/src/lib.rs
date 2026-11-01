//! The Njord chart server: an axum HTTP server that serves S-57 charts as Mapbox Vector Tiles.
//!
//! The `njord-server` binary loads a [`ChartsConfig`], [`connect`]s to PostGIS, [`migrate`]s
//! the schema, builds the [`AppState`] and serves [`router`] until [`shutdown_signal`], with the
//! background workers from [`spawn_workers`] beside it. The `njord-ingest` binary runs only the
//! workers ([`spawn_ingest_workers`]). Everything lives in this library so that tests drive the
//! router in-process (`tower::ServiceExt::oneshot`) and replay the conformance goldens without a
//! socket.
//!
//! # Request handling, outermost first
//!
//! 1. Tracing of every request.
//! 2. CORS ([`cors`]): preflights are answered here, before routing.
//! 3. A panic in a handler becomes a 500.
//! 4. The routes ([`handlers`]), each answering an empty 405 for the verbs it does not implement
//!    ([`handlers::api`]), then the web app for any path no route matches ([`handlers::web`]).
//!
//! The external base URL that style and TileJSON URLs are built from comes from the forwarded
//! headers ([`forwarded`]).
//!
//! # Tiles
//!
//! [`AppState::db`] is a lazy `njord_db` pool on `pgConnectionInfo`; [`migrate`] brings the schema
//! to the current version before the server listens. `GET /v1/tile/{z}/{x}/{y}`
//! ([`handlers::tile`]) assembles a tile from the chart and base map features with
//! [`tile_encoder`] and keeps it in the on-disk [`tile_cache`] when `useTileCache` is on. Styles,
//! sprites and icons come from the resources directory ([`state`], [`sprite_sheet`],
//! [`gd_png`]).
//!
//! # Write path
//!
//! Every mutating verb (chart and GeoJSON insert, chart, cache and nuke deletes, uploads, region
//! re-render, the region archive download, the status socket) is behind the admin signature check
//! ([`handlers::Signed`], [`signature`]): an empty 401 without a valid signature. `GET /v1/admin`
//! mints signatures behind basic auth; `POST /v1/admin/verify` refreshes one.
//!
//! # Ingest and region export
//!
//! Uploads land in `<chartTempData>/save`; [`spawn_workers`] starts the ingest worker
//! ([`ingest::IngestWorker`]) that claims and ingests them and the region export worker
//! ([`regions::RegionWorker`]) that renders stale region `.mbtiles` archives, both coordinated
//! across replicas by the lock file ([`lock`]). They are started by the binaries, not by
//! [`router`], so tests drive the routes without them.

pub mod config;
pub mod cors;
pub mod forwarded;
pub mod gd_png;
pub mod handlers;
pub mod ingest;
pub mod lock;
pub mod regions;
pub mod signature;
pub mod sprite_sheet;
pub mod state;
pub mod tile_cache;
pub mod tile_encoder;

use std::sync::Arc;

use axum::Router;
use axum::extract::DefaultBodyLimit;
use axum::middleware;
use axum::routing::{delete, get, post};
use njord_db::{AdvisoryLock, Db};
use tower_http::catch_panic::CatchPanicLayer;
use tower_http::trace::{DefaultMakeSpan, DefaultOnResponse, TraceLayer};
use tracing::Level;

pub use config::ChartsConfig;
pub use state::AppState;

use handlers::{
    about, admin, api, cache, charts, content, enc_save, enc_url, feature, geojson, icon, nuke,
    regions as region_routes, sector_svg, style, tile, tile_json, web, ws,
};

/// Connections the server keeps to PostgreSQL.
pub const MAX_DB_CONNECTIONS: u32 = 10;

/// Builds the connection pool for `config.pgConnectionInfo`; nothing connects until the first
/// query.
///
/// # Errors
///
/// Returns an error when the connection URL cannot be parsed.
pub fn connect(config: &ChartsConfig) -> Result<Db, njord_db::Error> {
    Db::connect_lazy(&config.pg_connection_info, MAX_DB_CONNECTIONS)
}

/// Runs the schema migrations and returns the schema version reached.
///
/// The migrations run under a PostgreSQL advisory lock, so servers starting concurrently on one
/// database migrate it once.
///
/// # Errors
///
/// Returns an error when the database cannot be reached or a migration fails.
pub async fn migrate(db: &Db) -> Result<i32, njord_db::Error> {
    let lock = AdvisoryLock::new(db.pool().clone(), AdvisoryLock::MIGRATION_KEY);
    db.migrate(&lock).await
}

/// Starts the background workers when `enableIngestion` is set.
///
/// These are the ingest worker, and the region export worker unless `enableRegionExport` is off.
/// The handles are the tasks; dropping them does not stop the workers.
pub fn spawn_workers(state: &Arc<AppState>) -> Vec<tokio::task::JoinHandle<()>> {
    if !state.config.enable_ingestion {
        tracing::info!("ingestion disabled by config");
        return Vec::new();
    }
    spawn_ingest_workers(state)
}

/// Starts the background workers whatever `enableIngestion` says, for the `njord-ingest` binary.
///
/// These are the ingest worker, and the region export worker unless `enableRegionExport` is off.
pub fn spawn_ingest_workers(state: &Arc<AppState>) -> Vec<tokio::task::JoinHandle<()>> {
    let mut handles = Vec::new();
    handles.push(tokio::spawn(ingest::IngestWorker::new(state.clone()).run()));
    if state.config.enable_region_export {
        handles.push(tokio::spawn(
            regions::RegionWorker::new(state.clone()).run(),
        ));
    } else {
        tracing::info!("region export disabled by config");
    }
    handles
}

/// Resolves on SIGINT, or SIGTERM on unix.
///
/// # Panics
///
/// Panics when the SIGTERM handler cannot be installed.
pub async fn shutdown_signal() {
    let interrupt = tokio::signal::ctrl_c();
    #[cfg(unix)]
    {
        let mut term = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("SIGTERM handler");
        tokio::select! {
            _ = interrupt => {}
            _ = term.recv() => {}
        }
    }
    #[cfg(not(unix))]
    let _ = interrupt.await;
}

/// Builds the whole HTTP API.
///
/// `{x}` matches one path segment and `{*x}` the rest; `/v1/about` and `/v1/content` are routed
/// on their own so the bare prefix answers too. A trailing slash matches no route.
pub fn router(state: Arc<AppState>) -> Router {
    Router::new()
        .route("/v1/about", api(get(about::root)))
        .route("/v1/about/{*path}", api(get(about::about)))
        .route("/v1/tile_json", api(get(tile_json::tile_json)))
        .route("/v1/style/{depth}/{theme}", api(get(style::style)))
        .route("/v1/icon/{name}", api(get(icon::icon)))
        .route(
            "/v1/sector_svg/{theme}/{sectr1}/{sectr2}/{color}",
            api(get(sector_svg::sector_svg)),
        )
        .route("/v1/content", api(get(content::root)))
        .route("/v1/content/{*content}", api(get(content::content)))
        .route(
            "/v1/chart",
            api(get(charts::chart)
                .post(charts::insert)
                .delete(charts::delete)),
        )
        .route("/v1/chart_catalog", api(get(charts::chart_catalog)))
        .route("/v1/chart_editions", api(get(charts::chart_editions)))
        .route(
            "/v1/geojson",
            api(get(geojson::geojson).post(geojson::insert)),
        )
        .route("/v1/tile/{z}/{x}/{y}", api(get(tile::tile)))
        .route("/v1/cache", api(get(cache::cache).delete(cache::clear)))
        .route("/v1/feature/{by}/{arg}", api(get(feature::feature)))
        .route(
            "/v1/regions",
            api(get(region_routes::manifest).post(region_routes::rerender)),
        )
        .route("/v1/regions/{archive}", api(get(region_routes::archive)))
        .route(
            "/v1/enc_save",
            api(post(enc_save::upload).delete(enc_save::reset)),
        )
        .route("/v1/enc_url", api(post(enc_url::download)))
        .route("/v1/nuke", api(delete(nuke::nuke)))
        .route("/v1/admin", api(get(admin::admin)))
        .route("/v1/admin/verify", api(post(admin::verify)))
        .route("/v1/ws/enc_process", api(get(ws::enc_process)))
        .fallback(web::fallback)
        .with_state(state)
        // Uploads are whole ENC bundles, so the body size is not limited.
        .layer(DefaultBodyLimit::disable())
        .layer(CatchPanicLayer::new())
        .layer(middleware::from_fn(cors::cors))
        .layer(
            TraceLayer::new_for_http()
                .make_span_with(DefaultMakeSpan::new().level(Level::INFO))
                .on_response(DefaultOnResponse::new().level(Level::INFO)),
        )
}

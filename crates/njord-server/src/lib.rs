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
//! The routes are also the OpenAPI document ([`openapi`]): `GET /v1/openapi.json`, rendered by
//! Swagger UI at `/v1/api_docs/`.
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
//! re-render, the region archive download, the status socket) and the change report routes
//! ([`handlers::change_reports`]) are behind the admin signature check ([`handlers::Signed`],
//! [`signature`]): an empty 401 without a valid signature. `GET /v1/admin` mints signatures
//! behind basic auth; `POST /v1/admin/verify` refreshes one. Every change the write path makes
//! is recorded in the change history (`docs/change_reports.md`).
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
pub mod openapi;
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
use axum::routing::get;
use njord_db::{AdvisoryLock, Db};
use tower_http::catch_panic::CatchPanicLayer;
use tower_http::trace::{DefaultMakeSpan, DefaultOnResponse, TraceLayer};
use tracing::Level;
use utoipa::OpenApi;
use utoipa_axum::router::{OpenApiRouter, UtoipaMethodRouterExt};
use utoipa_axum::routes;

pub use config::ChartsConfig;
pub use state::AppState;

use handlers::{
    about, admin, api, cache, change_reports, charts, content, enc_save, enc_url, feature, geojson,
    icon, nuke, regions as region_routes, sector_svg, style, tile, tile_json, web, ws,
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
///
/// Every route goes through [`routes!`], which registers it with axum and in the OpenAPI
/// document at once, wrapped by [`api`] for the empty 405s. The two wildcard routes are mounted
/// by hand ([`openapi`] explains). Swagger UI and the document itself are merged in after; they
/// are plain axum routes, so they answer axum's 405 (with an `Allow` header) rather than
/// [`api`]'s.
pub fn router(state: Arc<AppState>) -> Router {
    let (router, document) =
        OpenApiRouter::<Arc<AppState>>::with_openapi(openapi::ApiDoc::openapi())
            .routes(routes!(about::root).map(api))
            .route("/v1/about/{*path}", api(get(about::about)))
            .routes(routes!(tile_json::tile_json).map(api))
            .routes(routes!(style::style).map(api))
            .routes(routes!(icon::icon).map(api))
            .routes(routes!(sector_svg::sector_svg).map(api))
            .routes(routes!(content::root).map(api))
            .route("/v1/content/{*content}", api(get(content::content)))
            .routes(routes!(charts::chart, charts::insert, charts::delete).map(api))
            .routes(routes!(charts::chart_catalog).map(api))
            .routes(routes!(charts::chart_editions).map(api))
            .routes(routes!(change_reports::days).map(api))
            .routes(routes!(change_reports::report).map(api))
            .routes(routes!(geojson::geojson, geojson::insert).map(api))
            .routes(routes!(tile::tile).map(api))
            .routes(routes!(cache::cache, cache::clear).map(api))
            .routes(routes!(feature::feature).map(api))
            .routes(routes!(region_routes::manifest, region_routes::rerender).map(api))
            .routes(routes!(region_routes::archive).map(api))
            .routes(routes!(enc_save::upload, enc_save::reset).map(api))
            .routes(routes!(enc_url::download).map(api))
            .routes(routes!(nuke::nuke).map(api))
            .routes(routes!(admin::admin).map(api))
            .routes(routes!(admin::verify).map(api))
            .routes(routes!(ws::enc_process).map(api))
            .split_for_parts();
    router
        .merge(openapi::api_docs(document))
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

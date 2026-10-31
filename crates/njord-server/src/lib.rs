//! The Njord chart server (RUST_PORT_PLAN.md §3.2, Phase 3), ported from the Kotlin `server`
//! module (`Main.kt`, `ChartServerApp.kt`, `endpoints/`). axum on tokio; the binary in `main.rs`
//! only loads the configuration and serves [`router`].
//!
//! The library holds everything else so tests drive the router in-process
//! (`tower::ServiceExt::oneshot`) and replay the conformance goldens without a socket.
//!
//! # Request handling, outermost first
//!
//! 1. Tracing of every request (Kotlin's `CallLogging`).
//! 2. CORS ([`cors`]): preflights are answered here, before routing.
//! 3. A panic in a handler becomes a 500, as an uncaught Kotlin exception does.
//! 4. The routes ([`handlers`]), each with Kotlin's verb semantics ([`handlers::api`]), then the
//!    web app for any path no route matches ([`handlers::web`]).
//!
//! The external base URL that style and TileJSON URLs are built from comes from the forwarded
//! headers ([`forwarded`]).
//!
//! # Database
//!
//! [`AppState::db`] is a lazy `njord_db` pool on `pgConnectionInfo`; [`migrate`] brings the schema
//! to the current version before the server listens, as Kotlin's `DbMigrations.run()` does. Tiles
//! are assembled by [`tile_encoder`] and cached on disk by [`tile_cache`].
//!
//! # Mutating routes
//!
//! The read-only server registers every Kotlin mutating verb (chart and GeoJSON insert, chart,
//! cache and nuke deletes, uploads, region re-render) behind the admin signature check
//! ([`handlers::not_implemented`]): 401 without a valid signature, as Kotlin, and 501 with one.
//! Phase 4 replaces them. `GET /v1/admin` checks basic auth (401) and answers 501 to the admin;
//! `/v1/admin/verify` and `/v1/ws/enc_process` are not registered yet and fall through to the
//! web app.

pub mod config;
pub mod cors;
pub mod forwarded;
pub mod gd_png;
pub mod handlers;
pub mod regions;
pub mod signature;
pub mod sprite_sheet;
pub mod state;
pub mod tile_cache;
pub mod tile_encoder;

use std::sync::Arc;

use axum::Router;
use axum::middleware;
use axum::routing::{delete, get, post};
use njord_db::{AdvisoryLock, Db};
use tower_http::catch_panic::CatchPanicLayer;
use tower_http::trace::{DefaultMakeSpan, DefaultOnResponse, TraceLayer};
use tracing::Level;

pub use config::ChartsConfig;
pub use state::AppState;

use handlers::{
    about, admin, api, cache, charts, content, feature, geojson, icon, not_implemented,
    regions as region_routes, sector_svg, style, tile, tile_json, web,
};

/// Connections the server keeps to PostgreSQL.
pub const MAX_DB_CONNECTIONS: u32 = 10;

/// The pool for `config.pgConnectionInfo`. Nothing connects until the first query.
pub fn connect(config: &ChartsConfig) -> Result<Db, njord_db::Error> {
    Db::connect_lazy(&config.pg_connection_info, MAX_DB_CONNECTIONS)
}

/// Runs the schema migrations under the PostgreSQL advisory lock, so concurrently starting
/// servers migrate once; the schema version reached. (Kotlin serialises on a lock file under
/// `chartTempData` instead, which a Rust server does not see: do not start a Kotlin and a Rust
/// server on an unmigrated database at the same moment.)
pub async fn migrate(db: &Db) -> Result<i32, njord_db::Error> {
    let lock = AdvisoryLock::new(db.pool().clone(), AdvisoryLock::MIGRATION_KEY);
    db.migrate(&lock).await
}

/// The whole HTTP API. Routes are matched as Ktor matches the Kotlin route patterns: `{x}` is one
/// path segment, `{x...}` the rest (`/v1/about` and `/v1/content` included), no trailing slash.
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
                .post(not_implemented)
                .delete(not_implemented)),
        )
        .route("/v1/chart_catalog", api(get(charts::chart_catalog)))
        .route("/v1/chart_editions", api(get(charts::chart_editions)))
        .route(
            "/v1/geojson",
            api(get(geojson::geojson).post(not_implemented)),
        )
        .route("/v1/tile/{z}/{x}/{y}", api(get(tile::tile)))
        .route("/v1/cache", api(get(cache::cache).delete(not_implemented)))
        .route("/v1/feature/{by}/{arg}", api(get(feature::feature)))
        .route(
            "/v1/regions",
            api(get(region_routes::manifest).post(not_implemented)),
        )
        .route("/v1/regions/{archive}", api(get(region_routes::archive)))
        .route(
            "/v1/enc_save",
            api(post(not_implemented).delete(not_implemented)),
        )
        .route("/v1/enc_url", api(post(not_implemented)))
        .route("/v1/nuke", api(delete(not_implemented)))
        .route("/v1/admin", api(get(admin::admin)))
        .fallback(web::fallback)
        .with_state(state)
        .layer(CatchPanicLayer::new())
        .layer(middleware::from_fn(cors::cors))
        .layer(
            TraceLayer::new_for_http()
                .make_span_with(DefaultMakeSpan::new().level(Level::INFO))
                .on_response(DefaultOnResponse::new().level(Level::INFO)),
        )
}

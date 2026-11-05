//! Test databases on the dev PostGIS (`chart_server_db/`, `podman-compose up`).
//!
//! `NJORD_TEST_PG` overrides the server URL, without a database name (default
//! `postgresql://admin:mysecretpassword@localhost:6432`); the role must be allowed to create
//! databases. Every database the tests touch is named `njord_rust_test*` and is dropped and
//! recreated (from a PostGIS template) by the test that owns it, so other databases on the same
//! server (the dev `njord_rust`) are never touched. Tests that run in
//! parallel each own their database; the seeded ingest database is shared within its test
//! binary and seeded once.

#![allow(dead_code)]

use std::path::Path;

use geo_types::Geometry;
use njord_db::{AdvisoryLock, Db, InstalledChart};
use njord_model::JsonObject;
use njord_s57::{GdalCell, S57Reader};
use sqlx::postgres::PgPoolOptions;
use tokio::sync::Mutex;

pub const DEFAULT_PG: &str = "postgresql://admin:mysecretpassword@localhost:6432";

/// The database `CREATE DATABASE` is issued from.
const MAINTENANCE_DB: &str = "postgres";

/// `CREATE DATABASE` fails while another session uses its template, so creation is serialised.
static CREATE: Mutex<()> = Mutex::const_new(());

pub fn pg() -> String {
    std::env::var("NJORD_TEST_PG").unwrap_or_else(|_| DEFAULT_PG.to_owned())
}

pub fn url(database: &str) -> String {
    format!("{}/{database}", pg().trim_end_matches('/'))
}

/// Drops and recreates `njord_rust_test_<suffix>` with PostGIS and no njord schema.
pub async fn fresh_db(suffix: &str) -> Db {
    let name = format!("njord_rust_test_{suffix}");
    create_database(&name).await;
    db(&name)
}

/// [`fresh_db`] provisioned to the current schema.
pub async fn migrated_db(suffix: &str) -> Db {
    let db = fresh_db(suffix).await;
    migrate(&db).await;
    db
}

pub async fn migrate(db: &Db) {
    let lock = AdvisoryLock::new(db.pool().clone(), AdvisoryLock::MIGRATION_KEY);
    let version = db.migrate(&lock).await.expect("migrate");
    assert_eq!(version, njord_db::CURRENT_VERSION);
}

pub fn db(name: &str) -> Db {
    let pool = PgPoolOptions::new()
        .max_connections(4)
        .connect_lazy(&url(name))
        .expect("pool");
    Db::new(pool)
}

/// Holds PostGIS only; every test database is copied from it, which is much faster than
/// creating the extension each time. Kept between runs.
const TEMPLATE: &str = "njord_rust_test_template";

pub async fn create_database(name: &str) {
    assert!(name.starts_with("njord_rust_test"), "{name}");
    let _guard = CREATE.lock().await;
    let admin = PgPoolOptions::new()
        .max_connections(1)
        .connect(&url(MAINTENANCE_DB))
        .await
        .unwrap_or_else(|e| panic!("cannot connect to {}: {e} (start chart_server_db)", pg()));
    let run = async |sql: String| {
        sqlx::raw_sql(sqlx::AssertSqlSafe(sql))
            .execute(&admin)
            .await
            .unwrap_or_else(|e| panic!("{name}: {e}"));
    };
    let template: bool =
        sqlx::query_scalar("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = $1);")
            .bind(TEMPLATE)
            .fetch_one(&admin)
            .await
            .unwrap();
    if !template {
        run(format!("CREATE DATABASE {TEMPLATE};")).await;
        let t = PgPoolOptions::new()
            .max_connections(1)
            .connect(&url(TEMPLATE))
            .await
            .expect("connect");
        sqlx::query("CREATE EXTENSION IF NOT EXISTS postgis;")
            .execute(&t)
            .await
            .expect("postgis");
        t.close().await;
    }
    run(format!("DROP DATABASE IF EXISTS {name} WITH (FORCE);")).await;
    run(format!("CREATE DATABASE {name} TEMPLATE {TEMPLATE};")).await;
    admin.close().await;
}

pub async fn drop_database(db: Db) {
    let name: String = sqlx::query_scalar("SELECT current_database();")
        .fetch_one(db.pool())
        .await
        .unwrap();
    db.pool().close().await;
    let admin = PgPoolOptions::new()
        .max_connections(1)
        .connect(&url(MAINTENANCE_DB))
        .await
        .unwrap();
    sqlx::raw_sql(sqlx::AssertSqlSafe(format!(
        "DROP DATABASE IF EXISTS {name} WITH (FORCE);"
    )))
    .execute(&admin)
    .await
    .unwrap();
}

/// Runs `sql` (one or more statements) on `db`.
pub async fn exec(db: &Db, sql: &str) {
    sqlx::raw_sql(sqlx::AssertSqlSafe(sql.to_owned()))
        .execute(db.pool())
        .await
        .unwrap_or_else(|e| panic!("{sql}: {e}"));
}

/// `createTileClipPolygon(x, y, z, expandPixels = 15)`, the box both tile queries take.
pub fn tile_clip(z: u32, x: i64, y: i64) -> Geometry {
    njord_geo::TILE_SYSTEM
        .create_tile_clip_polygon(x, y, z, 15)
        .into()
}

pub fn wkt_geometry(wkt: &str) -> Geometry {
    let g = gdal::vector::Geometry::from_wkt(wkt).unwrap();
    njord_db::geometry::from_wkb(&g.wkb().unwrap()).unwrap()
}

/// `OgrGeometry.wkt` of a decoded geometry: the form the fixtures record (`OGR_G_ExportToWkt`).
pub fn ogr_wkt(geometry: &Geometry) -> String {
    let wkb = njord_db::geometry::to_wkb(geometry).unwrap();
    gdal::vector::Geometry::from_wkb(&wkb)
        .unwrap()
        .wkt()
        .unwrap()
}

/// Ingests one cell as the ingest worker does: the chart insert with overwrite, then every
/// feature of the inserted layers with a geometry, made valid, in one transaction.
pub async fn ingest_cell(db: &Db, path: &Path) -> InstalledChart {
    let cell = GdalCell::open(path).unwrap_or_else(|e| panic!("{}: {e}", path.display()));
    let insert = cell.chart_insert().unwrap();
    let mut rows: Vec<(String, JsonObject, Vec<u8>)> = Vec::new();
    for layer in cell.inserted_layers() {
        for feature in cell.features(&layer).unwrap_or_default() {
            if let Some(geometry) = feature.geometry.and_then(|g| g.make_valid()) {
                rows.push((layer.clone(), feature.props, geometry.wkb()));
            }
        }
    }
    db.install_chart(
        &insert,
        rows.iter().map(|(l, p, w)| (l.as_str(), p, w.as_slice())),
    )
    .await
    .unwrap_or_else(|e| panic!("{}: {e}", path.display()))
}

pub const WA: &str = "US5WA22M.000";
pub const OUTER: &str = "US4ZZ01M.000";
pub const INNER: &str = "US5ZZ02M.000";

/// The three fixture cells the seeded database is ingested from.
pub const CELLS: [&str; 3] = [
    "fixtures/US5WA22M/US5WA22M.000",
    "fixtures/synthetic/US4ZZ01M.000",
    "fixtures/synthetic/US5ZZ02M.000",
];

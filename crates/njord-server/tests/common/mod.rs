//! The seeded test database and server state the DB-backed tests share.
//!
//! The database is `njord_rust_server_test` on the dev PostGIS (`chart_server_db/`,
//! `podman-compose up`); `NJORD_TEST_PG` overrides the server URL without a database name
//! (default `postgresql://admin:mysecretpassword@localhost:6432`, as `njord-db`'s tests). It
//! holds the three fixture cells and the Natural Earth 110m coastline extract, ingested as
//! Kotlin's `TestCharts.ensureSeeded` does (GDAL read, `makeValid`, one transaction per cell),
//! and is created and seeded once, under a PostgreSQL advisory lock so the test binaries running
//! in parallel do not race. `NJORD_RESEED=1` drops and reseeds it. No other database is touched.

#![allow(dead_code)]

use std::path::{Path, PathBuf};
use std::sync::Arc;

use njord_db::Db;
use njord_db::sqlx::{self, postgres::PgPoolOptions};
use njord_model::JsonObject;
use njord_s57::{GdalCell, S57Reader, Shapefile, normalize_layer_name, scale_from_filename};
use njord_server::{AppState, ChartsConfig};
use njord_testdata::{repo_root, testdata};
use tokio::sync::OnceCell;

pub const DEFAULT_PG: &str = "postgresql://admin:mysecretpassword@localhost:6432";
pub const DATABASE: &str = "njord_rust_server_test";
const SEED_LOCK: i64 = 0x6e6a_6f72_6473_7276;

pub const CELLS: [&str; 3] = [
    "fixtures/US5WA22M/US5WA22M.000",
    "fixtures/synthetic/US4ZZ01M.000",
    "fixtures/synthetic/US5ZZ02M.000",
];
pub const CHART_NAMES: [&str; 3] = ["US5WA22M.000", "US4ZZ01M.000", "US5ZZ02M.000"];

pub fn pg() -> String {
    std::env::var("NJORD_TEST_PG").unwrap_or_else(|_| DEFAULT_PG.to_owned())
}

pub fn url(database: &str) -> String {
    format!("{}/{database}", pg().trim_end_matches('/'))
}

pub fn resources() -> PathBuf {
    repo_root().join("server/src/nativeMain/resources")
}

static SEEDED: OnceCell<()> = OnceCell::const_new();

/// The seeded database, created on first use.
pub async fn seeded_db() -> Db {
    SEEDED.get_or_init(seed).await;
    Db::connect_lazy(&url(DATABASE), 8).unwrap()
}

/// Server state on the seeded database, with its own `chartTempData` (tile cache off, as in
/// the shipped config) under the target directory.
pub async fn state(name: &str) -> Arc<AppState> {
    let db = seeded_db().await;
    state_with(name, db, "{}")
}

/// Server state on a lazy pool to the test database that the test never queries.
pub fn offline_state(name: &str) -> Arc<AppState> {
    let db = Db::connect_lazy(&url(DATABASE), 1).unwrap();
    state_with(name, db, "{}")
}

pub fn state_with(name: &str, db: Db, overrides: &str) -> Arc<AppState> {
    let temp = temp_dir(name);
    let mut config = ChartsConfig::load(&resources(), Some(overrides)).unwrap();
    config.chart_temp_data = temp;
    AppState::new(config, &resources(), db).unwrap()
}

/// A fresh, empty directory under `target/njord-server-tests/<name>`.
pub fn temp_dir(name: &str) -> PathBuf {
    let dir = Path::new(env!("CARGO_TARGET_TMPDIR"))
        .join("njord-server-tests")
        .join(name);
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

async fn seed() {
    let admin = PgPoolOptions::new()
        .max_connections(1)
        .connect(&url("postgres"))
        .await
        .unwrap_or_else(|e| panic!("cannot connect to {}: {e} (start chart_server_db)", pg()));
    let mut conn = admin.acquire().await.unwrap();
    sqlx::query("SELECT pg_advisory_lock($1);")
        .bind(SEED_LOCK)
        .execute(&mut *conn)
        .await
        .unwrap();
    let exists: bool =
        sqlx::query_scalar("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = $1);")
            .bind(DATABASE)
            .fetch_one(&mut *conn)
            .await
            .unwrap();
    let reseed = std::env::var("NJORD_RESEED").is_ok_and(|v| v == "1");
    if !exists || reseed || !complete().await {
        for sql in [
            format!("DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);"),
            format!("CREATE DATABASE {DATABASE};"),
        ] {
            sqlx::raw_sql(sqlx::AssertSqlSafe(sql))
                .execute(&mut *conn)
                .await
                .unwrap();
        }
        populate().await;
    }
    sqlx::query("SELECT pg_advisory_unlock($1);")
        .bind(SEED_LOCK)
        .execute(&mut *conn)
        .await
        .unwrap();
}

/// The three charts with features and the coastline are present.
async fn complete() -> bool {
    let Ok(pool) = PgPoolOptions::new()
        .max_connections(1)
        .connect(&url(DATABASE))
        .await
    else {
        return false;
    };
    let charts: i64 = sqlx::query_scalar(
        "SELECT count(DISTINCT chart_name) FROM features WHERE chart_name = ANY($1);",
    )
    .bind(CHART_NAMES.map(String::from).to_vec())
    .fetch_one(&pool)
    .await
    .unwrap_or(0);
    let base: i64 = sqlx::query_scalar("SELECT count(*) FROM base_features;")
        .fetch_one(&pool)
        .await
        .unwrap_or(0);
    pool.close().await;
    charts == 3 && base > 0
}

async fn populate() {
    let pool = PgPoolOptions::new()
        .max_connections(2)
        .connect(&url(DATABASE))
        .await
        .unwrap();
    sqlx::query("CREATE EXTENSION IF NOT EXISTS postgis;")
        .execute(&pool)
        .await
        .unwrap();
    let db = Db::new(pool);
    njord_server::migrate(&db).await.expect("migrate");
    for cell in CELLS {
        ingest_cell(&db, &testdata().join(cell)).await;
    }
    ingest_shapefile(
        &db,
        &repo_root().join("libgdal/src/nativeTest/resources/ne_110_extract/ne_110m_coastline.shp"),
    )
    .await;
    db.pool().close().await;
}

/// `TestIngest.ingestCharts` for one cell: insert with overwrite, then every feature of the
/// inserted layers with a geometry, repaired with GDAL's `makeValid`, in one transaction.
async fn ingest_cell(db: &Db, path: &Path) {
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
    .unwrap_or_else(|e| panic!("{}: {e}", path.display()));
}

/// `NaturalEarthIngest.processShapefile`.
async fn ingest_shapefile(db: &Db, path: &Path) {
    let shp = Shapefile::open(path).unwrap();
    let file_name = path.file_name().unwrap().to_str().unwrap();
    let scale = scale_from_filename(file_name);
    db.delete_base_features(file_name, scale).await.unwrap();
    for layer in shp.layer_names() {
        let layer_name = normalize_layer_name(&layer);
        for feature in shp.features(&layer).unwrap_or_default() {
            if let Some(geometry) = feature.geometry.and_then(|g| g.make_valid()) {
                db.insert_base_feature(
                    &geometry.wkb(),
                    &feature.props,
                    file_name,
                    scale,
                    &layer_name,
                )
                .await
                .unwrap();
            }
        }
    }
}

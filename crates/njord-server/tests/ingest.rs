//! The ingest path end to end (audit IN-1, IN-3; P-API-8's `completion_report` golden), through
//! the production worker on its own database: a NOAA-style zip dropped in `save/` is claimed,
//! unzipped, read by GDAL and installed; the status file ends on the `CompletionReport`; the lock
//! is released, the claim directory removed and the tile cache cleared. Also the Natural Earth
//! branch, a hostile zip (D3) and a lock held elsewhere (D6).

mod common;

use std::io::Write;
use std::path::Path;
use std::sync::Arc;

use njord_db::Db;
use njord_db::sqlx::{self, postgres::PgPoolOptions};
use njord_model::WsMsg;
use njord_server::AppState;
use njord_server::ingest::IngestWorker;
use njord_testdata::{golden_dir, load, repo_root, testdata};
use serde_json::json;

const DATABASE: &str = "njord_rust_server_ingest";

/// A fresh, migrated database of this test's own.
async fn fresh_db() -> Db {
    let admin = PgPoolOptions::new()
        .max_connections(1)
        .connect(&common::url("postgres"))
        .await
        .unwrap_or_else(|e| {
            panic!(
                "cannot connect to {}: {e} (start chart_server_db)",
                common::pg()
            )
        });
    for sql in [
        format!("DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);"),
        format!("CREATE DATABASE {DATABASE};"),
    ] {
        sqlx::raw_sql(sqlx::AssertSqlSafe(sql))
            .execute(&admin)
            .await
            .unwrap();
    }
    admin.close().await;
    let pool = PgPoolOptions::new()
        .max_connections(8)
        .connect(&common::url(DATABASE))
        .await
        .unwrap();
    sqlx::query("CREATE EXTENSION IF NOT EXISTS postgis;")
        .execute(&pool)
        .await
        .unwrap();
    let db = Db::new(pool);
    njord_server::migrate(&db).await.unwrap();
    db
}

/// A NOAA-style zip: `ENC_ROOT/<cell>/<files>` (`conformance/lib/seed.py` `zip_cells`).
fn zip_cell_dir(dir: &Path, cell: &str) -> Vec<u8> {
    let mut entries = Vec::new();
    let mut files: Vec<_> = std::fs::read_dir(dir)
        .unwrap()
        .flatten()
        .map(|e| e.path())
        .collect();
    files.sort();
    for f in files {
        let name = f.file_name().unwrap().to_string_lossy().into_owned();
        if !f.is_file() || name.ends_with(".md") {
            continue;
        }
        if name.to_ascii_uppercase().starts_with(cell)
            || name.to_ascii_uppercase().ends_with(".TXT")
        {
            entries.push((
                format!("ENC_ROOT/{cell}/{name}"),
                std::fs::read(&f).unwrap(),
            ));
        }
    }
    zip_of(&entries)
}

fn zip_of(entries: &[(String, Vec<u8>)]) -> Vec<u8> {
    let mut cursor = std::io::Cursor::new(Vec::new());
    {
        let mut w = zip::ZipWriter::new(&mut cursor);
        let options = zip::write::SimpleFileOptions::default();
        for (name, data) in entries {
            w.start_file(name.as_str(), options).unwrap();
            w.write_all(data).unwrap();
        }
        w.finish().unwrap();
    }
    cursor.into_inner()
}

fn status(state: &AppState) -> WsMsg {
    serde_json::from_str(&state.status.read())
        .unwrap_or_else(|e| panic!("{e}: {}", state.status.read()))
}

fn drop_zip(state: &AppState, name: &str, bytes: &[u8]) {
    std::fs::create_dir_all(&state.save_dir).unwrap();
    std::fs::write(state.save_dir.join(name), bytes).unwrap();
}

fn dir_is_empty(dir: &Path) -> bool {
    std::fs::read_dir(dir)
        .map(|mut d| d.next().is_none())
        .unwrap_or(true)
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn worker_claims_and_ingests_through_the_production_path() {
    let db = fresh_db().await;
    let state = common::state_with(
        "ingest",
        db.clone(),
        &json!({"regionExports": []}).to_string(),
    );
    let worker = IngestWorker::new(state.clone());
    assert!(!worker.try_claim_and_ingest().await, "nothing to claim");

    // IN-1: the fixture cell with its updates and TXT sidecars.
    drop_zip(
        &state,
        "US5WA22M.zip",
        &zip_cell_dir(&testdata().join("fixtures/US5WA22M"), "US5WA22M"),
    );
    assert!(worker.try_claim_and_ingest().await);
    let report = status(&state);
    let golden = load(&golden_dir().join("api_08_ingest/completion_report.json"));
    let mut actual = serde_json::to_value(&report).unwrap();
    actual["ms"] = json!("<masked>");
    assert_eq!(
        golden["expected"]["body"], actual,
        "the P-API-8 completion report"
    );
    assert!(state.lock.is_free(), "the lock is released after ingest");
    assert!(
        dir_is_empty(&state.ingest_dir),
        "the claimed work directory is deleted"
    );
    assert!(
        dir_is_empty(&state.save_dir),
        "the zip was claimed out of save/"
    );
    assert!(state.tile_cache.counts().is_empty());
    let chart = db.find_chart("US5WA22M.000").await.unwrap().unwrap();
    assert_eq!((chart.scale, chart.zoom), (15000, 14));
    assert_eq!(chart.dsid_props["DSID_UPDN"], "4", "GDAL applied .001-.004");
    assert_eq!(chart.feature_count, 5158);
    assert_eq!(chart.chart_txt.len(), 4);

    // IN-3: re-ingesting leaves one chart row and one set of features.
    drop_zip(
        &state,
        "again.zip",
        &zip_cell_dir(&testdata().join("fixtures/US5WA22M"), "US5WA22M"),
    );
    assert!(worker.try_claim_and_ingest().await);
    assert!(matches!(status(&state), WsMsg::CompletionReport { .. }));
    let again = db.find_chart("US5WA22M.000").await.unwrap().unwrap();
    assert_eq!(again.feature_count, chart.feature_count);
    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM charts;")
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(count, 1);

    // Two cells in one zip, with one failing: the report lists it once with no features.
    let synthetic = testdata().join("fixtures/synthetic");
    let mut entries = vec![
        (
            "ENC_ROOT/US4ZZ01M/US4ZZ01M.000".to_owned(),
            std::fs::read(synthetic.join("US4ZZ01M.000")).unwrap(),
        ),
        (
            "ENC_ROOT/US5ZZ02M/US5ZZ02M.000".to_owned(),
            std::fs::read(synthetic.join("US5ZZ02M.000")).unwrap(),
        ),
        (
            "ENC_ROOT/BROKEN/BROKEN.000".to_owned(),
            b"not an S-57 cell".to_vec(),
        ),
    ];
    entries.sort();
    drop_zip(&state, "synthetic.zip", &zip_of(&entries));
    assert!(worker.try_claim_and_ingest().await);
    let WsMsg::CompletionReport {
        total_chart_count,
        mut items,
        failed_charts,
        ..
    } = status(&state)
    else {
        panic!("{:?}", status(&state));
    };
    items.sort_by(|a, b| a.chart_name.cmp(&b.chart_name));
    assert_eq!(total_chart_count, 3);
    assert_eq!(
        items
            .iter()
            .map(|i| i.chart_name.as_str())
            .collect::<Vec<_>>(),
        ["US4ZZ01M.000", "US5ZZ02M.000"]
    );
    assert!(items.iter().all(|i| i.feature_count > 0));
    assert_eq!(failed_charts.len(), 1);
    assert!(
        failed_charts[0].starts_with("BROKEN.000 msg: "),
        "{failed_charts:?}"
    );
    assert_eq!(db.list_charts(None).await.unwrap().total_chart_count, 3);

    // Natural Earth: a zip holding a shapefile goes to base_features.
    let ne = repo_root().join("testdata/fixtures/ne_110_extract");
    let mut entries: Vec<(String, Vec<u8>)> = ["shp", "shx", "dbf", "prj", "cpg"]
        .iter()
        .map(|ext| {
            let name = format!("ne_110m_coastline.{ext}");
            (name.clone(), std::fs::read(ne.join(&name)).unwrap())
        })
        .collect();
    entries.sort();
    drop_zip(&state, "ne.zip", &zip_of(&entries));
    assert!(worker.try_claim_and_ingest().await);
    let WsMsg::CompletionReport {
        items,
        failed_charts,
        total_chart_count,
        ..
    } = status(&state)
    else {
        panic!("{:?}", status(&state));
    };
    assert_eq!((total_chart_count, failed_charts.len()), (1, 0));
    assert_eq!(items[0].chart_name, "ne_110m_coastline.shp");
    let base: i64 = sqlx::query_scalar(
        "SELECT count(*) FROM base_features WHERE layer = 'coastline' AND scale = 110000000;",
    )
    .fetch_one(db.pool())
    .await
    .unwrap();
    assert_eq!(base, items[0].feature_count);
    assert!(base > 0);

    // D3: an unsafe entry aborts the whole extraction; nothing changes, the upload is an Error.
    let hostile = zip_of(&[
        (
            "ENC_ROOT/US4ZZ01M/US4ZZ01M.000".to_owned(),
            std::fs::read(synthetic.join("US4ZZ01M.000")).unwrap(),
        ),
        ("../escape.txt".to_owned(), b"x".to_vec()),
    ]);
    drop_zip(&state, "hostile.zip", &hostile);
    assert!(worker.try_claim_and_ingest().await);
    assert!(
        matches!(status(&state), WsMsg::Error { is_fatal: true, .. }),
        "{:?}",
        status(&state)
    );
    assert!(!state.config.chart_temp_data.join("escape.txt").exists());
    assert!(state.lock.is_free() && dir_is_empty(&state.ingest_dir));
    assert_eq!(db.list_charts(None).await.unwrap().total_chart_count, 3);

    // D6: a lock held by another instance is never taken; the zip stays queued.
    std::fs::write(state.lock.path(), "another-replica").unwrap();
    drop_zip(&state, "queued.zip", &zip_of(&entries));
    assert!(!worker.try_claim_and_ingest().await);
    assert!(state.save_dir.join("queued.zip").is_file());
    assert_eq!(state.lock.holder(), "another-replica");
    state.lock.try_clear();
    std::fs::remove_file(state.save_dir.join("queued.zip")).unwrap();

    db.pool().close().await;
}

#[allow(dead_code)]
fn _state_type(_: Arc<AppState>) {}

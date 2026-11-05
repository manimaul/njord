//! The ingest path end to end, through the production worker on its own database.
//!
//! The `api_08_ingest/completion_report` golden pins the report: a NOAA-style zip dropped in
//! `save/` is claimed, unzipped, read by GDAL and installed; the status file ends on the
//! `CompletionReport`; the lock is released, the claim directory removed and the tile cache
//! cleared. Also the Natural Earth branch, a hostile zip and a lock held elsewhere, and the change
//! history every step leaves behind (`docs/change_reports.md`): the cause from the sidecar or the
//! zip name, `new`/`update`/`error` per cell, and the two signed report routes over it.

mod common;

use std::io::Write;
use std::path::Path;
use std::sync::Arc;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use njord_db::Db;
use njord_db::sqlx::{self, Row, postgres::PgPoolOptions};
use njord_model::{
    AdminSignature, ChangeKind, ChangeOutcome, ChangeReport, ChangeReportPage, WsMsg,
};
use njord_server::AppState;
use njord_server::ingest::IngestWorker;
use njord_server::ingest::cause::sidecar_path;
use njord_testdata::{golden_dir, load, repo_root, testdata};
use serde_json::json;
use tower::ServiceExt;

const DATABASE: &str = "njord_rust_server_ingest";

/// A fresh, migrated database of this test's own.
async fn fresh_db() -> Db {
    fresh_db_named(DATABASE).await
}

async fn fresh_db_named(database: &str) -> Db {
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
        format!("DROP DATABASE IF EXISTS {database} WITH (FORCE);"),
        format!("CREATE DATABASE {database};"),
    ] {
        sqlx::raw_sql(sqlx::AssertSqlSafe(sql))
            .execute(&admin)
            .await
            .unwrap();
    }
    admin.close().await;
    let pool = PgPoolOptions::new()
        .max_connections(8)
        .connect(&common::url(database))
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

/// Every change event, oldest first: `(outcome, attempted, kind, name, change, cause, message,
/// zip_file)`, with `""` for a NULL.
async fn events(db: &Db) -> Vec<Vec<String>> {
    sqlx::query(
        "SELECT outcome, attempted, kind, name, feature_count_change::text, cause, message, zip_file
         FROM change_events ORDER BY id;",
    )
    .fetch_all(db.pool())
    .await
    .unwrap()
    .iter()
    .map(|r| {
        (0..8)
            .map(|i| r.get::<Option<String>, _>(i).unwrap_or_default())
            .collect()
    })
    .collect()
}

fn row(fields: [&str; 8]) -> Vec<String> {
    fields.iter().map(|f| (*f).to_owned()).collect()
}

/// A signature `GET /v1/admin` would mint for `http://localhost:9000` now.
fn signature(state: &AppState) -> String {
    let date = jiff::Timestamp::now();
    let uuid = "00000000-0000-4000-8000-000000000000".to_owned();
    let mac = njord_server::signature::hmac(
        &state.config.admin_key,
        &[
            "http://localhost:9000",
            &njord_model::instant::format(date),
            &uuid,
        ],
    );
    let sig = AdminSignature {
        date,
        signature: STANDARD.encode(mac),
        uuid,
        expiration_date: date,
    };
    let encoded = STANDARD.encode(serde_json::to_vec(&sig).unwrap());
    percent_encoding::utf8_percent_encode(&encoded, percent_encoding::NON_ALPHANUMERIC).to_string()
}

async fn get(state: &Arc<AppState>, path: &str) -> (StatusCode, Vec<u8>) {
    send(state, "GET", path, Vec::new()).await
}

async fn send(
    state: &Arc<AppState>,
    method: &str,
    path: &str,
    body: Vec<u8>,
) -> (StatusCode, Vec<u8>) {
    let response = njord_server::router(state.clone())
        .oneshot(
            Request::builder()
                .method(method)
                .uri(path)
                .header(header::HOST, "localhost:9000")
                .header(header::CONTENT_TYPE, "application/json")
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();
    let status = response.status();
    let body = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    (status, body.to_vec())
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

    // The fixture cell with its updates and TXT sidecars.
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
        "the api_08_ingest completion report"
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
    assert_eq!(
        events(&db).await,
        [row([
            "new",
            "",
            "chart",
            "US5WA22M.000",
            "5158",
            "admin",
            "",
            "US5WA22M.zip"
        ])],
        "no sidecar and no enc_bundle- prefix: an Admin new"
    );

    // Re-ingesting leaves one chart row and one set of features. The cron's sidecar names the
    // cause, and travels out of save/ with the zip.
    drop_zip(
        &state,
        "again.zip",
        &zip_cell_dir(&testdata().join("fixtures/US5WA22M"), "US5WA22M"),
    );
    std::fs::write(
        sidecar_path(&state.save_dir, "again.zip"),
        br#"{"cause":"noaa_enc_cron"}"#,
    )
    .unwrap();
    assert!(worker.try_claim_and_ingest().await);
    assert!(matches!(status(&state), WsMsg::CompletionReport { .. }));
    assert!(
        dir_is_empty(&state.save_dir),
        "the sidecar left with the zip"
    );
    assert_eq!(
        events(&db).await[1],
        row([
            "update",
            "",
            "chart",
            "US5WA22M.000",
            "0",
            "noaa_enc_cron",
            "",
            "again.zip"
        ])
    );
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
    drop_zip(&state, "enc_bundle-20260101-0001.zip", &zip_of(&entries));
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
    let mut recorded = events(&db).await[2..].to_vec();
    recorded.sort_by(|a, b| a[3].cmp(&b[3]));
    assert_eq!(
        recorded
            .iter()
            .map(|r| r[0..4].join(" "))
            .collect::<Vec<_>>(),
        [
            "error new chart BROKEN.000",
            "new  chart US4ZZ01M.000",
            "new  chart US5ZZ02M.000",
        ]
    );
    assert_eq!(recorded[0][4], "0");
    assert!(
        recorded[0][6].starts_with("failed to open ")
            && recorded[0][6].contains("BROKEN.000")
            && recorded[0][6].contains("with S57: "),
        "the recorded message is GDAL's, not a placeholder: {:?}",
        recorded[0][6]
    );
    assert!(
        recorded.iter().all(|r| r[5] == "noaa_enc_cron"),
        "the enc_bundle- fallback"
    );
    assert!(
        recorded
            .iter()
            .all(|r| r[7] == "enc_bundle-20260101-0001.zip")
    );
    assert!(recorded[1][4].parse::<i64>().unwrap() > 0);

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
    assert_eq!(
        events(&db).await[5],
        row([
            "new",
            "",
            "base_map",
            "ne_110m_coastline.shp",
            &base.to_string(),
            "admin",
            "",
            "ne.zip"
        ])
    );
    drop_zip(&state, "ne2.zip", &zip_of(&entries));
    assert!(worker.try_claim_and_ingest().await);
    assert_eq!(
        events(&db).await[6][..5],
        row([
            "update",
            "",
            "base_map",
            "ne_110m_coastline.shp",
            "0",
            "",
            "",
            ""
        ])[..5],
        "the same rows again: an update with no change"
    );

    // An unsafe entry aborts the whole extraction; nothing changes, the upload is an Error.
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
    assert_eq!(
        events(&db).await.len(),
        7,
        "a zip that never unpacked records nothing"
    );

    // A lock held by another instance is never taken; the zip stays queued.
    std::fs::write(state.lock.path(), "another-replica").unwrap();
    drop_zip(&state, "queued.zip", &zip_of(&entries));
    assert!(!worker.try_claim_and_ingest().await);
    assert!(state.save_dir.join("queued.zip").is_file());
    assert_eq!(state.lock.holder(), "another-replica");
    state.lock.try_clear();
    std::fs::remove_file(state.save_dir.join("queued.zip")).unwrap();

    // The report routes over what was recorded: signed only.
    let sig = signature(&state);
    assert_eq!(
        get(&state, "/v1/change_reports").await.0,
        StatusCode::UNAUTHORIZED
    );
    let (status, body) = get(&state, &format!("/v1/change_reports?signature={sig}")).await;
    assert_eq!(status, StatusCode::OK);
    let page: ChangeReportPage = serde_json::from_slice(&body).unwrap();
    assert_eq!(
        (page.page_index, page.page_size, page.total_days),
        (0, 25, 1)
    );
    assert_eq!(page.page.len(), 1);
    let today = page.page[0].date.clone();
    assert_eq!((page.page[0].actions, page.page[0].errors), (6, 1));
    assert!(
        get(
            &state,
            &format!("/v1/change_reports?page=1&signature={sig}")
        )
        .await
        .1
        .starts_with(br#"{"page":[],"#)
    );
    assert_eq!(
        get(
            &state,
            &format!("/v1/change_reports?page=x&signature={sig}")
        )
        .await
        .0,
        StatusCode::BAD_REQUEST
    );
    let (status, body) = get(
        &state,
        &format!("/v1/change_reports/{today}?signature={sig}"),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let report: ChangeReport = serde_json::from_slice(&body).unwrap();
    assert_eq!(report.date, today);
    assert_eq!(
        (
            report.counts.new,
            report.counts.update,
            report.counts.delete,
            report.counts.nuke,
            report.counts.error
        ),
        (4, 2, 0, 0, 1)
    );
    assert_eq!(report.items.len(), 7);
    assert_eq!(
        (report.items[0].name.as_str(), report.items[0].outcome),
        ("ne_110m_coastline.shp", ChangeOutcome::Update),
        "newest first: the last upload"
    );
    assert_eq!(
        (report.items[6].name.as_str(), report.items[6].outcome),
        ("US5WA22M.000", ChangeOutcome::New),
        "oldest last: the first upload"
    );
    assert!(
        report
            .items
            .windows(2)
            .all(|w| w[0].occurred_at >= w[1].occurred_at)
    );
    let broken = report
        .items
        .iter()
        .find(|i| i.name == "BROKEN.000")
        .expect("the failed cell");
    assert_eq!(broken.outcome, ChangeOutcome::Error);
    assert!(!broken.installed, "no chart row for a failed cell");
    assert!(
        report
            .items
            .iter()
            .filter(|i| i.name != "BROKEN.000")
            .all(|i| i.installed == (i.kind == ChangeKind::Chart)),
        "every other chart row is installed; a base map never is"
    );
    assert_eq!(
        (report.page_index, report.page_size, report.total_items),
        (0, 250, 7)
    );
    // The outcome filter keeps the day's counts and pages over the matching events alone; an
    // empty filter is an empty page, an unknown outcome is 400.
    let (status, body) = get(
        &state,
        &format!("/v1/change_reports/{today}?outcomes=error,delete&signature={sig}"),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let errors: ChangeReport = serde_json::from_slice(&body).unwrap();
    assert_eq!((errors.total_items, errors.counts), (1, report.counts));
    assert_eq!(errors.items.len(), 1);
    assert_eq!(errors.items[0].name, "BROKEN.000");
    let (status, body) = get(
        &state,
        &format!("/v1/change_reports/{today}?outcomes=&signature={sig}"),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let none: ChangeReport = serde_json::from_slice(&body).unwrap();
    assert_eq!(
        (none.total_items, none.items.len(), none.counts),
        (0, 0, report.counts)
    );
    assert_eq!(
        get(
            &state,
            &format!("/v1/change_reports/{today}?outcomes=new,nuked&signature={sig}")
        )
        .await
        .0,
        StatusCode::BAD_REQUEST
    );
    // Item pages: a page past the end keeps the day's counts and has no items; a bad page is 400.
    let (status, body) = get(
        &state,
        &format!("/v1/change_reports/{today}?page=1&signature={sig}"),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let past: ChangeReport = serde_json::from_slice(&body).unwrap();
    assert!(past.items.is_empty());
    assert_eq!((past.page_index, past.total_items), (1, 7));
    assert_eq!(past.counts, report.counts);
    assert_eq!(
        get(
            &state,
            &format!("/v1/change_reports/{today}?page=-1&signature={sig}")
        )
        .await
        .0,
        StatusCode::BAD_REQUEST
    );
    assert_eq!(
        get(
            &state,
            &format!("/v1/change_reports/2000-01-01?signature={sig}")
        )
        .await
        .0,
        StatusCode::NOT_FOUND
    );
    assert_eq!(
        get(
            &state,
            &format!("/v1/change_reports/2000-13-01?signature={sig}")
        )
        .await
        .0,
        StatusCode::BAD_REQUEST
    );
    assert_eq!(
        get(&state, &format!("/v1/change_reports/{today}")).await.0,
        StatusCode::UNAUTHORIZED
    );

    db.pool().close().await;
}

/// The handlers' events: `POST /v1/chart` (a row without features), `DELETE /v1/chart` with
/// its `cause`, and `DELETE /v1/nuke`, which records itself and keeps what came before.
#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn handlers_record_inserts_deletes_and_the_nuke() {
    let db = fresh_db_named("njord_rust_server_changes").await;
    let state = common::state_with(
        "changes",
        db.clone(),
        &json!({"regionExports": []}).to_string(),
    );
    let sig = signature(&state);
    let chart = json!({
        "name": "ZZ_TEST.000", "scale": 20000, "file_name": "ZZ_TEST.000",
        "updated": "20260101", "issued": "20260101", "zoom": 13,
        "covr": {"type": "Feature", "properties": {}, "geometry": {"type": "Polygon",
            "coordinates": [[[10.0, 10.0], [10.1, 10.0], [10.1, 10.1], [10.0, 10.1], [10.0, 10.0]]]}},
        "dsid_props": {"DSID_DSNM": "ZZ_TEST.000", "DSID_UPDN": "0"},
        "chart_txt": {}
    });
    let body = serde_json::to_vec(&chart).unwrap();
    let post = format!("/v1/chart?signature={sig}");
    assert_eq!(
        send(&state, "POST", &post, body.clone()).await.0,
        StatusCode::OK
    );
    let props = json!({"LNAM": "ZZ1"}).as_object().unwrap().clone();
    let point = njord_db::geometry::to_wkb(&geo_types::Geometry::Point(geo_types::Point::new(
        10.05, 10.05,
    )))
    .unwrap();
    db.insert_feature("BOYLAT", "ZZ_TEST.000", &point, &props)
        .await
        .unwrap();
    db.insert_feature("LIGHTS", "ZZ_TEST.000", &point, &props)
        .await
        .unwrap();
    assert_eq!(send(&state, "POST", &post, body).await.0, StatusCode::OK);
    assert_eq!(
        send(
            &state,
            "POST",
            &post,
            serde_json::to_vec(&json!({"not": "a chart"})).unwrap()
        )
        .await
        .0,
        StatusCode::BAD_REQUEST,
        "an undecodable body is refused before anything is recorded"
    );
    let mut no_covr = chart.clone();
    no_covr["covr"]["geometry"] = json!(null);
    assert_eq!(
        send(&state, "POST", &post, serde_json::to_vec(&no_covr).unwrap())
            .await
            .0,
        StatusCode::BAD_REQUEST
    );
    assert_eq!(
        send(
            &state,
            "DELETE",
            &format!("/v1/chart?name=ZZ_TEST.000&cause=bogus&signature={sig}"),
            Vec::new()
        )
        .await
        .0,
        StatusCode::BAD_REQUEST
    );
    assert_eq!(
        send(
            &state,
            "DELETE",
            &format!("/v1/chart?name=NOPE.000&cause=noaa_enc_cron&signature={sig}"),
            Vec::new()
        )
        .await
        .0,
        StatusCode::NO_CONTENT
    );
    db.insert_feature("BOYLAT", "ZZ_TEST.000", &point, &props)
        .await
        .unwrap();
    assert_eq!(
        send(
            &state,
            "DELETE",
            &format!("/v1/chart?name=ZZ_TEST.000&cause=noaa_enc_cron&signature={sig}"),
            Vec::new()
        )
        .await
        .0,
        StatusCode::ACCEPTED
    );
    let recorded = events(&db).await;
    assert_eq!(
        recorded
            .iter()
            .map(|r| r[..6].join(" "))
            .collect::<Vec<_>>(),
        [
            "new  chart ZZ_TEST.000 0 admin",
            "update  chart ZZ_TEST.000 -2 admin",
            "error update chart ZZ_TEST.000 0 admin",
            "delete  chart ZZ_TEST.000 -1 noaa_enc_cron",
        ]
    );
    assert!(
        recorded[2][6].contains("null value"),
        "the insert's error text: {}",
        recorded[2][6]
    );

    db.insert_base_feature(&point, &props, "ne.shp", 110_000_000, "COALNE")
        .await
        .unwrap();
    assert_eq!(
        send(
            &state,
            "DELETE",
            &format!("/v1/nuke?signature={sig}"),
            Vec::new()
        )
        .await
        .0,
        StatusCode::NO_CONTENT
    );
    let recorded = events(&db).await;
    assert_eq!(recorded.len(), 5, "the history survives the nuke");
    assert_eq!(
        recorded[4][..6].join(" "),
        "nuke  all * -1 admin",
        "the base feature it removed"
    );
    let (status, body) = get(&state, &format!("/v1/change_reports?signature={sig}")).await;
    assert_eq!(status, StatusCode::OK);
    let page: ChangeReportPage = serde_json::from_slice(&body).unwrap();
    assert_eq!((page.page[0].actions, page.page[0].errors), (4, 1));
    let (_, body) = get(
        &state,
        &format!("/v1/change_reports/{}?signature={sig}", page.page[0].date),
    )
    .await;
    let report: ChangeReport = serde_json::from_slice(&body).unwrap();
    assert_eq!((report.counts.nuke, report.counts.delete), (1, 1));
    db.pool().close().await;
}

#[allow(dead_code)]
fn _state_type(_: Arc<AppState>) {}

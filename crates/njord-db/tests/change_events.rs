//! The change history (`change_events`, `docs/change_reports.md`), each test on its own database.
//!
//! Day aggregation and paging, the counts the mutations return (`insert_chart_tracked`,
//! `delete_chart`, `delete_base_features`, `truncate_all`), an event written in a rolled-back
//! `FeatureInstall`, and `day` under a non-UTC session time zone.

mod common;

use common::{drop_database, exec, migrated_db, wkt_geometry};
use njord_db::{Db, NewChangeEvent, REPORT_ITEM_PAGE_SIZE, REPORT_PAGE_SIZE};
use njord_model::{ChangeCause, ChangeKind, ChangeOutcome, ChartInsert, IndexMap, JsonObject};
use serde_json::json;

const SQUARE: &str = r#"{"type":"Polygon","coordinates":[[[-1,-1],[-1,1],[1,1],[1,-1],[-1,-1]]]}"#;

fn chart(name: &str) -> ChartInsert {
    ChartInsert {
        name: name.into(),
        scale: 10000,
        file_name: name.into(),
        updated: "20240101".into(),
        issued: "20240101".into(),
        zoom: 14,
        covr: geojson::Feature {
            geometry: Some(serde_json::from_str(SQUARE).unwrap()),
            properties: Some(JsonObject::new()),
            ..Default::default()
        },
        dsid_props: json!({ "DSID_UPDN": "1" }).as_object().unwrap().clone(),
        chart_txt: IndexMap::new(),
    }
}

async fn add_feature(db: &Db, chart: &str, lnam: &str) {
    let props = json!({ "LNAM": lnam }).as_object().unwrap().clone();
    let wkb = njord_db::geometry::to_wkb(&wkt_geometry("POINT(0 0)")).unwrap();
    assert_eq!(
        db.insert_feature("BOYLAT", chart, &wkb, &props)
            .await
            .unwrap(),
        1
    );
}

fn event(name: &str, outcome: ChangeOutcome, change: i64) -> NewChangeEvent {
    NewChangeEvent {
        kind: ChangeKind::Chart,
        name: name.into(),
        outcome,
        attempted: (outcome == ChangeOutcome::Error).then_some(ChangeOutcome::Update),
        feature_count_change: change,
        cause: ChangeCause::Admin,
        message: (outcome == ChangeOutcome::Error).then(|| "boom".to_owned()),
        zip_file: None,
    }
}

#[tokio::test]
async fn days_aggregate_and_page_newest_first() {
    let db = migrated_db("change_days").await;
    // 30 synthetic days, oldest first: day n (1-based) has n actions and n % 3 errors.
    for n in 1..=30i64 {
        let day = format!("2026-01-{n:02}");
        for i in 0..n {
            exec(
                &db,
                &format!(
                    "INSERT INTO change_events (occurred_at, kind, name, outcome, feature_count_change, cause)
                     VALUES ('{day}T10:{i:02}:00Z', 'chart', 'C{i}.000', 'update', 1, 'admin');"
                ),
            )
            .await;
        }
        for i in 0..(n % 3) {
            exec(
                &db,
                &format!(
                    "INSERT INTO change_events (occurred_at, kind, name, outcome, attempted, feature_count_change, cause, message)
                     VALUES ('{day}T11:{i:02}:00Z', 'chart', 'E{i}.000', 'error', 'new', 0, 'noaa_enc_cron', 'failed to open');"
                ),
            )
            .await;
        }
    }
    let first = db.change_report_days(0, REPORT_PAGE_SIZE).await.unwrap();
    assert_eq!((first.page_index, first.page_size), (0, 25));
    assert_eq!(first.total_days, 30);
    assert_eq!(first.page.len(), 25);
    assert_eq!(first.page[0].date, "2026-01-30");
    assert_eq!((first.page[0].actions, first.page[0].errors), (30, 0));
    assert_eq!((first.page[1].actions, first.page[1].errors), (29, 2));
    assert_eq!(first.page[24].date, "2026-01-06");
    let second = db.change_report_days(1, REPORT_PAGE_SIZE).await.unwrap();
    assert_eq!(second.page.len(), 5);
    assert_eq!(second.page[4].date, "2026-01-01");
    assert_eq!((second.page[4].actions, second.page[4].errors), (1, 1));
    assert!(
        db.change_report_days(2, REPORT_PAGE_SIZE)
            .await
            .unwrap()
            .page
            .is_empty()
    );
    assert_eq!(
        db.change_report_days(-1, 0).await.unwrap().page.len(),
        1,
        "page and size are clamped"
    );

    let report = db
        .change_report("2026-01-29", 0, REPORT_ITEM_PAGE_SIZE)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(report.date, "2026-01-29");
    assert_eq!(
        (report.page_index, report.page_size, report.total_items),
        (0, 250, 31)
    );
    assert_eq!(
        (
            report.counts.update,
            report.counts.error,
            report.counts.new,
            report.counts.nuke
        ),
        (29, 2, 0, 0)
    );
    assert_eq!(report.items.len(), 31);
    assert_eq!(
        report.items[0].outcome,
        ChangeOutcome::Error,
        "errors first"
    );
    assert_eq!(report.items[0].attempted, Some(ChangeOutcome::New));
    assert_eq!(report.items[0].message.as_deref(), Some("failed to open"));
    assert_eq!(report.items[0].cause, ChangeCause::NoaaEncCron);
    assert_eq!(report.items[2].name, "C0.000");
    assert_eq!(
        report.items[0].occurred_at.to_string(),
        "2026-01-29T11:00:00Z"
    );

    // Item paging: 31 items in pages of 10, the whole day's counts on every page, and a page
    // past the end still answers (with no items) because the day exists.
    let mut paged = Vec::new();
    for page in 0..4 {
        let part = db
            .change_report("2026-01-29", page, 10)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(
            (part.page_index, part.page_size, part.total_items),
            (page, 10, 31)
        );
        assert_eq!(part.counts, report.counts, "page {page}");
        assert_eq!(
            part.items.len(),
            if page < 3 { 10 } else { 1 },
            "page {page}"
        );
        paged.extend(part.items);
    }
    assert_eq!(paged, report.items, "the pages are the full list in order");
    let past = db
        .change_report("2026-01-29", 4, 10)
        .await
        .unwrap()
        .unwrap();
    assert!(past.items.is_empty());
    assert_eq!((past.total_items, past.counts), (31, report.counts));
    let clamped = db
        .change_report("2026-01-29", -1, 0)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(
        (clamped.page_index, clamped.page_size, clamped.items.len()),
        (0, 1, 1),
        "page and size are clamped"
    );

    assert!(
        db.change_report("2025-12-31", 0, REPORT_ITEM_PAGE_SIZE)
            .await
            .unwrap()
            .is_none()
    );
    assert!(
        db.change_report("not-a-date", 0, REPORT_ITEM_PAGE_SIZE)
            .await
            .is_err(),
        "the caller validates the date"
    );
    drop_database(db).await;
}

#[tokio::test]
async fn mutations_return_the_counts_the_report_needs() {
    let db = migrated_db("change_counts").await;
    let (_, before) = db
        .insert_chart_tracked(&chart("a.000"), true)
        .await
        .unwrap();
    assert_eq!(before, None, "no chart existed");
    add_feature(&db, "a.000", "L1").await;
    add_feature(&db, "a.000", "L2").await;
    let (_, before) = db
        .insert_chart_tracked(&chart("a.000"), true)
        .await
        .unwrap();
    assert_eq!(before, Some(2), "the replaced chart's feature count");
    assert_eq!(db.feature_count("a.000").await.unwrap(), 0);
    add_feature(&db, "a.000", "L3").await;

    assert_eq!(
        db.delete_chart("a.000", Some(ChangeCause::NoaaEncCron))
            .await
            .unwrap(),
        Some(1)
    );
    assert_eq!(
        db.delete_chart("a.000", Some(ChangeCause::Admin))
            .await
            .unwrap(),
        None
    );
    let today = db.change_report_days(0, 25).await.unwrap();
    assert_eq!(
        today.page.len(),
        1,
        "one delete recorded, nothing for the miss"
    );
    let report = db
        .change_report(&today.page[0].date, 0, REPORT_ITEM_PAGE_SIZE)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(report.items.len(), 1);
    assert_eq!(report.items[0].outcome, ChangeOutcome::Delete);
    assert_eq!(report.items[0].feature_count_change, -1);
    assert_eq!(report.items[0].cause, ChangeCause::NoaaEncCron);
    assert!(!report.items[0].installed, "a.000 is gone");

    let props = json!({ "scalerank": 1 }).as_object().unwrap().clone();
    let wkb = njord_db::geometry::to_wkb(&wkt_geometry("LINESTRING(-1 0, 1 0)")).unwrap();
    for _ in 0..3 {
        db.insert_base_feature(&wkb, &props, "ne_coast.shp", 110_000_000, "COALNE")
            .await
            .unwrap();
    }
    assert_eq!(
        db.delete_base_features("ne_coast.shp", 110_000_000)
            .await
            .unwrap(),
        3
    );
    db.insert_base_feature(&wkb, &props, "ne_coast.shp", 110_000_000, "COALNE")
        .await
        .unwrap();
    db.insert_chart(&chart("b.000"), true).await.unwrap();
    add_feature(&db, "b.000", "L4").await;
    db.record_change_event(&event("b.000", ChangeOutcome::New, 1))
        .await
        .unwrap();
    let report = db
        .change_report(&today.page[0].date, 0, REPORT_ITEM_PAGE_SIZE)
        .await
        .unwrap()
        .unwrap();
    let installed: Vec<(&str, bool)> = report
        .items
        .iter()
        .map(|i| (i.name.as_str(), i.installed))
        .collect();
    assert!(
        installed.contains(&("b.000", true)) && installed.contains(&("a.000", false)),
        "installed follows the charts table, never the outcome: {installed:?}"
    );
    assert_eq!(
        db.truncate_all().await.unwrap(),
        2,
        "one feature and one base feature"
    );
    let remaining: i64 = sqlx::query_scalar("SELECT count(*) FROM change_events;")
        .fetch_one(db.pool())
        .await
        .unwrap();
    assert_eq!(remaining, 2, "truncate_all leaves the history alone");
    drop_database(db).await;
}

#[tokio::test]
async fn an_event_in_a_rolled_back_install_does_not_persist() {
    let db = migrated_db("change_rollback").await;
    db.insert_chart(&chart("r.000"), true).await.unwrap();
    let mut install = db.begin_feature_install("r.000").await.unwrap();
    let props = json!({ "LNAM": "L1" }).as_object().unwrap().clone();
    let wkb = njord_db::geometry::to_wkb(&wkt_geometry("POINT(0 0)")).unwrap();
    install.insert("BOYLAT", &props, &wkb).await.unwrap();
    install
        .record(&event("r.000", ChangeOutcome::New, 1))
        .await
        .unwrap();
    install.rollback().await.unwrap();
    assert_eq!(db.feature_count("r.000").await.unwrap(), 0);
    assert!(db.change_report_days(0, 25).await.unwrap().page.is_empty());

    let mut install = db.begin_feature_install("r.000").await.unwrap();
    install.insert("BOYLAT", &props, &wkb).await.unwrap();
    install
        .record(&event("r.000", ChangeOutcome::New, 1))
        .await
        .unwrap();
    assert_eq!(install.commit().await.unwrap(), 1);
    let days = db.change_report_days(0, 25).await.unwrap();
    assert_eq!((days.page.len(), days.page[0].actions), (1, 1));
    drop_database(db).await;
}

#[tokio::test]
async fn day_is_the_utc_date_whatever_the_session_time_zone() {
    let db = migrated_db("change_tz").await;
    // 2026-03-01 03:30 in Auckland (UTC+13) is 2026-02-28 14:30 UTC.
    exec(
        &db,
        "SET TIME ZONE 'Pacific/Auckland';
         INSERT INTO change_events (occurred_at, kind, name, outcome, feature_count_change, cause)
         VALUES ('2026-03-01T03:30:00+13:00', 'chart', 'Z.000', 'new', 5, 'admin');
         INSERT INTO change_events (occurred_at, kind, name, outcome, feature_count_change, cause)
         VALUES ('2026-02-28T23:59:59Z', 'chart', 'Y.000', 'new', 5, 'admin');
         INSERT INTO change_events (occurred_at, kind, name, outcome, feature_count_change, cause)
         VALUES ('2026-03-01T00:00:00Z', 'chart', 'X.000', 'new', 5, 'admin');",
    )
    .await;
    let days: Vec<(String, i64)> =
        sqlx::query_as("SELECT day::text, count(*) FROM change_events GROUP BY day ORDER BY day;")
            .fetch_all(db.pool())
            .await
            .unwrap();
    assert_eq!(
        days,
        [("2026-02-28".to_owned(), 2), ("2026-03-01".to_owned(), 1)]
    );
    let report = db
        .change_report("2026-02-28", 0, REPORT_ITEM_PAGE_SIZE)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(report.items.len(), 2);
    drop_database(db).await;
}

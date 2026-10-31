//! P-DB-6 / DB-11: the region export flags, ported from the Kotlin `RegionDaoTest` (no fixture
//! file). `region_needs_rebuild` compares `charts.ingested_at` with
//! `region_export_state.exported_at`; `mark_region_exported` / `clear_region_export_state` move
//! that watermark; `regions_containing_chart` is asked before a chart is deleted.

mod common;

use common::{drop_database, exec, migrated_db};
use njord_db::Db;

async fn insert_test_chart(db: &Db, name: &str, wkt: &str) {
    exec(
        db,
        &format!(
            "INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt)
             VALUES ('{name}', 1, '{name}.000', '20240101', '20240101', 1, ST_GeomFromText('{wkt}', 4326), '{{}}', '{{}}');"
        ),
    )
    .await;
}

const COVERAGE_A: &str = "POLYGON((-10 -10, -10 10, 10 10, 10 -10, -10 -10))";
const COVERAGE_B: &str = "POLYGON((20 20, 20 30, 30 30, 30 20, 20 20))";

#[tokio::test]
async fn region_needs_rebuild_reflects_per_region_ingestion_recency() {
    let db = migrated_db("regions_rebuild").await;
    assert!(
        !db.region_needs_rebuild(COVERAGE_A, "A").await.unwrap(),
        "no charts yet"
    );
    assert!(!db.region_needs_rebuild(COVERAGE_B, "B").await.unwrap());

    insert_test_chart(&db, "a", "POINT(0 0)").await;
    assert!(db.region_needs_rebuild(COVERAGE_A, "A").await.unwrap());
    assert!(!db.region_needs_rebuild(COVERAGE_B, "B").await.unwrap());

    db.mark_region_exported("A").await.unwrap();
    assert!(
        !db.region_needs_rebuild(COVERAGE_A, "A").await.unwrap(),
        "exporting clears the flag"
    );
    assert!(!db.region_needs_rebuild(COVERAGE_B, "B").await.unwrap());

    insert_test_chart(&db, "b", "POINT(25 25)").await;
    assert!(!db.region_needs_rebuild(COVERAGE_A, "A").await.unwrap());
    assert!(db.region_needs_rebuild(COVERAGE_B, "B").await.unwrap());

    db.mark_region_exported("B").await.unwrap();
    db.mark_region_exported("B").await.unwrap();
    assert!(
        !db.region_needs_rebuild(COVERAGE_B, "B").await.unwrap(),
        "re-marking upserts"
    );
    drop_database(db).await;
}

#[tokio::test]
async fn clear_region_export_state_forces_an_exported_region_stale() {
    let db = migrated_db("regions_clear").await;
    insert_test_chart(&db, "clear", "POINT(0 0)").await;
    db.mark_region_exported("clear").await.unwrap();
    assert!(!db.region_needs_rebuild(COVERAGE_A, "clear").await.unwrap());
    assert!(db.clear_region_export_state("clear").await.unwrap());
    assert!(db.region_needs_rebuild(COVERAGE_A, "clear").await.unwrap());
    assert!(
        !db.clear_region_export_state("clear").await.unwrap(),
        "nothing recorded -> false"
    );
    drop_database(db).await;
}

#[tokio::test]
async fn regions_containing_chart_lists_intersecting_regions_in_input_order() {
    let db = migrated_db("regions_containing").await;
    insert_test_chart(&db, "contained", "POLYGON((0 0, 0 2, 2 2, 2 0, 0 0))").await;
    let regions: Vec<(String, String)> = [
        ("inside", COVERAGE_A),
        ("touching", "POLYGON((2 0, 2 5, 5 5, 5 0, 2 0))"),
        ("elsewhere", COVERAGE_B),
        (
            "world",
            "POLYGON((-180 -90, 180 -90, 180 90, -180 90, -180 -90))",
        ),
    ]
    .into_iter()
    .map(|(n, w)| (n.to_owned(), w.to_owned()))
    .collect();
    assert_eq!(
        db.regions_containing_chart("contained", &regions)
            .await
            .unwrap(),
        ["inside", "touching", "world"],
        "ST_Intersects: a shared boundary counts, disjoint does not"
    );
    assert!(
        db.regions_containing_chart("unknown", &regions)
            .await
            .unwrap()
            .is_empty()
    );
    assert!(
        db.regions_containing_chart("contained", &[])
            .await
            .unwrap()
            .is_empty()
    );
    drop_database(db).await;
}

#[tokio::test]
async fn charts_in_region_and_latest_update_see_only_intersecting_charts() {
    let db = migrated_db("regions_find").await;
    insert_test_chart(&db, "r2", "POINT(1 1)").await;
    insert_test_chart(&db, "r1", "POINT(0 0)").await;
    insert_test_chart(&db, "far", "POINT(50 50)").await;
    let charts = db.find_charts_in_region(COVERAGE_A).await.unwrap();
    assert_eq!(
        charts.iter().map(|c| c.name.as_str()).collect::<Vec<_>>(),
        ["r1", "r2"],
        "by name"
    );
    assert_eq!(charts[0].file_name, "r1.000");
    assert_eq!(
        (
            charts[0].dsid_props_json.as_str(),
            charts[0].chart_txt_json.as_str()
        ),
        ("{}", "{}")
    );
    assert!(!charts[0].ingested_at.is_empty());
    assert_eq!(
        njord_db::geometry::from_wkb(&charts[0].covr_wkb).unwrap(),
        geo_types::Point::new(0.0, 0.0).into()
    );
    assert_eq!(
        db.latest_chart_update_in_region(COVERAGE_A)
            .await
            .unwrap()
            .as_deref(),
        Some("20240101")
    );
    assert_eq!(
        db.latest_chart_update_in_region("POLYGON((80 80, 80 85, 85 85, 85 80, 80 80))")
            .await
            .unwrap(),
        None,
        "MAX over nothing (Kotlin read it as \"\")"
    );
    drop_database(db).await;
}

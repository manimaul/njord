//! Queries over ingested fixture cells, on one database (`njord_rust_test`) seeded once per run
//! with US5WA22M and the two synthetic cells through [`common::ingest_cell`]:
//!
//! - `s57/US5WA22M/db_state.json`, `s57/synthetic/db_state.json`: the `charts` row and
//!   per-layer aggregates ingest leaves behind (the feature insert's MINZ/MAXZ defaults,
//!   `lnam_refs`, 2D forcing).
//! - `db/feature_queries.json`: both tile feature queries, rows compared as
//!   (chart, layer, props, assoc, OGR WKT), sorted on both sides. The WKT is printed by GDAL from
//!   the decoded geometry, so vertices are compared at OGR's 15 significant digits.
//! - `db/lnam_refs.json`: the GIN lookup for every LNAM the real cell references.

mod common;

use common::{CELLS, INNER, OUTER, WA, ingest_cell, migrated_db, ogr_wkt, tile_clip};
use njord_db::{ChartFeature, Db};
use njord_testdata::{Failures, cases, first_difference, testdata};
use serde_json::{Map, Value, json};
use sqlx::Row;
use tokio::sync::OnceCell;

const SEEDED: &str = "njord_rust_test";

static SEED: OnceCell<()> = OnceCell::const_new();

/// The seeded database, on a pool of this test's runtime.
async fn seeded() -> Db {
    SEED.get_or_init(|| async {
        common::create_database(SEEDED).await;
        let db = common::db(SEEDED);
        common::migrate(&db).await;
        for cell in CELLS {
            let installed = ingest_cell(&db, &testdata().join(cell)).await;
            assert!(installed.feature_count > 0, "{cell}");
        }
        db.pool().close().await;
    })
    .await;
    common::db(SEEDED)
}

/// The chart row and per-layer feature aggregates, in the shape of `db_state.json`.
async fn db_state(db: &Db, name: &str) -> Value {
    let row = sqlx::query(
        "SELECT name, scale, file_name, updated, issued, zoom, st_astext(covr), dsid_props::text, chart_txt::text
         FROM charts WHERE name = $1;",
    )
    .bind(name)
    .fetch_one(db.pool())
    .await
    .unwrap();
    let txt: Map<String, Value> = serde_json::from_str(&row.get::<String, _>(8)).unwrap();
    let mut txt_keys: Vec<&String> = txt.keys().collect();
    txt_keys.sort();
    let layers = sqlx::query(
        "SELECT layer, count(*), min(z_min), max(z_max), count(lnam_refs),
                count(*) FILTER (WHERE st_ndims(geom) <> 2)
         FROM features WHERE chart_name = $1 GROUP BY layer ORDER BY layer;",
    )
    .bind(name)
    .fetch_all(db.pool())
    .await
    .unwrap()
    .iter()
    .map(|r| {
        assert_eq!(
            r.get::<i64, _>(5),
            0,
            "3D geometry stored for {}",
            r.get::<String, _>(0)
        );
        json!({
            "layer": r.get::<String, _>(0),
            "count": r.get::<i64, _>(1),
            "minZ": r.get::<i32, _>(2),
            "maxZ": r.get::<i32, _>(3),
            "withLnamRefs": r.get::<i64, _>(4),
        })
    })
    .collect::<Vec<_>>();
    json!({
        "name": row.get::<String, _>(0),
        "scale": row.get::<i32, _>(1),
        "fileName": row.get::<String, _>(2),
        "updated": row.get::<String, _>(3),
        "issued": row.get::<String, _>(4),
        "zoom": row.get::<i32, _>(5),
        "covrWkt": row.get::<String, _>(6),
        "dsidProps": serde_json::from_str::<Value>(&row.get::<String, _>(7)).unwrap(),
        "chartTxtKeys": txt_keys,
        "layers": layers,
    })
}

#[tokio::test]
async fn ingest_leaves_the_fixture_db_state() {
    let db = seeded().await;
    let mut failures = Failures::default();
    let mut checked = 0;
    for fixture in ["s57/US5WA22M/db_state.json", "s57/synthetic/db_state.json"] {
        for case in cases(fixture) {
            checked += 1;
            let name = case["input"].as_str().unwrap();
            if let Some(d) = first_difference(&case["expected"], &db_state(&db, name).await, "") {
                failures.push(format!("{fixture} {name}: {d}"));
            }
        }
    }
    failures.assert_none(checked);
}

fn sorted_keys(props: &Map<String, Value>) -> Map<String, Value> {
    let mut entries: Vec<(&String, &Value)> = props.iter().collect();
    entries.sort_by(|a, b| a.0.cmp(b.0));
    entries
        .into_iter()
        .map(|(k, v)| (k.clone(), v.clone()))
        .collect()
}

fn row(chart: &str, feature: &ChartFeature) -> Value {
    let mut assoc = feature.associated_layer_names.clone();
    assoc.sort();
    json!({
        "chart": chart,
        "layer": feature.layer,
        "props": sorted_keys(&feature.props),
        "assoc": assoc,
        "wkt": feature.geometry.as_ref().map(ogr_wkt).unwrap_or_default(),
    })
}

/// Sorts rows by chart, layer, LNAM (JSON form), props (compact JSON), WKT.
fn sort_rows(rows: &mut [Value]) {
    rows.sort_by_cached_key(|r| {
        let lnam = r["props"]
            .get("LNAM")
            .map(Value::to_string)
            .unwrap_or_default();
        [
            r["chart"].to_string(),
            r["layer"].to_string(),
            lnam,
            r["props"].to_string(),
            r["wkt"].to_string(),
        ]
    });
}

#[tokio::test]
async fn feature_queries() {
    let db = seeded().await;
    let cases = cases("db/feature_queries.json");
    let mut failures = Failures::default();
    for (i, case) in cases.iter().enumerate() {
        let input = &case["input"];
        let tile = &input["tile"];
        let (z, x, y) = (
            tile["z"].as_u64().unwrap() as u32,
            tile["x"].as_i64().unwrap(),
            tile["y"].as_i64().unwrap(),
        );
        let charts: Vec<String> = serde_json::from_value(input["charts"].clone()).unwrap();
        let clip = tile_clip(z, x, y);
        let mut actual: Vec<Value> = match input["query"].as_str().unwrap() {
            "findAllChartFeaturesAsync4326" => db
                .find_all_chart_features_4326(&clip, &charts, z as i32)
                .await
                .unwrap()
                .iter()
                .flat_map(|(chart, features)| features.iter().map(|f| row(chart, f)))
                .collect(),
            "findChartFeaturesAsync4326" => db
                .find_chart_features_4326(&clip, &charts[0], z as i32)
                .await
                .unwrap()
                .iter()
                .map(|f| row(&charts[0], f))
                .collect(),
            q => panic!("{q}"),
        };
        let mut expected = case["expected"].as_array().unwrap().clone();
        sort_rows(&mut actual);
        sort_rows(&mut expected);
        if let Some(d) = first_difference(&Value::Array(expected), &Value::Array(actual), "") {
            failures.push(format!("case {i} {input}: {d}"));
        }
    }
    failures.assert_none(cases.len());
}

#[tokio::test]
async fn empty_chart_list_and_zoom_range() {
    let db = seeded().await;
    let tile = tile_clip(14, 2619, 5744);
    assert!(
        db.find_all_chart_features_4326(&tile, &[], 14)
            .await
            .unwrap()
            .is_empty()
    );
    let count = async |z: i32| {
        db.find_all_chart_features_4326(&tile, &[WA.to_owned()], z)
            .await
            .unwrap()
            .get(WA)
            .map_or(0, Vec::len)
    };
    let (z10, z14, z32, z33) = (
        count(10).await,
        count(14).await,
        count(32).await,
        count(33).await,
    );
    assert!(
        z10 < z14,
        "features with MINZ above 10 drop out: {z10} < {z14}"
    );
    assert!(
        z32 >= z14 && z33 == 0,
        "MAXZ defaults to 32 inclusive: z32={z32} z33={z33}"
    );

    let names = [INNER.to_owned(), OUTER.to_owned()];
    let both = db
        .find_all_chart_features_4326(&tile_clip(14, 2594, 5762), &names, 14)
        .await
        .unwrap();
    assert_eq!(
        both.keys().collect::<Vec<_>>(),
        [OUTER, INNER],
        "grouped in name order"
    );
}

#[tokio::test]
async fn lnam_refs() {
    let db = seeded().await;
    let referenced: Vec<String> = sqlx::query_scalar(
        "SELECT DISTINCT unnest(lnam_refs)::text FROM features WHERE chart_name = $1 ORDER BY 1;",
    )
    .bind(WA)
    .fetch_all(db.pool())
    .await
    .unwrap();
    let cases = cases("db/lnam_refs.json");
    let inputs: Vec<&str> = cases.iter().map(|c| c["input"].as_str().unwrap()).collect();
    assert_eq!(referenced, inputs, "the LNAMs US5WA22M references");
    let mut failures = Failures::default();
    for case in &cases {
        let lnam = case["input"].as_str().unwrap();
        let actual = json!(db.find_associated_layer_names(lnam).await.unwrap());
        if let Some(d) = first_difference(&case["expected"], &actual, "") {
            failures.push(format!("{lnam}: {d}"));
        }
    }
    failures.assert_none(cases.len());
}

#[tokio::test]
async fn chart_and_feature_routes_on_the_real_cell() {
    let db = seeded().await;
    let chart = db.find_chart(WA).await.unwrap().unwrap();
    assert_eq!(
        (chart.scale, chart.zoom, chart.file_name.as_str()),
        (15000, 14, WA)
    );
    assert_eq!(chart.dsid_props["DSID_UPDN"], "4", "GDAL applied .001-.004");
    assert!(
        chart.layers.contains(&"M_COVR".to_owned()) && !chart.layers.contains(&"DSID".to_owned())
    );
    assert_eq!(chart.feature_count, db.feature_count(WA).await.unwrap());
    assert_eq!(chart.chart_txt.len(), 4);
    let synthetic = db.find_chart(OUTER).await.unwrap().unwrap();
    assert_eq!(synthetic.bounds, Some(vec![-123.2, 46.9, -122.8, 47.1]));
    assert_eq!(
        synthetic.layers,
        ["BOYLAT", "DEPARE", "DEPCNT", "LIGHTS", "M_COVR"]
    );
    assert!(db.find_chart("nope.000").await.unwrap().is_none());

    let light = db.find_feature("022600019FEA0001").await.unwrap().unwrap();
    assert_eq!(
        (light.layer.as_str(), light.chart_name.as_str()),
        ("LIGHTS", WA)
    );
    assert_eq!(
        (light.zoom_min, light.zoom_max),
        (32, 13),
        "zooms swapped on the wire, as the golden records"
    );
    assert!(db.find_feature("nope").await.unwrap().is_none());

    let page = db.find_layer_positions_page("BOYLAT", 0).await.unwrap();
    assert_eq!(page.items.len(), 5);
    assert_eq!(page.last_id, page.items.last().unwrap().id);
    assert!(page.items.windows(2).all(|w| w[0].id < w[1].id));
    assert!(
        page.items
            .iter()
            .all(|i| i.geom_type == "Point" && i.zoom > 0.0)
    );
    let next = db
        .find_layer_positions_page("BOYLAT", page.last_id)
        .await
        .unwrap();
    assert!(next.items.iter().all(|i| i.id > page.last_id));
    let none = db.find_layer_positions_page("NOSUCH", 0).await.unwrap();
    assert_eq!((none.last_id, none.items.len()), (0, 0));

    let info = db
        .find_chart_info(&tile_clip(14, 2594, 5762))
        .await
        .unwrap();
    assert_eq!(
        info.iter()
            .map(|c| (c.name.as_str(), c.scale))
            .collect::<Vec<_>>(),
        [(INNER, 10000), (OUTER, 80000)]
    );
    assert!(
        info.windows(2).all(|w| w[0].scale <= w[1].scale),
        "smallest scale first"
    );

    let layer = db.geojson_layer(WA, "LIGHTS").await.unwrap();
    assert_eq!(layer.features.len(), 29);
}

#[tokio::test]
async fn region_reads_on_the_real_cell() {
    let db = seeded().await;
    let coverage = "POLYGON((-123.3 46.8, -122.3 46.8, -122.3 47.4, -123.3 47.4, -123.3 46.8))";
    let charts = db.find_charts_in_region(coverage).await.unwrap();
    assert_eq!(
        charts.iter().map(|c| c.name.as_str()).collect::<Vec<_>>(),
        [OUTER, WA, INNER]
    );
    let features = db.find_features_for_chart(WA).await.unwrap();
    assert_eq!(features.len() as i32, db.feature_count(WA).await.unwrap());
    assert!(features.windows(2).all(|w| w[0].id < w[1].id));
    assert!(features.iter().any(|f| !f.lnam_refs.is_empty()));
    assert_eq!(
        db.latest_chart_update_in_region(coverage)
            .await
            .unwrap()
            .as_deref(),
        Some("20260101")
    );
}

#[tokio::test]
async fn migrated_db_helper_is_empty() {
    let db = migrated_db("ingest_empty").await;
    assert_eq!(db.list_charts(None).await.unwrap().total_chart_count, 0);
    common::drop_database(db).await;
}

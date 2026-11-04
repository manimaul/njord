//! Chart and feature queries, each test on its own database.
//!
//! Covers overwrite and delete semantics (`db/overwrite.json`, honouring its `port.expected`), the
//! TOPMAR association on a synthetic chart, the editions key (`db/editions.json`), catalog paging
//! (`db/catalog_paging.json`), and the GeoJSON, base-feature and truncate queries.

mod common;

use common::{drop_database, migrated_db, wkt_geometry};
use geo_types::Geometry;
use geojson::GeoJson;
use njord_db::Db;
use njord_db::features::{lnam_refs, max_z, min_z};
use njord_model::{ChartInsert, FeatureInsert, IndexMap, JsonObject};
use njord_testdata::{Failures, cases, first_difference, port_expected};
use serde_json::{Value, json};

const SQUARE: &str = r#"{"type":"Polygon","coordinates":[[[-1,-1],[-1,1],[1,1],[1,-1],[-1,-1]]]}"#;

/// A chart insert with fixed dates and zoom.
fn chart(name: &str, scale: i32, geometry_json: Option<&str>) -> ChartInsert {
    ChartInsert {
        name: name.into(),
        scale,
        file_name: name.into(),
        updated: "20240101".into(),
        issued: "20240101".into(),
        zoom: 14,
        covr: geojson::Feature {
            geometry: geometry_json.map(|g| serde_json::from_str(g).unwrap()),
            properties: Some(JsonObject::new()),
            ..Default::default()
        },
        dsid_props: json!({ "DSID_UPDN": "1" }).as_object().unwrap().clone(),
        chart_txt: IndexMap::new(),
    }
}

fn props(v: Value) -> JsonObject {
    v.as_object().unwrap().clone()
}

fn wkb(wkt: &str) -> Vec<u8> {
    njord_db::geometry::to_wkb(&wkt_geometry(wkt)).unwrap()
}

/// Inserts one feature with `LNAM` and, when given, `LNAM_REFS`.
async fn add_feature(db: &Db, chart: &str, layer: &str, lnam: &str, refs: &[&str], wkt: &str) {
    let mut p = props(json!({ "LNAM": lnam }));
    if !refs.is_empty() {
        p.insert("LNAM_REFS".into(), json!(refs));
    }
    assert_eq!(
        db.insert_feature(layer, chart, &wkb(wkt), &p)
            .await
            .unwrap(),
        1
    );
}

async fn count_charts(db: &Db, name: &str) -> i64 {
    sqlx::query_scalar("SELECT count(*) FROM charts WHERE name = $1;")
        .bind(name)
        .fetch_one(db.pool())
        .await
        .unwrap()
}

#[tokio::test]
async fn overwrite_replaces_the_chart_and_deletes_its_features() {
    let db = migrated_db("charts_overwrite").await;
    db.insert_chart(&chart("a.000", 10000, Some(SQUARE)), false)
        .await
        .unwrap();
    add_feature(&db, "a.000", "BOYLAT", "L1", &[], "POINT(0 0)").await;
    add_feature(&db, "a.000", "LIGHTS", "L2", &[], "POINT(0 0)").await;
    assert_eq!(db.feature_count("a.000").await.unwrap(), 2);
    assert!(
        db.insert_chart(&chart("a.000", 1, Some(SQUARE)), false)
            .await
            .is_err(),
        "no overwrite: duplicate key"
    );

    let replaced = db
        .insert_chart(&chart("a.000", 25000, Some(SQUARE)), true)
        .await
        .unwrap();
    assert_eq!((replaced.scale, replaced.feature_count), (25000, 0));
    assert!(replaced.layers.is_empty());
    assert_eq!(replaced.bounds, Some(vec![-1.0, -1.0, 1.0, 1.0]));
    assert_eq!(db.find_chart("a.000").await.unwrap().unwrap().scale, 25000);
    assert_eq!(
        db.feature_count("a.000").await.unwrap(),
        0,
        "features of the previous edition are gone"
    );
    assert_eq!(count_charts(&db, "a.000").await, 1);
    drop_database(db).await;
}

#[tokio::test]
async fn delete_cascades_and_reports_whether_a_chart_existed() {
    let db = migrated_db("charts_delete").await;
    db.insert_chart(&chart("b.000", 10000, Some(SQUARE)), false)
        .await
        .unwrap();
    add_feature(&db, "b.000", "BOYLAT", "L3", &[], "POINT(0 0)").await;
    assert_eq!(
        db.delete_chart("b.000", None).await.unwrap(),
        Some(1),
        "its one feature"
    );
    assert!(db.find_chart("b.000").await.unwrap().is_none());
    assert_eq!(db.feature_count("b.000").await.unwrap(), 0);
    assert!(
        db.delete_chart("b.000", None).await.unwrap().is_none(),
        "unknown name -> false"
    );
    assert!(
        db.delete_chart("never-existed.000", None)
            .await
            .unwrap()
            .is_none()
    );
    drop_database(db).await;
}

/// A failing overwrite (NULL coverage violates `covr NOT NULL`) keeps the previous chart and its
/// features.
#[tokio::test]
async fn failing_overwrite_keeps_the_previous_chart() {
    let db = migrated_db("charts_d5").await;
    db.insert_chart(&chart("c.000", 10000, Some(SQUARE)), false)
        .await
        .unwrap();
    add_feature(&db, "c.000", "BOYLAT", "L4", &[], "POINT(0 0)").await;

    let result = db.insert_chart(&chart("c.000", 10000, None), true).await;
    assert_eq!(
        result.as_ref().err().and_then(|e| e.sqlstate()).as_deref(),
        Some("23502")
    );
    let actual = json!({
        "insertReturned": result.is_ok(),
        "previousChartSurvives": db.find_chart("c.000").await.unwrap().is_some(),
        "previousFeaturesSurvive": db.feature_count("c.000").await.unwrap() > 0,
    });
    let cases = cases("db/overwrite.json");
    let mut failures = Failures::default();
    for case in &cases {
        assert!(
            case.get("port").is_some(),
            "overwrite case records the expected outcome under port"
        );
        if let Some(d) = first_difference(port_expected(case), &actual, "") {
            failures.push(format!("{}: {d}", case["input"]));
        }
    }
    failures.assert_none(cases.len());
    drop_database(db).await;
}

/// A feature install that is not committed leaves nothing behind.
#[tokio::test]
async fn uncommitted_feature_install_rolls_back() {
    let db = migrated_db("charts_install").await;
    db.insert_chart(&chart("d.000", 10000, Some(SQUARE)), true)
        .await
        .unwrap();
    let mut install = db.begin_feature_install("d.000").await.unwrap();
    let p = props(json!({ "LNAM": "X" }));
    install
        .insert("BOYLAT", &p, &wkb("POINT(0 0)"))
        .await
        .unwrap();
    assert_eq!(install.inserted(), 1);
    assert_eq!(
        db.feature_count("d.000").await.unwrap(),
        0,
        "not visible before commit"
    );
    drop(install);
    assert_eq!(db.feature_count("d.000").await.unwrap(), 0);

    let mut install = db.begin_feature_install("d.000").await.unwrap();
    install
        .insert("BOYLAT", &p, &wkb("POINT(0 0)"))
        .await
        .unwrap();
    assert!(install.insert("BOYLAT", &p, b"not wkb").await.is_err());
    assert!(
        matches!(install.commit().await, Err(njord_db::Error::Aborted(_))),
        "a failed statement aborts the transaction"
    );
    assert_eq!(db.feature_count("d.000").await.unwrap(), 0);

    let mut install = db.begin_feature_install("d.000").await.unwrap();
    // OGR's 2.5D WKB, as ingest sends it.
    let z = gdal::vector::Geometry::from_wkt("POINT Z (0 0 5)")
        .unwrap()
        .wkb()
        .unwrap();
    install.insert("BOYLAT", &p, &z).await.unwrap();
    assert_eq!(install.commit().await.unwrap(), 1);
    let dims: i32 =
        sqlx::query_scalar("SELECT st_ndims(geom)::int FROM features WHERE chart_name = 'd.000';")
            .fetch_one(db.pool())
            .await
            .unwrap();
    assert_eq!(dims, 2, "geometry forced 2D");
    drop_database(db).await;
}

/// Three topmarks, two referenced through `LNAM_REFS` by a buoy and a beacon (and a
/// light). Both lookups agree, other layers are untouched, and the layer page adds
/// `_PTFM`/`_ASOC`.
#[tokio::test]
async fn topmar_association() {
    let db = migrated_db("charts_topmar").await;
    let name = "topmar.000";
    db.insert_chart(&chart(name, 10000, Some(SQUARE)), true)
        .await
        .unwrap();
    add_feature(&db, name, "BOYLAT", "B1", &["T1"], "POINT(0.1 0.1)").await;
    add_feature(&db, name, "BCNLAT", "N1", &["T2", "T1"], "POINT(0.2 0.2)").await;
    add_feature(&db, name, "LIGHTS", "L1", &["T1"], "POINT(0.1 0.1)").await;
    add_feature(&db, name, "TOPMAR", "T1", &[], "POINT(0.1 0.1)").await;
    add_feature(&db, name, "TOPMAR", "T2", &[], "POINT(0.2 0.2)").await;
    add_feature(&db, name, "TOPMAR", "T3", &[], "POINT(0.3 0.3)").await;

    assert_eq!(
        db.find_associated_layer_names("T1").await.unwrap(),
        ["BCNLAT", "BOYLAT", "LIGHTS"]
    );
    assert_eq!(
        db.find_associated_layer_names("T2").await.unwrap(),
        ["BCNLAT"]
    );
    assert!(
        db.find_associated_layer_names("T3")
            .await
            .unwrap()
            .is_empty()
    );

    let include = wkt_geometry("POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))");
    let one = db
        .find_chart_features_4326(&include, name, 14)
        .await
        .unwrap();
    let all = db
        .find_all_chart_features_4326(&include, &[name.to_owned()], 14)
        .await
        .unwrap()
        .swap_remove(name)
        .unwrap();
    for features in [one, all] {
        let by_lnam = |l: &str| {
            let f = features.iter().find(|f| f.props["LNAM"] == l).unwrap();
            (f.layer.clone(), f.associated_layer_names.clone())
        };
        assert_eq!(
            by_lnam("T1"),
            (
                "TOPMAR".into(),
                vec!["BCNLAT".into(), "BOYLAT".into(), "LIGHTS".into()]
            )
        );
        assert_eq!(by_lnam("T2"), ("TOPMAR".into(), vec!["BCNLAT".into()]));
        assert_eq!(by_lnam("T3"), ("TOPMAR".into(), vec![]));
        assert!(
            features
                .iter()
                .filter(|f| f.layer != "TOPMAR")
                .all(|f| f.associated_layer_names.is_empty())
        );
    }

    let page = db.find_layer_positions_page("TOPMAR", 0).await.unwrap();
    let item = |l: &str| page.items.iter().find(|i| i.props["LNAM"] == l).unwrap();
    assert_eq!(item("T1").props["_PTFM"], "FLOATING");
    assert_eq!(
        item("T1").props["_ASOC"],
        json!(["BCNLAT", "BOYLAT", "LIGHTS"])
    );
    assert_eq!(item("T3").props["_PTFM"], "RIGID");
    assert_eq!(item("T3").props["_ASOC"], json!([]));
    assert_eq!((item("T2").lng, item("T2").lat), (0.2, 0.2));
    drop_database(db).await;
}

#[tokio::test]
async fn editions_key() {
    let db = migrated_db("charts_editions").await;
    let cases = cases("db/editions.json");
    let mut failures = Failures::default();
    for (i, case) in cases.iter().enumerate() {
        let input = &case["input"];
        let name = format!("ed_{i}.000");
        let insert = ChartInsert {
            dsid_props: props(input["dsidProps"].clone()),
            updated: input["updated"].as_str().unwrap().into(),
            issued: input["issued"].as_str().unwrap().into(),
            ..chart(&name, 10000, Some(SQUARE))
        };
        db.insert_chart(&insert, true).await.unwrap();
        let editions = db.chart_editions().await.unwrap().editions;
        let actual = editions.get(&name).map_or(Value::Null, |k| json!(k));
        if let Some(d) = first_difference(&case["expected"], &actual, "") {
            failures.push(format!("case {i} {input}: {d}"));
        }
    }
    failures.assert_none(cases.len());
    drop_database(db).await;
}

#[tokio::test]
async fn catalog_paging() {
    let db = migrated_db("charts_paging").await;
    let cases = cases("db/catalog_paging.json");
    let total = cases[0]["input"]["chartsBefore"].as_i64().unwrap();
    let values: Vec<String> = (1..=total)
        .map(|i| {
            let n = format!("zz_f2_chartdao_{i:04}.000");
            format!("('{n}', 1, '{n}', '20240101', '20240101', 1, ST_GeomFromText('POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))', 4326), '{{}}', '{{}}')")
        })
        .collect();
    common::exec(
        &db,
        &format!(
            "INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) VALUES {};",
            values.join(",\n")
        ),
    )
    .await;
    let mut failures = Failures::default();
    for case in &cases {
        let cursor = case["input"]["cursor"].as_str();
        let page = db.list_charts(cursor).await.unwrap();
        assert_eq!(i64::from(page.total_chart_count), total);
        let mut actual = json!({
            "pageSize": page.page.len(),
            "first": page.page.first().map(|c| c.name.clone()),
            "last": page.page.last().map(|c| c.name.clone()),
            "nextName": page.next_name,
        });
        njord_testdata::strip_nulls(&mut actual);
        if let Some(d) = first_difference(&case["expected"], &actual, "") {
            failures.push(format!("{}: {d}", case["input"]));
        }
    }
    failures.assert_none(cases.len());
    let first = db.list_charts(None).await.unwrap();
    assert_eq!(
        first.page[0].name, "zz_f2_chartdao_0001.000",
        "no cursor starts at the beginning"
    );
    drop_database(db).await;
}

/// The feature insert's prop mapping.
#[test]
fn insert_mapping_of_minz_maxz_and_lnam_refs() {
    let p = |v: Value| props(v);
    assert_eq!((min_z(&p(json!({}))), max_z(&p(json!({})))), (0, 32));
    assert_eq!(
        (
            min_z(&p(json!({ "MINZ": 13 }))),
            max_z(&p(json!({ "MAXZ": 18 })))
        ),
        (13, 18)
    );
    assert_eq!(
        min_z(&p(json!({ "MINZ": "12" }))),
        12,
        "intOrNull reads a numeric string"
    );
    assert_eq!(min_z(&p(json!({ "MINZ": 12.5 }))), 0);
    assert_eq!(
        min_z(&p(json!({ "MINZ": 13.0 }))),
        0,
        "\"13.0\".toIntOrNull() is null"
    );
    assert_eq!(max_z(&p(json!({ "MAXZ": null }))), 32);
    assert_eq!(max_z(&p(json!({ "MAXZ": 4_000_000_000_u64 }))), 32);

    assert!(lnam_refs(&p(json!({}))).is_empty());
    assert!(
        lnam_refs(&p(json!({ "LNAM_REFS": "A" }))).is_empty(),
        "not an array"
    );
    assert_eq!(
        lnam_refs(&p(json!({ "LNAM_REFS": ["A", "B"] }))),
        ["A", "B"]
    );
    assert_eq!(
        lnam_refs(&p(
            json!({ "LNAM_REFS": ["A", 7, null, true, ["x"], { "y": 1 }] })
        )),
        ["A", "7", "null", "true"],
        "JsonPrimitive.content of each scalar; arrays and objects dropped"
    );
}

/// `POST /v1/geojson` then `GET /v1/geojson` for each GeoJSON kind.
#[tokio::test]
async fn geojson_insert_and_fetch() {
    let db = migrated_db("charts_geojson").await;
    let chart = db
        .insert_chart(&chart("g.000", 10000, Some(SQUARE)), true)
        .await
        .unwrap();
    let insert = |geo: Value| FeatureInsert {
        layer_name: "LNDARE".into(),
        chart: chart.clone(),
        geo: serde_json::from_value::<GeoJson>(geo).unwrap(),
    };
    let geometry = json!({ "type": "Point", "coordinates": [0.5, 0.5, 9.0] });
    let feature = json!({ "type": "Feature", "geometry": { "type": "LineString", "coordinates": [[0, 0], [1, 1]] },
                          "properties": { "LNAM": "G1", "MINZ": 10, "LNAM_REFS": ["G2"] } });
    let no_geometry =
        json!({ "type": "Feature", "geometry": null, "properties": { "LNAM": "G9" } });
    let bowtie = json!({ "type": "Feature", "properties": { "LNAM": "G3" },
                         "geometry": { "type": "Polygon", "coordinates": [[[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]]] } });
    assert_eq!(db.geojson_insert(&insert(geometry)).await.unwrap(), 1);
    assert_eq!(
        db.geojson_insert(&insert(feature.clone())).await.unwrap(),
        1
    );
    assert_eq!(
        db.geojson_insert(&insert(no_geometry.clone()))
            .await
            .unwrap(),
        0,
        "C_AGGR-like: skipped"
    );
    let fc = json!({ "type": "FeatureCollection", "features": [feature, no_geometry, bowtie] });
    assert_eq!(db.geojson_insert(&insert(fc)).await.unwrap(), 2);

    let layer = db.geojson_layer("g.000", "LNDARE").await.unwrap();
    assert_eq!(layer.features.len(), 4);
    let first = &layer.features[0];
    let geometry_json =
        |f: &geojson::Feature| serde_json::to_value(f.geometry.as_ref().unwrap()).unwrap();
    assert_eq!(
        geometry_json(first),
        json!({ "type": "Point", "coordinates": [0.5, 0.5] }),
        "2D"
    );
    assert_eq!(first.foreign_members.as_ref().unwrap()["layer"], "LNDARE");
    assert!(matches!(first.id, Some(geojson::feature::Id::Number(_))));
    let made_valid = &layer.features[3];
    assert_eq!(
        geometry_json(made_valid)["type"],
        "MultiPolygon",
        "ST_MakeValid"
    );
    let rows: Vec<(i32, i32, Vec<String>)> = sqlx::query_as(
        "SELECT z_min, z_max, lnam_refs::text[] FROM features WHERE props->>'LNAM' = 'G1' ORDER BY id;",
    )
    .fetch_all(db.pool())
    .await
    .unwrap();
    assert_eq!(rows, vec![(10, 32, vec!["G2".to_owned()]); 2]);
    assert!(
        db.geojson_layer("g.000", "NOSUCH")
            .await
            .unwrap()
            .features
            .is_empty()
    );
    drop_database(db).await;
}

/// Base features by name and scale, and truncation keeping the schema version.
#[tokio::test]
async fn base_features_and_truncate() {
    let db = migrated_db("charts_base").await;
    let p = props(json!({ "featurecla": "Coastline" }));
    for (wkt, scale) in [
        ("LINESTRING(-2 0, 2 0)", 110_000_000),
        ("LINESTRING(-2 0.5, 2 0.5)", 50_000_000),
    ] {
        db.insert_base_feature(&wkb(wkt), &p, "ne_coast.shp", scale, "COALNE")
            .await
            .unwrap();
    }
    let include: Geometry = wkt_geometry("POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))");
    let found = db.find_base_features(110_000_000, &include).await.unwrap();
    assert_eq!(found.len(), 1);
    assert_eq!(found[0].layer, "COALNE");
    assert_eq!(
        found[0].geometry,
        Some(wkt_geometry("LINESTRING(-1 0, 1 0)")),
        "intersected with the mask"
    );
    assert!(
        db.find_base_features(10_000_000, &include)
            .await
            .unwrap()
            .is_empty()
    );
    assert!(
        db.delete_base_features("ne_coast.shp", 110_000_000)
            .await
            .unwrap()
            > 0
    );
    assert_eq!(
        db.delete_base_features("ne_coast.shp", 110_000_000)
            .await
            .unwrap(),
        0
    );

    db.insert_chart(&chart("t.000", 10000, Some(SQUARE)), true)
        .await
        .unwrap();
    add_feature(&db, "t.000", "BOYLAT", "L1", &[], "POINT(0 0)").await;
    assert_eq!(
        db.truncate_all().await.unwrap(),
        2,
        "the chart's feature and the 1:50m base feature"
    );
    assert_eq!(db.list_charts(None).await.unwrap().total_chart_count, 0);
    assert!(
        db.find_base_features(50_000_000, &include)
            .await
            .unwrap()
            .is_empty()
    );
    assert_eq!(
        db.db_version().await.unwrap(),
        njord_db::CURRENT_VERSION,
        "meta_data kept"
    );
    drop_database(db).await;
}

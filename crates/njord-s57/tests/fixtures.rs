//! The GDAL read contract (RUST_PORT_PLAN.md §5.3) against the fixtures `S57FixtureTest` and
//! `NaturalEarthFixtureTest` wrote: P-IN-1…4 and P-IN-6.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use njord_s57::{
    EXCLUDED_LAYERS, Feature, GdalCell, Geometry, S57Reader, Shapefile, normalize_layer_name,
    scale_from_filename,
};
use njord_testdata::{
    Failures, cases, first_difference, json_files, port_expected, strip_nulls, testdata,
};
use serde_json::{Value, json};

fn open(fixture: &str) -> GdalCell {
    GdalCell::open(testdata().join(fixture)).unwrap_or_else(|e| panic!("{fixture}: {e}"))
}

fn compare(expected: &Value, actual: &Value) -> Result<(), String> {
    first_difference(expected, actual, "").map_or(Ok(()), Err)
}

/// `FeatureOut`: props plus the WKT after `makeValid()`, `wkt` left out when there is none.
fn feature_out(f: Feature) -> Value {
    let mut out = json!({ "props": f.props });
    if let Some(wkt) = f.geometry.and_then(|g| g.make_valid()).map(|g| g.wkt()) {
        out["wkt"] = Value::String(wkt);
    }
    out
}

fn inserted_features(cell: &GdalCell, layer: &str) -> Vec<Value> {
    cell.features(layer)
        .unwrap_or_else(|| panic!("no layer {layer}"))
        .into_iter()
        .map(feature_out)
        .collect()
}

/// P-IN-1: `chartInsertInfo()` for every fixture cell.
#[test]
fn p_in_1_chart_insert() {
    let mut failures = Failures::default();
    let cases = cases("s57/chart_insert.json");
    for case in &cases {
        let cell = open(case["input"].as_str().unwrap());
        failures.check(
            &case["input"],
            cell.chart_insert()
                .map_err(|e| e.to_string())
                .and_then(|insert| {
                    let mut actual = serde_json::to_value(insert).unwrap();
                    strip_nulls(&mut actual);
                    compare(port_expected(case), &actual)
                }),
        );
    }
    failures.assert_none(cases.len());
}

/// P-IN-2: every inserted feature of every layer, props and WKT after `make_valid`, per cell.
#[test]
fn p_in_2_features() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for (cell, dir) in [
        ("fixtures/US5WA22M/US5WA22M.000", "s57/US5WA22M/features"),
        (
            "fixtures/synthetic/US4ZZ01M.000",
            "s57/synthetic/US4ZZ01M/features",
        ),
        (
            "fixtures/synthetic/US5ZZ02M.000",
            "s57/synthetic/US5ZZ02M/features",
        ),
    ] {
        let reader = open(cell);
        let files: Vec<String> = json_files(&testdata().join(dir))
            .iter()
            .map(|p| p.file_stem().unwrap().to_string_lossy().into_owned())
            .collect();
        let mut layers = reader.inserted_layers();
        layers.sort();
        if layers != files {
            failures.push(format!(
                "{cell}: layers {layers:?} != fixture files {files:?}"
            ));
        }
        for layer in &files {
            let features = inserted_features(&reader, layer);
            let cases = cases(&format!("{dir}/{layer}.json"));
            if features.len() != cases.len() {
                failures.push(format!(
                    "{cell} {layer}: {} features, fixture has {}",
                    features.len(),
                    cases.len()
                ));
            }
            for (case, actual) in cases.iter().zip(&features) {
                checked += 1;
                failures.check(
                    format!("{cell} {layer} #{}", case["input"]),
                    compare(port_expected(case), actual),
                );
            }
        }
    }
    failures.assert_none(checked);
}

/// P-IN-3: layer order, per-layer counts and `featureCount(exLayers)`.
#[test]
fn p_in_3_layer_counts() {
    let mut failures = Failures::default();
    let cases = cases("s57/layer_counts.json");
    for case in &cases {
        let cell = open(case["input"].as_str().unwrap());
        let layers: serde_json::Map<String, Value> = cell
            .layer_names()
            .into_iter()
            .map(|l| {
                let count = cell.feature_count(&l).unwrap();
                (l, Value::from(count))
            })
            .collect();
        let actual = json!({
            "excluded": EXCLUDED_LAYERS,
            "total": cell.total_feature_count(),
            "layers": layers,
        });
        failures.check(&case["input"], compare(port_expected(case), &actual));
    }
    failures.assert_none(cases.len());
}

/// A fresh directory under the system temp dir, removed on drop.
struct TempDir(PathBuf);

impl TempDir {
    fn new(name: &str) -> TempDir {
        let dir = std::env::temp_dir().join(format!("njord-s57-{name}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        TempDir(dir)
    }
}

impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

fn dsid_state(cell: &GdalCell) -> Value {
    let info = cell.chart_insert().unwrap();
    let prop = |k: &str| match info.dsid_props.get(k) {
        Some(Value::String(s)) => Value::String(s.clone()),
        Some(v) => Value::String(v.to_string()),
        None => Value::Null,
    };
    let mut txt: Vec<&String> = info.chart_txt.keys().collect();
    txt.sort();
    json!({
        "name": info.name,
        "updated": info.updated,
        "issued": info.issued,
        "dsidUpdn": prop("DSID_UPDN"),
        "dsidEdtn": prop("DSID_EDTN"),
        "dsidUadt": prop("DSID_UADT"),
        "dsidIsdt": prop("DSID_ISDT"),
        "featureCount": cell.total_feature_count(),
        "chartTxt": txt,
    })
}

/// `(layer, LNAM)` → its features sorted by WKT: SPLIT_MULTIPOINT gives several soundings one LNAM.
fn by_lnam(cell: &GdalCell) -> BTreeMap<(String, String), Vec<Value>> {
    let mut out: BTreeMap<(String, String), Vec<Value>> = BTreeMap::new();
    for layer in cell.inserted_layers() {
        for f in inserted_features(cell, &layer) {
            if let Some(Value::String(lnam)) = f["props"].get("LNAM") {
                out.entry((layer.clone(), lnam.clone()))
                    .or_default()
                    .push(f);
            }
        }
    }
    let wkt = |f: &Value| {
        f.get("wkt")
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_string()
    };
    for features in out.values_mut() {
        features.sort_by_key(wkt);
    }
    out
}

fn change(key: &(String, String), before: &[Value], after: &[Value]) -> Value {
    json!({ "layer": key.0, "lnam": key.1, "before": before, "after": after })
}

/// P-IN-4: the lone `.000` (copied to a temp dir) against the cell with `.001`–`.004` applied.
#[test]
fn p_in_4_update_cell() {
    let tmp = TempDir::new("update_cell");
    let base_path = tmp.0.join("US5WA22M.000");
    std::fs::copy(
        testdata().join("fixtures/US5WA22M/US5WA22M.000"),
        &base_path,
    )
    .unwrap();
    let base = GdalCell::open(&base_path).unwrap();
    let updated = open("fixtures/US5WA22M/US5WA22M.000");

    let mut failures = Failures::default();
    let dsid = cases("s57/update_cell/dsid.json");
    for case in &dsid {
        let cell = if case["input"] == "base" {
            &base
        } else {
            &updated
        };
        failures.check(
            &case["input"],
            compare(port_expected(case), &dsid_state(cell)),
        );
    }

    let (before, after) = (by_lnam(&base), by_lnam(&updated));
    let none: &[Value] = &[];
    let actual = |kind: &str| -> Value {
        let list: Vec<Value> = match kind {
            "added" => after
                .iter()
                .filter(|(k, _)| !before.contains_key(*k))
                .map(|(k, a)| change(k, none, a))
                .collect(),
            "removed" => before
                .iter()
                .filter(|(k, _)| !after.contains_key(*k))
                .map(|(k, b)| change(k, b, none))
                .collect(),
            _ => before
                .iter()
                .filter_map(|(k, b)| after.get(k).filter(|a| *a != b).map(|a| change(k, b, a)))
                .collect(),
        };
        Value::Array(list)
    };
    let changed = cases("s57/update_cell/changed_features.json");
    for case in &changed {
        let kind = case["input"].as_str().unwrap();
        failures.check(kind, compare(port_expected(case), &actual(kind)));
    }
    failures.assert_none(dsid.len() + changed.len());
}

/// P-IN-6 (shapefile half): layers, feature count and the first three features of the NE extract.
#[test]
fn p_in_6_natural_earth() {
    let mut failures = Failures::default();
    let cases = cases("s57/natural_earth.json");
    for case in &cases {
        let path: &Path = &testdata().join(case["input"].as_str().unwrap());
        let shp = Shapefile::open(path).unwrap();
        let first: serde_json::Map<String, Value> = shp
            .layer_names()
            .into_iter()
            .map(|l| {
                let features = shp.features(&l).unwrap();
                let out = features.into_iter().take(3).map(feature_out).collect();
                (l, Value::Array(out))
            })
            .collect();
        let actual = json!({
            "layers": shp.layer_names(),
            "featureCount": shp.feature_count(),
            "firstFeatures": first,
        });
        failures.check(&case["input"], compare(port_expected(case), &actual));
    }
    failures.assert_none(cases.len());
}

/// P-IN-6 (name half): `normalizeLayerName` and `scaleFromFilename`.
#[test]
fn p_in_6_natural_earth_names() {
    let mut failures = Failures::default();
    let cases = cases("s57/natural_earth_names.json");
    for case in &cases {
        let name = case["input"].as_str().unwrap();
        let actual = json!({
            "layer": normalize_layer_name(name),
            "scale": scale_from_filename(name),
        });
        failures.check(name, compare(port_expected(case), &actual));
    }
    failures.assert_none(cases.len());
}

/// A layer name and its features' props and WKT.
type FakeLayer = (&'static str, Vec<(Value, Option<&'static str>)>);

/// A reader over in-memory features, for the `chart_insert` rules no fixture cell exercises.
struct FakeCell {
    path: PathBuf,
    layers: Vec<FakeLayer>,
}

impl S57Reader for FakeCell {
    fn path(&self) -> &Path {
        &self.path
    }

    fn layer_names(&self) -> Vec<String> {
        self.layers.iter().map(|(l, _)| l.to_string()).collect()
    }

    fn feature_count(&self, layer: &str) -> Option<u64> {
        self.features(layer).map(|f| f.len() as u64)
    }

    fn features(&self, layer: &str) -> Option<Vec<Feature>> {
        let (_, features) = self.layers.iter().find(|(l, _)| *l == layer)?;
        Some(
            features
                .iter()
                .map(|(props, wkt)| Feature {
                    props: props.as_object().unwrap().clone(),
                    geometry: wkt.map(|w| Geometry::from_wkt(w).unwrap()),
                })
                .collect(),
        )
    }
}

/// D7: a missing `DSID_DSNM` names the chart after its file, and several `CATCOV=1` coverages
/// (a `MultiPolygon` among them) become one `MultiPolygon` of all their polygons. Also the
/// ISO 8859-1 `.TXT` decoding (`DSSI_NALL = 1`) and that `CATCOV=2` is not coverage.
#[test]
fn chart_insert_d7_and_charset() {
    let tmp = TempDir::new("d7");
    std::fs::write(tmp.0.join("US5ZZ09A.txt"), b"NOAA ENC\xae").unwrap();
    std::fs::write(tmp.0.join("notes.md"), b"ignored").unwrap();
    let cell = FakeCell {
        path: tmp.0.join("US5ZZ09M.000"),
        layers: vec![
            (
                "DSID",
                vec![(
                    json!({ "DSID_UADT": "20260101", "DSSI_NALL": 1, "DSPM_CSCL": 20000 }),
                    None,
                )],
            ),
            (
                "M_COVR",
                vec![
                    (
                        json!({ "CATCOV": 1 }),
                        Some("POLYGON ((0 40,1 40,1 41,0 41,0 40))"),
                    ),
                    (
                        json!({ "CATCOV": 1 }),
                        Some(
                            "MULTIPOLYGON (((2 42,3 42,3 43,2 43,2 42)),((4 44,5 44,5 45,4 45,4 44)))",
                        ),
                    ),
                    (
                        json!({ "CATCOV": 2 }),
                        Some("POLYGON ((9 9,10 9,10 10,9 10,9 9))"),
                    ),
                ],
            ),
        ],
    };
    let insert = cell.chart_insert().unwrap();
    let mut failures = Failures::default();
    failures.check("name", compare(&json!("US5ZZ09M.000"), &json!(insert.name)));
    failures.check("issued", compare(&json!(""), &json!(insert.issued)));
    failures.check(
        "covr",
        compare(
            &json!({ "type": "MultiPolygon", "coordinates": [
                [[[0.0, 40.0], [1.0, 40.0], [1.0, 41.0], [0.0, 41.0], [0.0, 40.0]]],
                [[[2.0, 42.0], [3.0, 42.0], [3.0, 43.0], [2.0, 43.0], [2.0, 42.0]]],
                [[[4.0, 44.0], [5.0, 44.0], [5.0, 45.0], [4.0, 45.0], [4.0, 44.0]]],
            ] }),
            &serde_json::to_value(insert.covr.geometry).unwrap(),
        ),
    );
    failures.check(
        "chart_txt",
        compare(
            &json!({ "US5ZZ09A.txt": "NOAA ENC\u{ae}" }),
            &serde_json::to_value(&insert.chart_txt).unwrap(),
        ),
    );
    // 1:20000 at the coverage centre latitude, (40 + 45) / 2.
    failures.check("zoom", compare(&json!(13), &json!(insert.zoom)));
    failures.assert_none(5);
}

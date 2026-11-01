//! The pure decisions of tile assembly against the golden fixtures `geo/filtered_props.json` and
//! `geo/base_map_scale.json`.

use njord_geo::{filter_props, find_base_map_scale};
use njord_model::{IndexMap, JsonObject, S57Attribute};
use njord_testdata::{Failures, cases, first_difference, port_expected, repo_root};
use serde_json::{Value, json};

/// The S-57 attribute catalogue the server loads.
fn s57_attributes() -> IndexMap<String, S57Attribute> {
    let path = repo_root().join("crates/njord-server/resources/s57attributes.json");
    serde_json::from_value(njord_testdata::load(&path)).unwrap()
}

/// Blank strings, empty arrays and `"0"` on enumerated attributes are dropped; a nested object is
/// an error, which the fixture records as `{"throws": "IllegalStateException"}`.
#[test]
fn filtered_props() {
    let attributes = s57_attributes();
    let is_enumerated = |key: &str| attributes.get(key).is_some_and(|a| a.attribute_type == "E");
    let mut failures = Failures::default();
    let cases = cases("geo/filtered_props.json");
    for case in &cases {
        let mut props: JsonObject = serde_json::from_value(case["input"].clone()).unwrap();
        let actual = match filter_props(&mut props, is_enumerated) {
            Ok(()) => Value::Object(props),
            Err(_) => json!({ "throws": "IllegalStateException" }),
        };
        let result = first_difference(port_expected(case), &actual, "").map_or(Ok(()), Err);
        failures.check(&case["input"], result);
    }
    failures.assert_none(cases.len());
}

/// Natural Earth 110m below z3, 50m below z5, 10m from z5 up.
#[test]
fn base_map_scale() {
    let mut failures = Failures::default();
    let cases = cases("geo/base_map_scale.json");
    for case in &cases {
        let z = case["input"].as_u64().unwrap() as u32;
        let actual = json!(find_base_map_scale(z));
        let result = first_difference(port_expected(case), &actual, "").map_or(Ok(()), Err);
        failures.check(z, result);
    }
    failures.assert_none(cases.len());
}

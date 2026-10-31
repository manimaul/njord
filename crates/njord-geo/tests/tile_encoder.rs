//! P-TE-3 and P-TE-4: the pure decisions of the Kotlin `TileEncoder`.

use njord_geo::{filter_props, find_base_map_scale};
use njord_model::{IndexMap, JsonObject, S57Attribute};
use njord_testdata::{Failures, cases, first_difference, port_expected, repo_root};
use serde_json::{Value, json};

/// The attribute catalogue the Kotlin server loads (`Singletons.s57ObjectLibrary`).
fn s57_attributes() -> IndexMap<String, S57Attribute> {
    let path = repo_root().join("server/src/nativeMain/resources/s57attributes.json");
    serde_json::from_value(njord_testdata::load(&path)).unwrap()
}

/// P-TE-3: blank strings, empty arrays and `"0"` on enumerated attributes are dropped; a nested
/// object is an error (Kotlin throws `IllegalStateException`).
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

/// P-TE-4: Natural Earth 110m below z3, 50m below z5, 10m from z5 up.
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

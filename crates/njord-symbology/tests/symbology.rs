//! The symbology golden fixtures under `testdata/symbology/`, each case checked and every failure
//! reported together. (The sprite fixtures are checked in `njord-model`'s `tests/testdata.rs`.)
//!
//! To check only some layers in [`pre_tile_encode`] and [`pre_tile_encode_cell`], set `NJORD_LAYER` to their keys
//! (comma-separated):
//!
//! ```text
//! NJORD_LAYER=BOYLAT cargo test -p njord-symbology -- --nocapture
//! ```

use std::collections::BTreeSet;

use njord_model::{Colors, CustomTheme, Depth, Theme, ThemeMode};
use njord_symbology::layers::attributehelpers::*;
use njord_symbology::layers::light_description::{self, LightDescription};
use njord_symbology::layers::sbdare;
use njord_symbology::{
    ChartFeature, ColorLibrary, Colour, Context, GeometryType, LayerFactory, Layerable,
    SymbologyConfig, add_sounding_conversions, build_sector_svg, style,
};
use njord_testdata::{
    Failures, cases, first_difference, json_files, load, port_expected, testdata,
};
use serde_json::{Value, json};

fn colors() -> ColorLibrary {
    let path = njord_testdata::repo_root().join("crates/njord-server/resources/colors.json");
    let colors: Colors = serde_json::from_value(load(&path)).expect("colors.json");
    ColorLibrary::new(colors)
}

/// The factory the goldens were recorded with: shipped thresholds (3 / 6 / 9 m) and `colors.json`.
fn factory() -> LayerFactory {
    LayerFactory::new(SymbologyConfig::default(), colors())
}

/// `NJORD_LAYER`, when set: only these keys are checked.
fn selected(key: &str) -> bool {
    match std::env::var("NJORD_LAYER") {
        Ok(keys) if !keys.is_empty() => keys.split(',').any(|k| k.trim() == key),
        _ => true,
    }
}

/// A fixture input (`{props, geometry_type, assoc}`) as the feature of `layer`.
fn feature(layer: &str, input: &Value) -> ChartFeature {
    ChartFeature {
        layer: layer.to_owned(),
        geometry_type: input["geometry_type"]
            .as_str()
            .and_then(GeometryType::from_fixture_name),
        props: input["props"].as_object().cloned().unwrap_or_default(),
        associated_layer_names: input
            .get("assoc")
            .and_then(Value::as_array)
            .map(|a| a.iter().map(|s| s.as_str().unwrap().to_owned()).collect())
            .unwrap_or_default(),
    }
}

/// [`first_difference`] of a case's props against the expected output.
fn props_difference(case: &Value, props: njord_model::JsonObject) -> Result<(), String> {
    diff(port_expected(case), &Value::Object(props))
}

/// Runs one [`Layerable::pre_tile_encode`] case and compares the props, key order included.
fn check_encode(
    layerable: &dyn Layerable,
    cx: &Context,
    key: &str,
    case: &Value,
) -> Result<(), String> {
    let mut f = feature(key, &case["input"]);
    layerable.pre_tile_encode(&mut f, cx);
    props_difference(case, f.props)
}

/// Every `symbology/pre_tile_encode/<KEY>.json` (non-uppercase keys under `custom_key/`).
///
/// The goldens only hold a file for a layerable whose output ever differs from its input, so a
/// layerable without one must leave a minimal feature of every geometry type unchanged.
#[test]
fn pre_tile_encode() {
    let factory = factory();
    let mut failures = Failures::default();
    let mut checked = 0;
    let dir = testdata().join("symbology/pre_tile_encode");
    let mut files = json_files(&dir);
    files.extend(json_files(&dir.join("custom_key")));
    let mut with_fixture = BTreeSet::new();
    for path in files {
        let key = path.file_stem().unwrap().to_string_lossy().into_owned();
        with_fixture.insert(key.clone());
        if !selected(&key) {
            continue;
        }
        let Some(entry) = factory.get(&key) else {
            failures.push(format!("{key}: fixture for an unregistered layerable"));
            continue;
        };
        let cases = load(&path)["cases"].as_array().unwrap().clone();
        for (i, case) in cases.iter().enumerate() {
            checked += 1;
            failures.check(
                format!("{key}[{i}] {}", case["input"]),
                check_encode(entry.layerable.as_ref(), &factory.cx, &key, case),
            );
        }
    }
    for entry in factory.entries() {
        if with_fixture.contains(&entry.key) || !selected(&entry.key) {
            continue;
        }
        for geometry in ["POINT", "LINESTRING", "POLYGON"] {
            checked += 1;
            let case = json!({
                "input": {"props": {}, "geometry_type": geometry, "assoc": []},
                "expected": {},
            });
            failures.check(
                format!("{} (no fixture: identity) {geometry}", entry.key),
                check_encode(entry.layerable.as_ref(), &factory.cx, &entry.key, &case),
            );
        }
    }
    println!("pre_tile_encode: {checked} cases checked");
    failures.assert_none(checked);
}

/// Every real feature of US5WA22M, through the factory's dispatch by layer.
///
/// The goldens were recorded without TOPMAR associations, so none are passed. A layer with no registered layerable passes through
/// unchanged.
#[test]
fn pre_tile_encode_cell() {
    let factory = factory();
    let mut failures = Failures::default();
    let mut checked = 0;
    for path in json_files(&testdata().join("symbology/pre_tile_encode_cell")) {
        let layer = path.file_stem().unwrap().to_string_lossy().into_owned();
        if !selected(&layer) {
            continue;
        }
        let cases = load(&path)["cases"].as_array().unwrap().clone();
        for (i, case) in cases.iter().enumerate() {
            checked += 1;
            let mut f = feature(&layer, &case["input"]);
            f.associated_layer_names.clear();
            factory.pre_tile_encode(&mut f);
            failures.check(format!("{layer}[{i}]"), props_difference(case, f.props));
        }
    }
    println!("pre_tile_encode_cell: {checked} cases checked");
    if checked == 0 && std::env::var_os("NJORD_LAYER").is_some() {
        return; // the selected layers have no features in the cell
    }
    failures.assert_none(checked);
}

/// DEPARE colour bands with thresholds 2 / 5 / 20 m.
#[test]
fn depth_bands() {
    let config = SymbologyConfig {
        shallow_depth: 2.0,
        safety_depth: 5.0,
        deep_depth: 20.0,
        ..Default::default()
    };
    let cx = Context::new(config, colors());
    let depare = njord_symbology::layers::depare::Depare;
    let mut failures = Failures::default();
    let cases = cases("symbology/depth_bands.json");
    for (i, case) in cases.iter().enumerate() {
        failures.check(
            format!("[{i}] {}", case["input"]),
            check_encode(&depare, &cx, "DEPARE", case),
        );
    }
    failures.assert_none(cases.len());
}

/// [`add_sounding_conversions`] for 0.0…31.0 m, drying heights and edge values.
#[test]
fn display_depths() {
    let mut failures = Failures::default();
    let cases = cases("symbology/display_depths.json");
    for case in &cases {
        let mut props = serde_json::Map::new();
        add_sounding_conversions(&mut props, case["input"].as_f64().unwrap());
        failures.check(
            &case["input"],
            diff(port_expected(case), &Value::Object(props)),
        );
    }
    failures.assert_none(cases.len());
}

/// [`LightDescription::from`] (`None` without LITCHR) and its number format.
#[test]
fn light_description() {
    let mut failures = Failures::default();
    let descriptions = cases("symbology/light_description.json");
    for case in &descriptions {
        let actual = LightDescription::from(case["input"].as_object().unwrap())
            .map(|d| json!({"head": d.head, "tail": d.tail}))
            .unwrap_or(Value::Null);
        failures.check(&case["input"], diff(port_expected(case), &actual));
    }
    let numbers = cases("symbology/light_number_format.json");
    for case in &numbers {
        let actual = json!(light_description::format(case["input"].as_f64().unwrap()));
        failures.check(&case["input"], diff(port_expected(case), &actual));
    }
    failures.assert_none(descriptions.len() + numbers.len());
}

/// Decodes `{attribute: value}` with the named helper: an enum name or `null` for a
/// single-valued attribute, a list of names for a list attribute.
fn decode_helper(helper: &str, props: &njord_model::JsonObject) -> Option<Value> {
    macro_rules! single {
        ($t:ty) => {
            json!(<$t>::of(props).map(|v| v.name()))
        };
    }
    macro_rules! list {
        ($t:ty) => {
            json!(<$t>::of(props).iter().map(|v| v.name()).collect::<Vec<_>>())
        };
    }
    Some(match helper {
        "Bcnshp" => single!(Bcnshp),
        "Boyshp" => single!(Boyshp),
        "Catbrg" => list!(Catbrg),
        "Catcam" => single!(Catcam),
        "Catdam" => single!(Catdam),
        "Catfif" => single!(Catfif),
        "Catgat" => single!(Catgat),
        "Catlam" => single!(Catlam),
        "Catlmk" => list!(Catlmk),
        "Catlnd" => list!(Catlnd),
        "Catmor" => single!(Catmor),
        "Catobs" => single!(Catobs),
        "Catpip" => list!(Catpip),
        "Catpra" => single!(Catpra),
        "Catrea" => list!(Catrea),
        "Catsea" => single!(Catsea),
        "Catsil" => single!(Catsil),
        "Catspm" => list!(Catspm),
        "Cattrk" => single!(Cattrk),
        "Catwrk" => single!(Catwrk),
        "Conrad" => single!(Conrad),
        "Convis" => single!(Convis),
        "Functn" => list!(Functn),
        "Prodct" => list!(Prodct),
        "Quasou" => list!(Quasou),
        "Restrn" => list!(Restrn),
        "Topshp" => single!(Topshp),
        "Trafic" => single!(Trafic),
        "Watlev" => single!(Watlev),
        "Colour" => list!(Colour),
        _ => return None,
    })
}

/// Every attribute helper over 0 … max+1, plus [`sbdare::natsur`].
#[test]
fn attribute_helpers() {
    let mut failures = Failures::default();
    let helpers = cases("symbology/attribute_helpers.json");
    for case in &helpers {
        let input = &case["input"];
        let mut props = serde_json::Map::new();
        props.insert(
            input["attribute"].as_str().unwrap().to_owned(),
            input["value"].clone(),
        );
        let result = decode_helper(input["helper"].as_str().unwrap(), &props)
            .ok_or_else(|| "unknown helper".to_owned())
            .and_then(|actual| diff(port_expected(case), &actual));
        failures.check(input, result);
    }
    let natsur = cases("symbology/natsur.json");
    for case in &natsur {
        let values: Vec<i32> = case["input"]
            .as_array()
            .unwrap()
            .iter()
            .map(|v| v.as_i64().unwrap() as i32)
            .collect();
        failures.check(
            &case["input"],
            diff(port_expected(case), &json!(sbdare::natsur(&values))),
        );
    }
    failures.assert_none(helpers.len() + natsur.len());
}

/// The full style per depth × theme mode × colour library, compared as one document
/// (keys in order, every layer and expression).
///
/// Layer ids are numbered per style generation, from 1 for each layerable, which is what the
/// goldens record, so ids are compared as is (no masking).
#[test]
fn styles() {
    let factory = factory();
    let mut failures = Failures::default();
    let mut checked = 0;
    for path in json_files(&testdata().join("symbology/style")) {
        let name = path.file_stem().unwrap().to_string_lossy().into_owned();
        for case in load(&path)["cases"].as_array().unwrap() {
            checked += 1;
            let input = &case["input"];
            let depth: Depth = input["depth"].as_str().unwrap().parse().unwrap();
            let mode: ThemeMode = input["theme"]
                .as_str()
                .unwrap()
                .to_uppercase()
                .parse()
                .unwrap();
            let theme = match input.get("custom").and_then(Value::as_str) {
                Some(custom) => Theme::Custom(CustomTheme {
                    mode,
                    name: custom.to_owned(),
                }),
                None => Theme::Mode(mode),
            };
            let sprite_name = format!("{}_simplified", mode.path_name());
            let style = style(
                &factory,
                input["baseUrl"].as_str().unwrap(),
                &sprite_name,
                depth,
                &theme,
            );
            let actual: Value =
                serde_json::from_str(&serde_json::to_string(&style).unwrap()).unwrap();
            failures.check(&name, diff(port_expected(case), &actual));
        }
    }
    assert_eq!(checked, 27, "one style per depth × theme × custom theme");
    failures.assert_none(checked);
}

/// [`build_sector_svg`] over a grid of bearings, colours and radii.
#[test]
fn svg_sector() {
    let mut failures = Failures::default();
    let cases = cases("symbology/svg_sector.json");
    for case in &cases {
        let i = &case["input"];
        let svg = build_sector_svg(
            i["sectr1"].as_f64().unwrap(),
            i["sectr2"].as_f64().unwrap(),
            i["fillColor"].as_str().unwrap(),
            i["lineColor"].as_str().unwrap(),
            i["radius"].as_f64().unwrap(),
        );
        failures.check(i, diff(port_expected(case), &json!(svg)));
    }
    failures.assert_none(cases.len());
}

fn diff(expected: &Value, actual: &Value) -> Result<(), String> {
    match first_difference(expected, actual, "") {
        None => Ok(()),
        Some(d) => Err(d),
    }
}

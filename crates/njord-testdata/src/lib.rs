//! Golden-fixture helpers shared by every crate's tests (a dev-dependency only).
//!
//! Fixtures are read from the repo's `testdata/` ([`testdata`], overridden by `$NJORD_TESTDATA`)
//! and `conformance/golden/` ([`golden_dir`]). Conventions are in `testdata/README.md`: table
//! fixtures are `{ pins, decisions, generator, source, cases: [{ input, expected, port? }] }`
//! ([`cases`]), and a case's `port.expected`, when present, is what the server must produce
//! instead of the recorded `expected` ([`port_expected`]).
//!
//! [`round_trip`] checks a wire type against a fixture, [`first_difference`] compares two JSON
//! values, and [`Failures`] collects the bad cases so one test reports all of them. Every helper
//! panics on a missing or malformed fixture, which fails the calling test.

use std::path::{Path, PathBuf};

use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::Value;

/// The repository root.
pub fn repo_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

/// The fixture directory: `$NJORD_TESTDATA`, or `testdata/` in the repository.
pub fn testdata() -> PathBuf {
    std::env::var_os("NJORD_TESTDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| repo_root().join("testdata"))
}

/// The HTTP conformance goldens, `conformance/golden/`.
pub fn golden_dir() -> PathBuf {
    repo_root().join("conformance/golden")
}

/// Reads and parses the JSON file at `path`.
///
/// # Panics
///
/// Panics when the file cannot be read or is not JSON.
pub fn load(path: &Path) -> Value {
    let text = std::fs::read_to_string(path).unwrap_or_else(|e| panic!("{}: {e}", path.display()));
    serde_json::from_str(&text).unwrap_or_else(|e| panic!("{}: {e}", path.display()))
}

/// The `cases` of a `testdata/` table fixture, by path relative to [`testdata`].
///
/// # Panics
///
/// Panics when the fixture cannot be loaded or has no `cases` array.
pub fn cases(fixture: &str) -> Vec<Value> {
    let path = testdata().join(fixture);
    match load(&path).get("cases") {
        Some(Value::Array(cases)) => cases.clone(),
        _ => panic!("{}: no cases", path.display()),
    }
}

/// What the server must produce for a case.
///
/// That is `port.expected` when the case records a deliberate divergence from the recorded
/// output, otherwise `expected`.
pub fn port_expected(case: &Value) -> &Value {
    case.get("port")
        .and_then(|p| p.get("expected"))
        .unwrap_or(&case["expected"])
}

/// Every `*.json` file directly in `dir`, sorted.
///
/// # Panics
///
/// Panics when `dir` cannot be read.
pub fn json_files(dir: &Path) -> Vec<PathBuf> {
    let mut files: Vec<PathBuf> = std::fs::read_dir(dir)
        .unwrap_or_else(|e| panic!("{}: {e}", dir.display()))
        .map(|e| e.unwrap().path())
        .filter(|p| p.extension().is_some_and(|x| x == "json"))
        .collect();
    files.sort();
    files
}

/// How a fixture writes `None` fields, for [`round_trip`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Nulls {
    /// The fixture writes explicit `null` members, as API responses do: compare as is.
    Explicit,
    /// The fixture omits `null` members: drop them from the serialised output before comparing.
    Omitted,
}

/// Deserialises `expected` as `T`, serialises it back and requires the same JSON.
///
/// The same JSON means the same keys in the same order, the same values, and integers kept
/// distinct from floats. GeoJSON objects are the exception: the georust `geojson` crate orders
/// their members differently and leaves out the `null` members (`"id"`, `"bbox"`) the fixtures
/// contain, so inside a GeoJSON object member order is ignored and `null` members are dropped on
/// both sides.
///
/// # Errors
///
/// Returns a description of the deserialisation error, or of the [`first_difference`].
pub fn round_trip<T: DeserializeOwned + Serialize>(
    expected: &Value,
    nulls: Nulls,
) -> Result<T, String> {
    let typed: T =
        serde_json::from_value(expected.clone()).map_err(|e| format!("deserialise: {e}"))?;
    // Through a string, not `to_value`: only the writer prints an `f32` in its shortest form
    // (`0.1`, not `0.10000000149011612`), as the fixtures do.
    let text = serde_json::to_string(&typed).map_err(|e| format!("serialise: {e}"))?;
    let mut actual: Value = serde_json::from_str(&text).unwrap();
    if nulls == Nulls::Omitted {
        strip_nulls(&mut actual);
    }
    match first_difference(expected, &actual, "") {
        None => Ok(typed),
        Some(d) => Err(d),
    }
}

const GEOJSON_TYPES: &[&str] = &[
    "Point",
    "MultiPoint",
    "LineString",
    "MultiLineString",
    "Polygon",
    "MultiPolygon",
    "GeometryCollection",
    "Feature",
    "FeatureCollection",
];

fn is_geojson(object: &serde_json::Map<String, Value>) -> bool {
    object
        .get("type")
        .and_then(Value::as_str)
        .is_some_and(|t| GEOJSON_TYPES.contains(&t))
}

/// Removes every `null` object member from `v`, recursively.
pub fn strip_nulls(v: &mut Value) {
    match v {
        Value::Object(map) => {
            map.retain(|_, v| !v.is_null());
            map.values_mut().for_each(strip_nulls);
        }
        Value::Array(items) => items.iter_mut().for_each(strip_nulls),
        _ => {}
    }
}

/// The first place `a` and `b` differ, as a JSON pointer and description.
///
/// `path` is the pointer prefix, `""` at the root. GeoJSON objects are compared as described
/// in [`round_trip`].
pub fn first_difference(a: &Value, b: &Value, path: &str) -> Option<String> {
    match (a, b) {
        (Value::Object(x), Value::Object(y)) if is_geojson(x) => {
            let present = |m: &serde_json::Map<String, Value>| {
                let mut keys: Vec<String> = m
                    .iter()
                    .filter(|(_, v)| !v.is_null())
                    .map(|(k, _)| k.clone())
                    .collect();
                keys.sort();
                keys
            };
            let (xk, yk) = (present(x), present(y));
            if xk != yk {
                return Some(format!("{path}: GeoJSON members {xk:?} != {yk:?}"));
            }
            xk.iter()
                .find_map(|k| first_difference(&x[k], &y[k], &format!("{path}/{k}")))
        }
        (Value::Object(x), Value::Object(y)) => {
            let xk: Vec<&String> = x.keys().collect();
            let yk: Vec<&String> = y.keys().collect();
            if xk != yk {
                return Some(format!("{path}: keys {xk:?} != {yk:?}"));
            }
            x.iter()
                .find_map(|(k, xv)| first_difference(xv, &y[k], &format!("{path}/{k}")))
        }
        (Value::Array(x), Value::Array(y)) => {
            if x.len() != y.len() {
                return Some(format!("{path}: length {} != {}", x.len(), y.len()));
            }
            x.iter()
                .zip(y)
                .enumerate()
                .find_map(|(i, (xv, yv))| first_difference(xv, yv, &format!("{path}/{i}")))
        }
        (Value::Number(x), Value::Number(y)) => {
            let same = if x.is_f64() || y.is_f64() {
                x.is_f64() && y.is_f64() && x.as_f64() == y.as_f64()
            } else {
                x == y
            };
            (!same).then(|| format!("{path}: {x} != {y}"))
        }
        _ => (a != b).then(|| format!("{path}: {a} != {b}")),
    }
}

/// Collects failures so one test reports every bad case, not just the first.
#[derive(Default)]
pub struct Failures(Vec<String>);

impl Failures {
    /// Records `result`'s error under `label`, or returns its value.
    pub fn check<T>(
        &mut self,
        label: impl std::fmt::Display,
        result: Result<T, String>,
    ) -> Option<T> {
        match result {
            Ok(t) => Some(t),
            Err(e) => {
                self.0.push(format!("{label}: {e}"));
                None
            }
        }
    }

    /// Records a failure.
    pub fn push(&mut self, failure: String) {
        self.0.push(failure);
    }

    /// Asserts that no failure was recorded among `checked` cases.
    ///
    /// # Panics
    ///
    /// Panics listing every failure, or when `checked` is 0.
    pub fn assert_none(self, checked: usize) {
        assert!(checked > 0, "no cases checked");
        assert!(
            self.0.is_empty(),
            "{} of {checked} failed:\n{}",
            self.0.len(),
            self.0.join("\n")
        );
    }
}

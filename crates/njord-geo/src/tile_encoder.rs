//! The pure decisions of tile assembly: feature property filtering and the Natural Earth scale per
//! zoom.
//!
//! [`filter_props`] is pinned by `testdata/geo/filtered_props.json` and [`find_base_map_scale`] by
//! `testdata/geo/base_map_scale.json`. The database-backed tile assembly lives in the server.

use njord_model::JsonObject;
use serde_json::Value;

/// A feature property held a JSON object, which S-57 props never do.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct UnexpectedObject {
    /// The property that held the object.
    pub key: String,
}

impl std::fmt::Display for UnexpectedObject {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "unable to filter unexpected object in property '{}'",
            self.key
        )
    }
}

impl std::error::Error for UnexpectedObject {}

/// Drops, in place, the properties that carry no information for the style.
///
/// The dropped values are:
///
/// - blank strings (empty or all whitespace);
/// - empty arrays;
/// - a primitive whose JSON text is exactly `0` (the integer, or the string `"0"`) on an
///   *enumerated* S-57 attribute, where 0 is not a defined value and GDAL emits it for "unset".
///   `0.0`, and `0` on float, integer or unknown attributes (`VALSOU = 0`), are data and kept.
///
/// `is_enumerated` answers whether an attribute acronym has S-57 attribute type `E` (from
/// `s57attributes.json`).
///
/// # Errors
///
/// Returns [`UnexpectedObject`] if any value is a JSON object; `props` is then left unchanged.
pub fn filter_props(
    props: &mut JsonObject,
    is_enumerated: impl Fn(&str) -> bool,
) -> Result<(), UnexpectedObject> {
    if let Some((key, _)) = props.iter().find(|(_, v)| v.is_object()) {
        return Err(UnexpectedObject { key: key.clone() });
    }
    props.retain(|key, value| keep(key, value, &is_enumerated));
    Ok(())
}

fn keep(key: &str, value: &Value, is_enumerated: &impl Fn(&str) -> bool) -> bool {
    match value {
        Value::String(s) if s.trim().is_empty() => false,
        Value::String(s) if s == "0" => !is_enumerated(key),
        Value::Number(n) if n.to_string() == "0" => !is_enumerated(key),
        Value::Array(a) => !a.is_empty(),
        _ => true,
    }
}

/// The Natural Earth scale denominator whose `base_features` rows a tile at zoom `z` draws.
///
/// That is 1:110m below z3, 1:50m below z5 and 1:10m from z5 up.
pub fn find_base_map_scale(z: u32) -> i32 {
    if z < 3 {
        110_000_000
    } else if z < 5 {
        50_000_000
    } else {
        10_000_000
    }
}

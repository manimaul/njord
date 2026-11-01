//! Typed reads of S-57 feature props, with lenient parsing.
//!
//! A value is read through its JSON primitive text, so the string `"3"` reads as the integer 3
//! while the real `3.0` does not, and GDAL's list attributes arrive as arrays of strings
//! (`"COLOUR": ["1", "3"]`).
//!
//! A list where a scalar is expected, or a scalar where a list is expected, answers `None`; no
//! fixture reaches those cases.

use njord_model::JsonObject;
use serde_json::Value;

/// The text of a string, number or boolean; `None` for `null`, arrays and objects.
fn content(v: &Value) -> Option<String> {
    match v {
        Value::String(s) => Some(s.clone()),
        Value::Number(n) => Some(n.to_string()),
        Value::Bool(b) => Some(b.to_string()),
        _ => None,
    }
}

/// Like [`content`], but `null` reads as the text `"null"`.
fn content_or_null_text(v: &Value) -> Option<String> {
    match v {
        Value::Null => Some("null".to_owned()),
        Value::Array(_) | Value::Object(_) => None,
        v => content(v),
    }
}

/// Parses a decimal `i32`, `None` when the text is not one.
fn to_int(s: &str) -> Option<i32> {
    s.parse().ok()
}

/// Parses the decimal forms S-57 values take, `None` for anything else (including `inf`).
///
/// The text is parsed straight to the target width, so an `f32` read is not an `f64` read
/// narrowed afterwards.
fn to_number<T: std::str::FromStr>(s: &str) -> Option<T> {
    if s.eq_ignore_ascii_case("inf") || s.eq_ignore_ascii_case("infinity") {
        return None;
    }
    s.parse().ok()
}

pub fn string_value(props: &JsonObject, key: &str) -> Option<String> {
    props.get(key).and_then(content)
}

pub fn int_value(props: &JsonObject, key: &str) -> Option<i32> {
    string_value(props, key).as_deref().and_then(to_int)
}

pub fn float_value(props: &JsonObject, key: &str) -> Option<f32> {
    string_value(props, key).as_deref().and_then(to_number)
}

pub fn double_value(props: &JsonObject, key: &str) -> Option<f64> {
    string_value(props, key).as_deref().and_then(to_number)
}

/// The elements of a list attribute as text, `None` when the attribute is absent or not a list.
pub fn string_values(props: &JsonObject, key: &str) -> Option<Vec<String>> {
    match props.get(key)? {
        Value::Array(items) => items.iter().map(content_or_null_text).collect(),
        _ => None,
    }
}

/// The integer elements of a list attribute; elements that are not integers are dropped.
pub fn int_values(props: &JsonObject, key: &str) -> Vec<i32> {
    string_values(props, key)
        .unwrap_or_default()
        .iter()
        .filter_map(|s| to_int(s))
        .collect()
}

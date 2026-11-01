//! MapLibre filter expressions shared by the layerables.
//!
//! The [`GT`]/[`LT`] names are swapped relative to their operators ([`GT`] is `"<"`); read them as
//! "the threshold is greater than the prop". Mind this when writing a comparison filter.

use njord_model::{Color, Theme};
use serde_json::{Value, json};

use crate::ColorLibrary;

pub const ANY: &str = "any";
pub const ALL: &str = "all";
pub const GT: &str = "<";
pub const GT_EQ: &str = "<=";
pub const LT: &str = ">";
pub const LT_EQ: &str = ">=";
pub const EQ: &str = "==";
pub const IS_IN: &str = "in";
pub const NOT_EQ: &str = "!=";

pub fn eq_type_line_string_or_polygon() -> Value {
    json!([NOT_EQ, "$type", "Point"])
}

pub fn eq_type_point_or_polygon() -> Value {
    json!([NOT_EQ, "$type", "LineString"])
}

pub fn eq_type_line_string() -> Value {
    json!([EQ, "$type", "LineString"])
}

pub fn eq_type_polygon() -> Value {
    json!([EQ, "$type", "Polygon"])
}

pub fn eq_type_point() -> Value {
    json!([EQ, "$type", "Point"])
}

/// A `case` on the `AC` prop over the theme's colours (restricted to `options`
/// when given), defaulting to transparent.
pub fn area_fill_color(colors: &ColorLibrary, options: Option<&[Color]>, theme: &Theme) -> Value {
    case_on(colors, "AC", options, theme)
}

/// As [`area_fill_color`], on the `LC` prop.
pub fn line_color(colors: &ColorLibrary, options: Option<&[Color]>, theme: &Theme) -> Value {
    case_on(colors, "LC", options, theme)
}

fn case_on(colors: &ColorLibrary, prop: &str, options: Option<&[Color]>, theme: &Theme) -> Value {
    let mut expr = vec![json!("case")];
    for tuple in colors.colors(theme) {
        if options.is_none_or(|o| o.contains(&tuple.color)) {
            expr.push(json!(["==", ["get", prop], tuple.color.as_str()]));
            expr.push(json!(tuple.hex));
        }
    }
    expr.push(json!("rgba(255, 255, 255, 0)"));
    Value::Array(expr)
}

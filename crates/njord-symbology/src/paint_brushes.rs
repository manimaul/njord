//! The small style-value types the layer helpers take (`PaintBrushes.kt`).

use njord_model::Sprite;
use serde_json::{Value, json};

use crate::kotlin::float_json;

/// An icon: a fixed sprite, or the sprite named by a feature prop (`SY`, `SY2`, …).
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Symbol {
    Sprite(Sprite),
    /// `["get", "SY"]`, or `["get", "SY<n>"]`.
    Property(Option<i32>),
}

impl Default for Symbol {
    fn default() -> Self {
        Symbol::Property(None)
    }
}

impl Symbol {
    pub fn property(&self) -> Value {
        match self {
            Symbol::Sprite(s) => json!(s.as_str()),
            Symbol::Property(None) => json!(["get", "SY"]),
            Symbol::Property(Some(n)) => json!(["get", format!("SY{n}")]),
        }
    }
}

/// Label text: literal, or a feature prop.
#[derive(Clone, Debug, PartialEq)]
pub enum Label {
    Text(String),
    Property(String),
}

impl Label {
    pub fn property(&self) -> Value {
        match self {
            Label::Text(t) => json!(t),
            Label::Property(name) => json!(["get", name]),
        }
    }
}

/// Icon rotation: fixed degrees, or a feature prop.
#[derive(Clone, Debug, PartialEq)]
pub enum IconRot {
    Degrees(f32),
    Property(String),
}

impl IconRot {
    pub fn property(&self) -> Value {
        match self {
            IconRot::Degrees(d) => float_json(*d),
            IconRot::Property(name) => json!(["get", name]),
        }
    }
}

/// An icon or text offset.
#[derive(Clone, Debug, PartialEq)]
pub enum Offset {
    /// `Offset.Coord(x, y)`; positive is right and down.
    Coord(f32, f32),
    /// `Offset.EvalEq`: `eq` when the prop `key` equals `value`, else `neq`.
    EvalEq {
        key: String,
        value: String,
        eq: (f32, f32),
        neq: (f32, f32),
    },
}

impl Offset {
    pub fn property(&self) -> Value {
        match self {
            Offset::Coord(x, y) => coord(*x, *y),
            Offset::EvalEq {
                key,
                value,
                eq,
                neq,
            } => json!([
                "case",
                ["==", ["get", key], value],
                ["literal", coord(eq.0, eq.1)],
                ["literal", coord(neq.0, neq.1)],
            ]),
        }
    }
}

fn coord(x: f32, y: f32) -> Value {
    json!([float_json(x), float_json(y)])
}

/// Line dash style.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub enum LineStyle {
    /// `[1, 2]`.
    DashLine,
    #[default]
    Solid,
    /// `[width, gap]`.
    CustomDash(f32, f32),
}

impl LineStyle {
    pub fn line_dash_array(&self) -> Option<Vec<f32>> {
        match self {
            LineStyle::DashLine => Some(vec![1.0, 2.0]),
            LineStyle::Solid => None,
            LineStyle::CustomDash(width, gap) => Some(vec![*width, *gap]),
        }
    }
}

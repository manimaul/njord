//! Sounding SOUNDG
//! Geometric primitives: Point
//!
//! Set Attribute_A:     EXPSOU; NOBJNM; OBJNAM; QUASOU; (?)SOUACC; STATUS; (?)TECSOU; VERDAT;
//! Set Attribute_B:     INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
//! Set Attribute_C:     RECDAT; RECIND; SORDAT; SORIND;
//!
//! Definition:
//! A measured water depth or spot which has been reduced to a vertical datum (may be a drying height).
//! References
//! INT 1:    II 10-15;
//! S-4:    403.1; 410; 412-412.4; 413.1; 417.3;
//! Remarks:
//! The value of the sounding is encoded in the 3-D Coordinate field of the Spatial Record Structure (see S-57 Part 3).
//! Drying heights (drying soundings) are indicated by a negative value.
//! Distinction:
//! depth area; wreck; underwater/awash rock; obstruction;
//!
//! Drying heights (S-52 PresLib SNDFRM04): the depth parts in the tile are negative, so every text
//! field renders `abs` of its part - the digits are drawn positive, never with a minus sign - the
//! shallow colour applies (a negative value is always under the safety depth) and the layers that
//! draw the whole number carry the SOUNDSA1 underline, fitted to the digits with icon-text-fit.
//!
//! The text layers here are `pub` because [`obstrn`](crate::layers::obstrn) reuses them.

use crate::layers::prelude::*;

const TEXT_SIZE: f32 = 16.0;

#[derive(Clone, Copy, Debug, Default)]
pub struct Soundg;

impl Layerable for Soundg {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if let Some(meters) = double_value(&feature.props, "METERS") {
            add_sounding_conversions(&mut feature.props, meters);
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        text_layers(b)
    }
}

/// The depth text layers for the builder's depth unit.
pub fn text_layers(b: &LayerBuilder) -> Vec<Layer> {
    let key = b.key;
    match b.depth {
        Depth::Fathoms => vec![
            single_text(b, format!("{key}_fathoms"), "FATHOMS", Some("FATHOMS_FT")),
            primary_text_layer(b, format!("{key}_fathoms_offset"), "FATHOMS", "FATHOMS_FT"),
            sub_text_layer(b, format!("{key}_fathoms_feet_offset"), "FATHOMS_FT"),
        ],
        Depth::Meters => vec![
            single_text(b, format!("{key}_meters"), "METERS_W", Some("METERS_T")),
            primary_text_layer(b, format!("{key}_meters_offset"), "METERS_W", "METERS_T"),
            sub_text_layer(b, format!("{key}_meters_tenths_offset"), "METERS_T"),
        ],
        Depth::Feet => vec![single_text(b, format!("{key}_feet"), "FEET", None)],
    }
}

/// Ascending METERS gives shallower (more safety-critical) soundings priority when labels collide.
fn sort_key_by_depth() -> Value {
    json!(["get", "METERS"])
}

/// The magnitude of a depth part - drying heights are stored negative and drawn without a sign.
fn abs_text(key: &str) -> Value {
    json!(["to-string", ["abs", ["get", key]]])
}

/// S-52 SOUNDSA1, the drying-height underline, only for negative soundings. An empty image name
/// resolves to no icon. The sprite is stretchable (see sprites/create_sheet.py) and fitted to the
/// text box with `icon-text-fit`; the bottom padding keeps the line clear of the text halo.
///
/// The icon takes no part in collision detection (`icon-allow-overlap` and
/// `icon-ignore-placement`): its fitted box reaches into the subscript digit's box, which is
/// placed first, and MapLibre would then drop the whole leading-digit symbol. With the icon
/// exempt, the symbol places on its text alone and the underline shows exactly when the digits do.
fn drying_underline(layout: Layout) -> Layout {
    Layout {
        icon_image: Some(json!([
            "case",
            ["<", ["get", "METERS"], 0],
            Sprite::SOUNDSA1.as_str(),
            ""
        ])),
        icon_text_fit: Some(IconTextFit::Both),
        icon_text_fit_padding: Some(vec![0.0, 0.0, 3.0, 0.0]),
        icon_allow_overlap: Some(true),
        icon_ignore_placement: Some(true),
        ..layout
    }
}

fn paint(b: &LayerBuilder) -> Paint {
    Paint {
        text_color: Some(text_color(b)),
        text_halo_color: Some(b.color(Color::DEPDW)),
        text_halo_width: Some(2.5),
        ..Default::default()
    }
}

fn deep_depth(b: &LayerBuilder) -> Value {
    float_json(b.config().deep_depth)
}

fn single_text(b: &LayerBuilder, id: String, text_key: &str, sub_text_key: Option<&str>) -> Layer {
    Layer {
        filter: Some(match sub_text_key {
            Some(sub) => json!([
                filters::ALL,
                filters::eq_type_point(),
                [
                    filters::ANY,
                    [">=", "METERS", deep_depth(b)],
                    ["==", sub, 0]
                ],
            ]),
            None => filters::eq_type_point(),
        }),
        layout: Some(drying_underline(Layout {
            text_font: Some(vec![Font::RobotoBold]),
            text_anchor: Some(Anchor::Center),
            text_justify: Some(TextJustify::Center),
            text_field: Some(abs_text(text_key)),
            text_allow_overlap: Some(false),
            text_ignore_placement: Some(false),
            text_size: Some(TEXT_SIZE),
            symbol_placement: Some(Placement::Point),
            symbol_sort_key: Some(sort_key_by_depth()),
            ..Default::default()
        })),
        paint: Some(paint(b)),
        ..b.layer(id, LayerType::Symbol)
    }
}

/// Whole-number depth, anchored so it sits up-and-left of the feature point (its own
/// bottom-right corner offset down from the point) — paired with [`sub_text_layer`], whose
/// top-left anchor sits just right of the same point, so the smaller fractional digit tucks
/// in below and to the right of this number regardless of how many digits it has.
fn primary_text_layer(b: &LayerBuilder, id: String, text_key: &str, sub_text_key: &str) -> Layer {
    Layer {
        filter: Some(json!([
            filters::ALL,
            filters::eq_type_point(),
            ["!=", sub_text_key, 0],
            ["<", "METERS", deep_depth(b)],
        ])),
        layout: Some(drying_underline(Layout {
            text_font: Some(vec![Font::RobotoBold]),
            text_anchor: Some(Anchor::BottomRight),
            text_offset: Some(json!([float_json(0.0), float_json(0.6)])),
            text_justify: Some(TextJustify::Center),
            text_field: Some(abs_text(text_key)),
            text_allow_overlap: Some(false),
            text_ignore_placement: Some(false),
            text_size: Some(TEXT_SIZE),
            // Zeroed so the default 2px collision buffer doesn't bridge the intentional ~1-2px
            // gap to sub_text_layer's box and flag this pair as colliding with each other.
            text_padding: Some(0.0),
            symbol_placement: Some(Placement::Point),
            symbol_sort_key: Some(sort_key_by_depth()),
            ..Default::default()
        })),
        paint: Some(paint(b)),
        ..b.layer(id, LayerType::Symbol)
    }
}

/// The fractional digit. SOUNDSA1 underlines only the leading digits, so no icon here.
fn sub_text_layer(b: &LayerBuilder, id: String, text_key: &str) -> Layer {
    Layer {
        filter: Some(json!([
            filters::ALL,
            filters::eq_type_point(),
            ["!=", text_key, 0],
            ["<", "METERS", deep_depth(b)],
        ])),
        layout: Some(Layout {
            text_font: Some(vec![Font::RobotoBold]),
            text_anchor: Some(Anchor::TopLeft),
            text_offset: Some(json!([float_json(0.1), float_json(0.0)])),
            text_justify: Some(TextJustify::Center),
            text_field: Some(abs_text(text_key)),
            text_allow_overlap: Some(false),
            text_ignore_placement: Some(false),
            text_size: Some(TEXT_SIZE - 4.0),
            text_padding: Some(0.0),
            symbol_placement: Some(Placement::Point),
            symbol_sort_key: Some(sort_key_by_depth()),
            ..Default::default()
        }),
        paint: Some(paint(b)),
        ..b.layer(id, LayerType::Symbol)
    }
}

fn text_color(b: &LayerBuilder) -> Value {
    json!([
        "case",
        ["<=", ["get", "METERS"], deep_depth(b)],
        b.color(Color::SNDG2),
        b.color(Color::SNDG1),
    ])
}

//! Geometry Primitives: Point, Line, Area
//!
//! Object: Seabed area
//!
//! Acronym: SBDARE
//!
//! Code: 116
//!
//! S-52 lookups: `TX(NATSUR,1,2,2,'15110',0,0,CHBLK,25)` on every geometry, centred, plus
//! `LS(SOLD,1,CHGRD)` on a line and `LS(DASH,1,CHGRD)` around an area whose WATLEV is 3 (always
//! under water) or 4 (covers and uncovers). No fill.
//!
//! Not done: an intertidal (WATLEV 4) rock, lava or coral area is filled with the RCKLDG01 pattern
//! in place of the text, but that pattern is vector-only in the presentation library and isn't in
//! the sprite sheets, so those areas get the text instead.

use crate::layers::prelude::*;

const NATURE_OF_SURFACE: &str = "_NS";
const DASHED_OUTLINE: &str = "_DO";

#[derive(Clone, Copy, Debug, Default)]
pub struct Sbdare;

impl Layerable for Sbdare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if let Some(text) = natsur(&int_values(&feature.props, "NATSUR")) {
            feature.props.insert(NATURE_OF_SURFACE.into(), text.into());
        }
        if let Some(3 | 4) = int_value(&feature.props, "WATLEV") {
            feature.props.insert(DASHED_OUTLINE.into(), true.into());
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    width: 1.0,
                    filter: Some(filters::eq_type_line_string()),
                    ..Default::default()
                },
            ),
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    width: 1.0,
                    style: LineStyle::DashLine,
                    filter: Some(json!([
                        filters::ALL,
                        filters::eq_type_polygon(),
                        ["has", DASHED_OUTLINE]
                    ])),
                    ..Default::default()
                },
            ),
            b.text_layer(
                json!(["get", NATURE_OF_SURFACE]),
                TextLayerOpts {
                    filter: Some(json!(["has", NATURE_OF_SURFACE])),
                    ..Default::default()
                },
            ),
        ]
    }
}

/// PresLib 4.0 Part I §14.6.3 "Nature of seabed abbreviations" - space separated, "to reduce
/// undue clutter ... " M S G" rather than "Mud Sand Gravel"". Lava and boulders abbreviate
/// to `R`, the same as rock, so repeats are dropped. `None` when nothing maps.
pub fn natsur(values: &[i32]) -> Option<String> {
    let mut parts: Vec<&str> = Vec::new();
    for abbreviation in values.iter().filter_map(|&v| natsur_abbreviation(v)) {
        if !parts.contains(&abbreviation) {
            parts.push(abbreviation);
        }
    }
    (!parts.is_empty()).then(|| parts.join(" "))
}

fn natsur_abbreviation(id: i32) -> Option<&'static str> {
    Some(match id {
        1 => "M",
        2 => "Cy",
        3 => "Si",
        4 => "S",
        5 => "St",
        6 => "G",
        7 => "P",
        8 => "Cb",
        9 => "R",
        11 => "R",
        14 => "Co",
        17 => "Sh",
        18 => "R",
        _ => return None,
    })
}

//! Geometry Primitives: Point, Line
//!
//! Object: Land elevation
//!
//! Acronym: LNDELV
//!
//! Code: 75
//!
//! S-52 lookups: a point is `SY(POSGEN04);TE('%3.0lf m','ELEVAT',3,2,2,'15110',1,0,CHBLK,28)`, a
//! line (a land contour) is `LS(SOLD,1,LANDF)` with no text.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lndelv;

const FEET_PER_METER: f64 = 3.28084;

impl Layerable for Lndelv {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::POSGEN04);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::LANDF,
                LineColorOpts {
                    width: 1.0,
                    filter: Some(filters::eq_type_line_string()),
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                ..Default::default()
            }),
            b.text_layer(
                elevation(b.depth),
                TextLayerOpts {
                    text_anchor: Anchor::Left,
                    text_justify: TextJustify::Left,
                    text_offset: Some(Offset::Coord(1.2, 0.0)),
                    filter: Some(json!([
                        filters::ALL,
                        filters::eq_type_point(),
                        ["has", "ELEVAT"]
                    ])),
                },
            ),
        ]
    }
}

/// Whole units, per `%3.0lf`. Like [`ClearanceLabel`] the tile stays unit-agnostic and the style
/// picks metres or feet from the depth preference.
///
/// [`ClearanceLabel`]: crate::layers::clearance_label::ClearanceLabel
fn elevation(depth: Depth) -> Value {
    match depth {
        Depth::Meters => json!(["concat", ["to-string", ["round", ["get", "ELEVAT"]]], "m"]),
        Depth::Feet | Depth::Fathoms => json!([
            "concat",
            [
                "to-string",
                ["round", ["*", ["get", "ELEVAT"], FEET_PER_METER]]
            ],
            "ft"
        ]),
    }
}

//! Geometry Primitives: Point, Area
//!
//! Object: Precautionary area
//!
//! Acronym: PRCARE
//!
//! Code: 96

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Prcare;

const LINE_COLOR: Color = Color::TRFCF;
const SYMBOL: Sprite = Sprite::PRCARE12;

impl Layerable for Prcare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(SYMBOL);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                ..Default::default()
            }),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 2.0,
                    style: LineStyle::CustomDash(3.0, 2.0),
                    ..Default::default()
                },
            ),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                symbol: Some(SYMBOL),
                // Room for lights / buoys.
                icon_offset: Some(vec![30.0, 30.0]),
                ..Default::default()
            }),
        ]
    }
}

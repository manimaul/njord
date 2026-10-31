//! Geometry Primitives: Point, Area
//!
//! Object: Weed/Kelp
//!
//! Acronym: WEDKLP
//!
//! Code: 158

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHGRF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Wedklp;

impl Layerable for Wedklp {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::WEDKLP03);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts::default()),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    width: 1.0,
                    ..Default::default()
                },
            ),
        ]
    }
}

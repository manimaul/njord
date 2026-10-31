//! Geometry Primitives: Point, Area
//!
//! Object: Anchor berth
//!
//! Acronym: ACHBRT
//!
//! Code: 3

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Achbrt;

impl Layerable for Achbrt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        feature.point_symbol(Sprite::ACHBRT07);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 2.0,
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
        ]
    }
}

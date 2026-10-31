//! Geometry Primitives: Point, Area
//!
//! Object: Log pond
//!
//! Acronym: LOGPON
//!
//! Code: 80

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Logpon;

const LINE_COLOR: Color = Color::CHBLK;

impl Layerable for Logpon {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::FLTHAZ02);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 1.0,
                    style: LineStyle::CustomDash(3.0, 4.0),
                    ..Default::default()
                },
            ),
        ]
    }
}

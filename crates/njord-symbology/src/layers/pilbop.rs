//! Geometry Primitives: Point, Area
//!
//! Object: Pilot boarding place
//!
//! Acronym: PILBOP
//!
//! Code: 91

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Pilbop;

const LINE_COLOR: Color = Color::TRFCF;

impl Layerable for Pilbop {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::PILBOP02);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts::default()),
        ]
    }
}

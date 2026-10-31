//! Geometry Primitives: Point, Area
//!
//! Object: Offshore platform
//!
//! Acronym: OFSPLF
//!
//! Code: 87

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ofsplf;

const AREA_COLOR: Color = Color::CHBRN;
const LINE_COLOR: Color = Color::CSTLN;

impl Layerable for Ofsplf {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
        feature.point_symbol(Sprite::OFSPLF01);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 4.0,
                    ..Default::default()
                },
            ),
        ]
    }
}

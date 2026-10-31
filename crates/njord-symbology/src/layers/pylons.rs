//! Geometry Primitives: Point, Area
//!
//! Object: Pylon/bridge support
//!
//! Acronym: PYLONS
//!
//! Code: 98

use crate::layers::prelude::*;

const AREA_COLOR: Color = Color::CHBRN;
const LINE_COLOR: Color = Color::CSTLN;

#[derive(Clone, Copy, Debug, Default)]
pub struct Pylons;

impl Layerable for Pylons {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
        feature.point_symbol(Sprite::POSGEN03);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 2.0,
                    ..Default::default()
                },
            ),
        ]
    }
}

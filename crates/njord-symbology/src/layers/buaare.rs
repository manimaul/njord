//! Geometry Primitives: Point, Area
//!
//! Object: Built-up area
//!
//! Acronym: BUAARE
//!
//! Code: 13

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Buaare;

impl Layerable for Buaare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::BUAARE02);
        feature.area_color(Color::LANDF);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::CHBRN, None),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 1.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts::default()),
        ]
    }
}

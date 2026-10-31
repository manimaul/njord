//! Geometry Primitives: Point
//!
//! Object: Radar reflector
//!
//! Acronym: RADRFL
//!
//! Code: 101

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Radrfl;

impl Layerable for Radrfl {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::RADRFL03);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts::default()),
        ]
    }
}

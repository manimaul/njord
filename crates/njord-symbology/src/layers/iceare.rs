//! Geometry Primitives: Area
//!
//! Object: Ice area
//!
//! Acronym: ICEARE
//!
//! Code: 66

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Iceare;

impl Layerable for Iceare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::ICEARE04P);
        feature.area_pattern(Sprite::ICEARE04P);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_pattern(Some(Sprite::ICEARE04P)),
        ]
    }
}

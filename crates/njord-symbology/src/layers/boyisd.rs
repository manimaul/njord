//! Geometry Primitives: Point
//!
//! Object: Buoy, isolated danger
//!
//! Acronym: BOYISD
//!
//! Code: 16

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Boyisd;

impl Layerable for Boyisd {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::BOYISD12);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

//! Geometry Primitives: Point
//!
//! Object: Buoy, installation
//!
//! Acronym: BOYINB
//!
//! Code: 15

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Boyinb;

impl Layerable for Boyinb {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::BOYMOR11);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

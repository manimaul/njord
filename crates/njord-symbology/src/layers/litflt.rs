//! Geometry Primitives: Point
//!
//! Object: Light float
//!
//! Acronym: LITFLT
//!
//! Code: 76

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Litflt;

const SYMBOL: Sprite = Sprite::LITFLT02;

impl Layerable for Litflt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            symbol: Symbol::Sprite(SYMBOL),
            ..Default::default()
        })]
    }
}

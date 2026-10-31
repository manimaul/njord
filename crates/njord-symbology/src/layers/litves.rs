//! Geometry Primitives: Point
//!
//! Object: Light vessel
//!
//! Acronym: LITVES
//!
//! Code: 77

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Litves;

const SYMBOL: Sprite = Sprite::LITVES02;

impl Layerable for Litves {
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

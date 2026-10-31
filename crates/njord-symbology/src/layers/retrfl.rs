//! Geometry Primitives: Point
//!
//! Object: Retro-reflector
//!
//! Acronym: RETRFL
//!
//! Code: 112

use crate::layers::prelude::*;

const SYMBOL: Sprite = Sprite::RETRFL02;

#[derive(Clone, Copy, Debug, Default)]
pub struct Retrfl;

impl Layerable for Retrfl {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            symbol: Symbol::Sprite(SYMBOL),
            anchor: Anchor::Center,
            ..Default::default()
        })]
    }
}

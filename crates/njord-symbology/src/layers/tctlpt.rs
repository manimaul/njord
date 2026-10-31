//! Geometry Primitives: Point
//!
//! Object: Traffic control point
//!
//! Acronym: TCTLPT
//!
//! Code: 144

use crate::layers::prelude::*;

const SYMBOL: Sprite = Sprite::CHKPNT01;

#[derive(Clone, Copy, Debug, Default)]
pub struct Tctlpt;

impl Layerable for Tctlpt {
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

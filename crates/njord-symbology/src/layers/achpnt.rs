//! Geometry Primitives: Point
//!
//! Object: Anchor
//!
//! Acronym: ACHPNT
//!
//! Code: 1003
//!
//! S-52 display: SY(ACHPNT01) — rendered using ACHARE02 (identical symbol)

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Achpnt;

impl Layerable for Achpnt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::ACHARE02);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            symbol: Symbol::Sprite(Sprite::ACHARE02),
            anchor: Anchor::Center,
            ..Default::default()
        })]
    }
}

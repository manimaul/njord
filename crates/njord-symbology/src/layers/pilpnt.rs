//! Geometry Primitives: Point
//!
//! Object: Pile
//!
//! Acronym: PILPNT
//!
//! Code: 90

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Pilpnt;

const SYMBOL: Sprite = Sprite::PILPNT02;

impl Layerable for Pilpnt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            anchor: Anchor::Center,
            icon_rotation_alignment: Some(IconRotationAlignment::Map),
            ..Default::default()
        })]
    }
}

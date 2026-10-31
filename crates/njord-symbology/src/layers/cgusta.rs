//! Geometry Primitives: Point
//!
//! Object: Coastguard station
//!
//! Acronym: CGUSTA
//!
//! Code: 29

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Cgusta;

impl Layerable for Cgusta {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CGUSTA02);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            anchor: Anchor::BottomLeft,
            ..Default::default()
        })]
    }
}

//! Geometry Primitives: Point
//!
//! Object: Fog signal
//!
//! Acronym: FOGSIG
//!
//! Code: 58

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Fogsig;

impl Layerable for Fogsig {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::FOGSIG01);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            icon_offset: Some(Offset::Coord(-10.0, 10.0)),
            icon_allow_overlap: true,
            ..Default::default()
        })]
    }
}

//! Geometry Primitives: Point
//!
//! Object: Signal station, warning
//!
//! Acronym: SISTAW
//!
//! Code: 124

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Sistaw;

impl Layerable for Sistaw {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::SISTAW03);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            icon_offset: Some(Offset::Coord(0.0, -24.0)),
            ..Default::default()
        })]
    }
}

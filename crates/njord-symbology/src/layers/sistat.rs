//! Geometry Primitives: Point
//!
//! Object: Signal station, traffic
//!
//! Acronym: SISTAT
//!
//! Code: 123

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Sistat;

impl Layerable for Sistat {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::SISTAT03);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            icon_offset: Some(Offset::Coord(0.0, -24.0)),
            ..Default::default()
        })]
    }
}

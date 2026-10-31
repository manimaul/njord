//! Geometry Primitives: Point
//!
//! Object: Buoy, safe water
//!
//! Acronym: BOYSAW
//!
//! Code: 18

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Boysaw;

impl Layerable for Boysaw {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(match Colour::first(&feature.props) {
            Some(Colour::Red) => Sprite::BOYSAW12,
            _ => Sprite::BOYSPP11,
        });
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

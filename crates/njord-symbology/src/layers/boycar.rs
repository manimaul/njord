//! Geometry Primitives: Point
//!
//! Object: Buoy, cardinal
//!
//! Acronym: BOYCAR
//!
//! Code: 14

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Boycar;

impl Layerable for Boycar {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(match Catcam::of(&feature.props) {
            Some(Catcam::NORTH_CARDINAL_MARK) => Sprite::BOYCAR01,
            Some(Catcam::EAST_CARDINAL_MARK) => Sprite::BOYCAR02,
            Some(Catcam::SOUTH_CARDINAL_MARK) => Sprite::BOYCAR03,
            Some(Catcam::WEST_CARDINAL_MARK) => Sprite::BOYCAR04,
            None => Sprite::BOYDEF03,
        });
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

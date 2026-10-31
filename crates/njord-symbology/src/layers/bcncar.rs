//! Geometry Primitives: Point
//!
//! Object: Beacon, cardinal
//!
//! Acronym: BCNCAR
//!
//! Code: 5

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Bcncar;

impl Layerable for Bcncar {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(match Catcam::of(&feature.props) {
            Some(Catcam::NORTH_CARDINAL_MARK) => Sprite::BCNCAR01,
            Some(Catcam::EAST_CARDINAL_MARK) => Sprite::BCNCAR02,
            Some(Catcam::SOUTH_CARDINAL_MARK) => Sprite::BCNCAR03,
            Some(Catcam::WEST_CARDINAL_MARK) => Sprite::BCNCAR04,
            None => Sprite::BCNDEF13,
        });
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

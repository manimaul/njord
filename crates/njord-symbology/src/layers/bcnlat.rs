//! Geometry Primitives: Point
//!
//! Object: Beacon, lateral
//!
//! Acronym: BCNLAT
//!
//! Code: 7

use crate::layers::prelude::*;

/// Lateral beacons; `Bcnsaw` and `Bcnspp` reuse [`bcnshp`].
#[derive(Clone, Copy, Debug, Default)]
pub struct Bcnlat;

/// The symbol by BCNSHP and the first colour, BCNDEF13 without a shape.
pub fn bcnshp(feature: &mut ChartFeature) {
    let colour = Colour::first(&feature.props);
    feature.point_symbol(match Bcnshp::of(&feature.props) {
        Some(Bcnshp::STAKE_POLE_PERCH_POST | Bcnshp::WHITY) => match colour {
            Some(Colour::Red) => Sprite::BCNLAT21,   // red tall beacon
            Some(Colour::Green) => Sprite::BCNLAT22, // green tall beacon
            Some(Colour::Black) => Sprite::BCNSAW21, // black tall beacon
            _ => Sprite::BCNSPP21,                   // yellow tall beacon
        },
        Some(Bcnshp::BEACON_TOWER | Bcnshp::LATTICE_BEACON | Bcnshp::PILE_BEACON) => match colour {
            Some(Colour::Red) => Sprite::BCNLAT15,   // red short beacon
            Some(Colour::Green) => Sprite::BCNLAT16, // green short beacon
            Some(Colour::Black) => Sprite::BCNSAW13, // black short beacon
            _ => Sprite::BCNSPP13,                   // yellow short beacon
        },
        Some(Bcnshp::CAIRN) => Sprite::CAIRNS11,
        Some(Bcnshp::BUOYANT_BEACON) => Sprite::BCNLAT21,
        None => Sprite::BCNDEF13,
    });
}

impl Layerable for Bcnlat {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        bcnshp(feature);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

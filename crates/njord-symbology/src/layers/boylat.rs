//! Geometry Primitives: Point
//!
//! Object: Buoy, lateral
//!
//! Acronym: BOYLAT
//!
//! Code: 17

use crate::layers::prelude::*;

/// Lateral buoys; `Boyspp` reuses [`boyshp`].
#[derive(Clone, Copy, Debug, Default)]
pub struct Boylat;

pub fn half_triangle(feature: &mut ChartFeature) {
    feature.point_symbol(match Colour::first(&feature.props) {
        Some(Colour::Red) => Sprite::BOYLAT14,
        Some(Colour::Green) => Sprite::BOYLAT13,
        _ => Sprite::BOYSPP15,
    });
}

pub fn rhomboid(feature: &mut ChartFeature) {
    feature.point_symbol(match Colour::first(&feature.props) {
        Some(Colour::Red) => Sprite::BOYLAT24,
        Some(Colour::Green) => Sprite::BOYLAT23,
        _ => Sprite::BOYSPP25,
    });
}

pub fn circle(feature: &mut ChartFeature) {
    feature.point_symbol(match Colour::first(&feature.props) {
        Some(Colour::Red) => Sprite::BOYSAW12,
        _ => Sprite::BOYSPP11,
    });
}

/// Pillar and spar buoys get the tall beacon symbols.
pub fn stake(feature: &mut ChartFeature) {
    feature.point_symbol(match Colour::first(&feature.props) {
        Some(Colour::Red) => Sprite::BCNLAT21,
        Some(Colour::Green) => Sprite::BCNLAT22,
        Some(Colour::Black) => Sprite::BCNSAW21,
        _ => Sprite::BCNSPP21,
    });
}

/// The symbol by BOYSHP, BOYDEF03 without one.
pub fn boyshp(feature: &mut ChartFeature) {
    match Boyshp::of(&feature.props) {
        Some(Boyshp::CONICAL) => half_triangle(feature),
        Some(Boyshp::CAN) => rhomboid(feature),
        Some(Boyshp::SPHERICAL) => circle(feature),
        Some(Boyshp::PILLAR | Boyshp::SPAR) => stake(feature),
        Some(Boyshp::BARREL | Boyshp::SUPERBUOY | Boyshp::ICEBUOY) => circle(feature),
        None => feature.point_symbol(Sprite::BOYDEF03),
    }
}

impl Layerable for Boylat {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Catlam::of(&feature.props) {
            Some(Catlam::PORT_HAND_LATERAL_MARK) => rhomboid(feature),
            Some(Catlam::STARBOARD_HAND_LATERAL_MARK) => half_triangle(feature),
            Some(Catlam::PREFERRED_CHANNEL_TO_STARBOARD_LATERAL_MARK) => rhomboid(feature),
            Some(Catlam::PREFERRED_CHANNEL_TO_PORT_LATERAL_MARK) => half_triangle(feature),
            None => boyshp(feature),
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

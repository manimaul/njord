//! Geometry Primitives: Point, Area
//!
//! Object: Building, single
//!
//! Acronym: BUISGL
//!
//! Code: 12

use crate::layers::lndare::Lndare;
use crate::layers::prelude::*;

/// `class Buisgl : Lndare()` with `areaColor = LANDF`.
const LNDARE: Lndare = Lndare {
    area_color: Color::LANDF,
};

#[derive(Clone, Copy, Debug, Default)]
pub struct Buisgl;

impl Layerable for Buisgl {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        LNDARE.pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        LNDARE.layers(b)
    }
}

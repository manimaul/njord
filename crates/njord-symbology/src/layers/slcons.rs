//! Geometry Primitives: Point, Line, Area
//!
//! Object: Shoreline Construction
//!
//! Acronym: SLCONS
//!
//! Code: 122

use crate::layers::lndare::Lndare;
use crate::layers::prelude::*;

/// `class Slcons : Lndare()`.
#[derive(Clone, Copy, Debug, Default)]
pub struct Slcons;

impl Layerable for Slcons {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        Lndare::default().pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        Lndare::default().layers(b)
    }
}

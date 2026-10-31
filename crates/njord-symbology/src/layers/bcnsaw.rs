//! Geometry Primitives: Point
//!
//! Object: Beacon, safe water
//!
//! Acronym: BCNSAW
//!
//! Code: 8

use crate::layers::bcnlat;
use crate::layers::prelude::*;

/// `class Bcnsaw : Bcnlat()`.
#[derive(Clone, Copy, Debug, Default)]
pub struct Bcnsaw;

impl Layerable for Bcnsaw {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        bcnlat::bcnshp(feature);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

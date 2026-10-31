//! Geometry Primitives: Line, Area
//!
//! Object: River
//!
//! Acronym: RIVERS
//!
//! Code: 114

use crate::layers::canals::Canals;
use crate::layers::prelude::*;

/// `class Rivers : Canals()`.
#[derive(Clone, Copy, Debug, Default)]
pub struct Rivers;

impl Layerable for Rivers {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        Canals::default().pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        Canals::default().layers(b)
    }
}

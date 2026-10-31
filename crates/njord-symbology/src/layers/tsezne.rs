//! Geometry Primitives: Area
//!
//! Object: Traffic Separation Zone
//!
//! Acronym: TSEZNE
//!
//! Code: 150

use crate::layers::prelude::*;

const AC: Color = Color::TRFCF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Tsezne;

impl Layerable for Tsezne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AC);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.area_layer_with_fill_color(AC, Some(0.25))]
    }
}

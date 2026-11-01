//! Natural Earth base layer: lakes: `Lakare` on the `lakes` source layer.

use crate::layers::lakare::Lakare;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lakes;

impl Layerable for Lakes {
    fn source_layer(&self) -> Option<&str> {
        Some("lakes")
    }

    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        Lakare.pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        Lakare.layers(b)
    }
}

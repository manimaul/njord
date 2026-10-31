//! Natural Earth base layer: minor islands (`base/MinorIslands.kt`): `Lndare` on the `minor_islands` source layer.

use crate::layers::lndare::Lndare;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct MinorIslands;

impl Layerable for MinorIslands {
    fn source_layer(&self) -> Option<&str> {
        Some("minor_islands")
    }

    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        Lndare::default().pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        Lndare::default().layers(b)
    }
}

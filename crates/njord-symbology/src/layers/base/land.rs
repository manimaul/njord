//! Natural Earth base layer: land polygons (`base/Land.kt`): `Lndare` on the `land` source layer.

use crate::layers::lndare::Lndare;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Land;

impl Layerable for Land {
    fn source_layer(&self) -> Option<&str> {
        Some("land")
    }

    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        Lndare::default().pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        Lndare::default().layers(b)
    }
}

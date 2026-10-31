//! Natural Earth base layer: rivers and lake centerlines (`base/RiversLakeCenterlines.kt`): `Canals` on the `rivers_lake_centerlines` source layer.

use crate::layers::canals::Canals;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct RiversLakeCenterlines;

impl Layerable for RiversLakeCenterlines {
    fn source_layer(&self) -> Option<&str> {
        Some("rivers_lake_centerlines")
    }

    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        Canals::default().pre_tile_encode(feature, cx);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        Canals::default().layers(b)
    }
}

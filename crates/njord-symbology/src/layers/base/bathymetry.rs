//! Natural Earth base layer: bathymetry depth area polygons (`base/Bathymetry.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Bathymetry;

impl Layerable for Bathymetry {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.area_layer_with_fill_color(Color::DEPMD, None)]
    }
}

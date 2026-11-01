//! Natural Earth base layer: ocean polygon fill.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ocean;

impl Layerable for Ocean {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.area_layer_with_fill_color(Color::DEPDW, None)]
    }
}

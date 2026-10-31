//! Natural Earth base layer: coastline (`base/Coastline.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Coastline;

impl Layerable for Coastline {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            Color::CSTLN,
            LineColorOpts {
                width: 1.0,
                ..Default::default()
            },
        )]
    }
}

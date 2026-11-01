//! Natural Earth base layer: playas (dry lake beds).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Playas;

impl Layerable for Playas {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::LANDA, None),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 0.5,
                    ..Default::default()
                },
            ),
        ]
    }
}

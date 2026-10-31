//! Acronym: PONTON (the Kotlin class has no header comment).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ponton;

impl Layerable for Ponton {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::CHBRN, None),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 2.0,
                    ..Default::default()
                },
            ),
        ]
    }
}

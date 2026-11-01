//! Natural Earth base layer: antarctic ice shelf polygons.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct AntarcticIceShelves;

impl Layerable for AntarcticIceShelves {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::CHWHT, None),
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

//! Acronym: COALNE (the Kotlin class has no header comment).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Coalne;

impl Layerable for Coalne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(Color::CSTLN);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            Color::CSTLN,
            LineColorOpts {
                width: 1.0,
                style: LineStyle::CustomDash(5.0, 5.0),
                ..Default::default()
            },
        )]
    }
}

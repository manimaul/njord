//! Geometry Primitives: Line
//!
//! Object: Traffic Separation Line
//!
//! Acronym: TSELNE
//!
//! Code: 145

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::TRFCF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Tselne;

impl Layerable for Tselne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                width: 6.0,
                ..Default::default()
            },
        )]
    }
}

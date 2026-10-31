//! Geometry Primitives: Area
//!
//! Object: Traffic separation scheme roundabout
//!
//! Acronym: TSSRON
//!
//! Code: 150

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::TRFCF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Tssron;

impl Layerable for Tssron {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                style: LineStyle::CustomDash(3.0, 2.0),
                ..Default::default()
            },
        )]
    }
}

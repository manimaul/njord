//! Geometry Primitives: Area
//!
//! Object: Fairway
//!
//! Acronym: FAIRWY
//!
//! Code: 51

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHBLK;

#[derive(Clone, Copy, Debug, Default)]
pub struct Fairwy;

impl Layerable for Fairwy {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                width: 0.5,
                style: LineStyle::CustomDash(10.0, 5.0),
                ..Default::default()
            },
        )]
    }
}

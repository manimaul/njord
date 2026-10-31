//! Geometry Primitives: Area
//!
//! Object: Swept area
//!
//! Acronym: SWPARE
//!
//! Code: 141

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHBLK;

#[derive(Clone, Copy, Debug, Default)]
pub struct Swpare;

impl Layerable for Swpare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                style: LineStyle::DashLine,
                ..Default::default()
            },
        )]
    }
}

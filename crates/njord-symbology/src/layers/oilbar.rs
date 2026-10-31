//! Geometry Primitives: Line
//!
//! Object: Oil barrier
//!
//! Acronym: OILBAR
//!
//! Code: 89

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Oilbar;

const LINE_COLOR: Color = Color::CHBLK;

impl Layerable for Oilbar {
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

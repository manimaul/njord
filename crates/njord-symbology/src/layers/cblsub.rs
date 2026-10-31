//! Geometry Primitives: Line
//!
//! Object: Cable, submarine
//!
//! Acronym: CBLSUB
//!
//! Code: 22

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGD;

#[derive(Clone, Copy, Debug, Default)]
pub struct Cblsub;

impl Layerable for Cblsub {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                width: 1.0,
                style: LineStyle::DashLine,
                ..Default::default()
            },
        )]
    }
}

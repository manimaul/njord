//! Geometry Primitives: Line, Area
//!
//! Object: Lake shore
//!
//! Acronym: LAKSHR
//!
//! Code: 70

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lakshr;

const LINE_COLOR: Color = Color::CSTLN;

impl Layerable for Lakshr {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                style: LineStyle::Solid,
                width: 1.0,
                ..Default::default()
            },
        )]
    }
}

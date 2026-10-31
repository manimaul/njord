//! Geometry Primitives: Area
//!
//! Object: Inshore traffic zone
//!
//! Acronym: ISTZNE
//!
//! Code: 68

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Istzne;

const LINE_COLOR: Color = Color::TRFCD;

impl Layerable for Istzne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            LINE_COLOR,
            LineColorOpts {
                style: LineStyle::DashLine,
                width: 1.0,
                ..Default::default()
            },
        )]
    }
}

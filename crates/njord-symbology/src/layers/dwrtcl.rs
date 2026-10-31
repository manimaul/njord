//! Geometry Primitives: Line
//!
//! Object: Deep water route centerline
//!
//! Acronym: DWRTCL
//!
//! Code: 40

use crate::layers::bearing::{bearing_label, bearing_label_layer};
use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHBLK;

#[derive(Clone, Copy, Debug, Default)]
pub struct Dwrtcl;

impl Layerable for Dwrtcl {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        bearing_label(feature);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(LINE_COLOR, LineColorOpts::default()),
            bearing_label_layer(b),
        ]
    }
}

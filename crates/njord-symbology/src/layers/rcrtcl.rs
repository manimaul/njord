//! Geometry Primitives: Line
//!
//! Object: Recommended track center line
//!
//! Acronym: RCRTCL
//!
//! Code: 107

use crate::layers::bearing::{bearing_label, bearing_label_layer};
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Rcrtcl;

impl Layerable for Rcrtcl {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        bearing_label(feature);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(Color::PLRTE, LineColorOpts::default()),
            bearing_label_layer(b),
        ]
    }
}

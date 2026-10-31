//! Geometry Primitives: Line
//!
//! Object: Radar line
//!
//! Acronym: RADLNE
//!
//! Code: 99

use crate::layers::bearing::{bearing_label, bearing_label_layer};
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Radlne;

impl Layerable for Radlne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(Color::TRFCD);
        bearing_label(feature);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::TRFCD,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            bearing_label_layer(b),
        ]
    }
}

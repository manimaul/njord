//! Geometry Primitives: Line
//!
//! Object: Navigation line
//!
//! Acronym: NAVLNE
//!
//! Code: 85

use crate::layers::bearing::{bearing_label, bearing_label_layer};
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Navlne;

const LINE_COLOR: Color = Color::CHGRD;

impl Layerable for Navlne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        bearing_label(feature);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 1.0,
                    style: LineStyle::CustomDash(10.0, 5.0),
                    ..Default::default()
                },
            ),
            bearing_label_layer(b),
        ]
    }
}

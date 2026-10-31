//! Geometry Primitives: Area
//!
//! Object: Radar range
//!
//! Acronym: RADRNG
//!
//! Code: 100

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Radrng;

impl Layerable for Radrng {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(Color::TRFCF);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            Color::TRFCF,
            LineColorOpts {
                width: 1.0,
                style: LineStyle::DashLine,
                ..Default::default()
            },
        )]
    }
}

//! Geometry Primitives:  Area
//!
//! Object: Lake
//!
//! Acronym: LAKARE
//!
//! Code: 69

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lakare;

impl Layerable for Lakare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(Color::CSTLN);
        feature.area_color(Color::DEPDW);
        feature.line_color(Color::CSTLN);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::DEPDW, None),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    style: LineStyle::Solid,
                    width: 1.0,
                    ..Default::default()
                },
            ),
        ]
    }
}

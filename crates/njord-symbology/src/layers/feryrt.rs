//! Geometry Primitives: Line, Area
//!
//! Object: Ferry route
//!
//! Acronym: FERYRT
//!
//! Code: 53

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHBLK;

#[derive(Clone, Copy, Debug, Default)]
pub struct Feryrt;

impl Layerable for Feryrt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        feature.line_pattern(Sprite::FRYARE51);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::CustomDash(6.0, 2.0),
                    ..Default::default()
                },
            ),
            b.line_layer_with_pattern(LinePatternOpts {
                symbol: Some(Sprite::FRYARE51),
                ..Default::default()
            }),
        ]
    }
}

//! Geometry Primitives: Area
//!
//! Object: Dock area
//!
//! Acronym: DOCARE
//!
//! Code: 45

use crate::layers::prelude::*;

const AREA_COLOR: Color = Color::DEPVS;
const LINE_COLOR: Color = Color::CHGRD;

#[derive(Clone, Copy, Debug, Default)]
pub struct Docare;

impl Layerable for Docare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 0.5,
                    ..Default::default()
                },
            ),
        ]
    }
}

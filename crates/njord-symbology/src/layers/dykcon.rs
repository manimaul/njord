//! Geometry Primitives: Line, Area
//!
//! Object: Dyke
//!
//! Acronym: DYKCON
//!
//! Code: 49

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::LANDF;
const AREA_COLOR: Color = Color::CHBRN;

#[derive(Clone, Copy, Debug, Default)]
pub struct Dykcon;

impl Layerable for Dykcon {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(LINE_COLOR, LineColorOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
        ]
    }
}

//! Geometry Primitives: Line, Area
//!
//! Object: Floating dock
//!
//! Acronym: FLODOC
//!
//! Code: 57

use crate::layers::prelude::*;

const AREA_COLOR: Color = Color::CHBRN;
const LINE_COLOR: Color = Color::CSTLN;

#[derive(Clone, Copy, Debug, Default)]
pub struct Flodoc;

impl Layerable for Flodoc {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.line_layer_with_color(LINE_COLOR, LineColorOpts::default()),
        ]
    }
}

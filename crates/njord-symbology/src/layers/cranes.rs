//! Geometry Primitives: Point, Area
//!
//! Object: Crane
//!
//! Acronym: CRANES
//!
//! Code: 35

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHBLK;
const AREA_COLOR: Color = Color::CHBRN;

#[derive(Clone, Copy, Debug, Default)]
pub struct Cranes;

impl Layerable for Cranes {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CRANES01);
        feature.line_color(LINE_COLOR);
        feature.area_color(AREA_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts::default()),
            b.line_layer_with_color(LINE_COLOR, LineColorOpts::default()),
        ]
    }
}

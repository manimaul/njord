//! Geometry Primitives: Point, Line, Area
//!
//! Object: Runway
//!
//! Acronym: RUNWAY
//!
//! Code: 117

use crate::layers::prelude::*;

const AREA_COLOR: Color = Color::CHBRN;
const LINE_COLOR: Color = Color::CHBLK;
const SYMBOL: Sprite = Sprite::AIRARE02;

#[derive(Clone, Copy, Debug, Default)]
pub struct Runway;

impl Layerable for Runway {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        feature.area_color(AREA_COLOR);
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(LINE_COLOR, LineColorOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                ..Default::default()
            }),
        ]
    }
}

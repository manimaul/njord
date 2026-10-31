//! Geometry Primitives: Area
//!
//! Object: Fishing ground
//!
//! Acronym: FSHGRD
//!
//! Code: 56

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Fshgrd;

const LINE_COLOR: Color = Color::CHMGD;

impl Layerable for Fshgrd {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::FSHGRD01);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(LINE_COLOR, LineColorOpts::default()),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts::default()),
        ]
    }
}

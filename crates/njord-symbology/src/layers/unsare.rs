//! Geometry Primitives: Area
//!
//! Object: Unsurveyed area
//!
//! Acronym: UNSARE
//!
//! Code: 154

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Unsare;

impl Layerable for Unsare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(Color::NODTA);
        feature.area_pattern(Sprite::NODATA03P);
        feature.line_color(Color::CHGRD);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::NODTA, None),
            b.line_layer_with_color(Color::CHGRD, LineColorOpts::default()),
            b.area_layer_with_fill_pattern(Some(Sprite::NODATA03P)),
        ]
    }
}

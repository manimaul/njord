//! Geometry Primitives: Point, Line, Area
//!
//! Object: Sand waves
//!
//! Acronym: SNDWAV
//!
//! Code: 118

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Sndwav;

impl Layerable for Sndwav {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(Color::CHGRD);
        feature.area_pattern(Sprite::SNDWAV01P);
        feature.point_symbol(Sprite::SNDWAV02);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts::default()),
        ]
    }
}

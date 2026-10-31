//! Geometry Primitives: Point, Area
//!
//! Object: Airport / airfield
//!
//! Acronym: AIRARE
//!
//! Code: 2

use crate::layers::prelude::*;

const AREA_COLOR: Color = Color::LANDA;
const LINE_COLOR: Color = Color::LANDF;
const SYMBOL: Sprite = Sprite::AIRARE02;

#[derive(Clone, Copy, Debug, Default)]
pub struct Airare;

impl Layerable for Airare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                symbol: Some(SYMBOL),
                ..Default::default()
            }),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 1.0,
                    ..Default::default()
                },
            ),
        ]
    }
}

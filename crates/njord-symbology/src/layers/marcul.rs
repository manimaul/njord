//! Geometry Primitives: Point, Line, Area
//!
//! Object: Marine farm/culture
//!
//! Acronym: MARCUL
//!
//! Code: 82

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Marcul;

const LINE_COLOR: Color = Color::CHGRD;
const SYMBOL: Sprite = Sprite::MARCUL02;

impl Layerable for Marcul {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        feature.area_pattern(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    width: 2.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                ..Default::default()
            }),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts {
                symbol: Some(SYMBOL),
                ..Default::default()
            }),
        ]
    }
}

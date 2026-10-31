//! Geometry Primitives: Point, Area
//!
//! Object: Anchorage area
//!
//! Acronym: ACHARE
//!
//! Code: 4

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Achare;

impl Layerable for Achare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::ACHARE02);
        feature.line_pattern(Sprite::ACHARE02);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 2.0,
                    style: LineStyle::CustomDash(3.0, 4.0),
                    ..Default::default()
                },
            ),
            b.line_layer_with_pattern(LinePatternOpts::default()),
            b.line_layer_with_label(&Label::Property("OBJNAM".into()), LineLabelOpts::default()),
        ]
    }
}

//! Geometry Primitives: Point, Area
//!
//! Object: Cargo transshipment area
//!
//! Acronym: CTSARE
//!
//! Code: 25

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ctsare;

impl Layerable for Ctsare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CHINFO07);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
        ]
    }
}

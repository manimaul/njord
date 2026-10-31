//! Geometry Primitives: Point, Area
//!
//! Object: Military practice area
//!
//! Acronym: MIPARE
//!
//! Code: 83

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Mipare;

const LINE_COLOR: Color = Color::CHMGD;

impl Layerable for Mipare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CHINFO06);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
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

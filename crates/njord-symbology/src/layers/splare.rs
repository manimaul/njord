//! Geometry Primitives: Area
//!
//! Object: Spoil area
//!
//! Acronym: SPLARE
//!
//! Code: 131

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGD;

#[derive(Clone, Copy, Debug, Default)]
pub struct Splare;

impl Layerable for Splare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CHINFO07);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
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

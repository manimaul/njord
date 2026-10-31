//! Geometry Primitives: Area
//!
//! Object: Cable area
//!
//! Acronym: CBLARE
//!
//! Code: 20

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGD;

#[derive(Clone, Copy, Debug, Default)]
pub struct Cblare;

impl Layerable for Cblare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CBLARE51);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::CustomDash(3.0, 2.0),
                    ..Default::default()
                },
            ),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts {
                // give room for resare
                icon_offset: Some(vec![30.0, 30.0]),
                anchor: Anchor::Center,
                icon_rotation_alignment: IconRotationAlignment::Map,
                ..Default::default()
            }),
        ]
    }
}

//! Geometry Primitives: Area
//!
//! Object: Two-way route part
//!
//! Acronym: TWRTPT
//!
//! Code: 152

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::TRFCF;

#[derive(Clone, Copy, Debug, Default)]
pub struct Twrtpt;

impl Layerable for Twrtpt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Trafic::of(&feature.props) {
            Some(Trafic::INBOUND | Trafic::OUTBOUND | Trafic::ONE_WAY) => {
                feature.point_symbol(Sprite::TWRTPT53)
            }
            Some(Trafic::TWO_WAY) => feature.point_symbol(Sprite::TWRTPT52),
            None => feature.point_symbol(Sprite::TWRDEF51),
        }
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 2.0,
                    style: LineStyle::CustomDash(3.0, 2.0),
                    ..Default::default()
                },
            ),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                icon_rotate: Some(IconRot::Property("ORIENT".into())),
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                icon_allow_overlap: false,
                ..Default::default()
            }),
        ]
    }
}

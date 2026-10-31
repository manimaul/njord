//! Geometry Primitives: Point, Area
//!
//! Object: Recommended Traffic Lane Part
//!
//! Acronym: RCTLPT
//!
//! Code: 110

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Rctlpt;

impl Layerable for Rctlpt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if float_value(&feature.props, "ORIENT").is_some() {
            feature.point_symbol(Sprite::RCTLPT52);
        } else {
            feature.point_symbol(Sprite::RTLDEF51);
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                icon_rotate: Some(IconRot::Property("ORIENT".into())),
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                icon_allow_overlap: false,
                ..Default::default()
            }),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                icon_rotate: Some(IconRot::Property("ORIENT".into())),
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                icon_allow_overlap: false,
                ..Default::default()
            }),
        ]
    }
}

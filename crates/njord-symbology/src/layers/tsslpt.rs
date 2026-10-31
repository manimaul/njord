//! Geometry Primitives: Area
//!
//! Object: Traffic Separation Scheme Lane part
//!
//! Acronym: TSSLPT
//!
//! Code: 148

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Tsslpt;

impl Layerable for Tsslpt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if float_value(&feature.props, "ORIENT").is_some() {
            feature.point_symbol(Sprite::RCTLPT52);
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.area_layer_with_point_symbol(AreaPointSymbolOpts {
            icon_rotate: Some(IconRot::Property("ORIENT".into())),
            icon_rotation_alignment: Some(IconRotationAlignment::Map),
            icon_allow_overlap: false,
            ..Default::default()
        })]
    }
}

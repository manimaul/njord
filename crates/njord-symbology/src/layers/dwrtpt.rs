//! Geometry Primitives: Area
//!
//! Object: Deep water route part
//!
//! Acronym: DWRTPT
//!
//! Code: 41

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Dwrtpt;

impl Layerable for Dwrtpt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if float_value(&feature.props, "ORIENT").is_some() {
            match Trafic::of(&feature.props) {
                Some(Trafic::INBOUND | Trafic::OUTBOUND | Trafic::ONE_WAY) => {
                    feature.point_symbol(Sprite::TSSLPT51)
                }
                Some(Trafic::TWO_WAY) => feature.point_symbol(Sprite::DWRUTE51),
                None => {}
            }
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

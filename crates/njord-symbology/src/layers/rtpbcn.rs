//! Geometry Primitives: Point
//!
//! Object: Radar transponder beacon
//!
//! Acronym: RTPBCN
//!
//! Code: 103

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Rtpbcn;

impl Layerable for Rtpbcn {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::RTPBCN02);
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
        ]
    }
}

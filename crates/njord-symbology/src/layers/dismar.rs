//! <https://openenc.com/control/symbols/DISMAR>
//!
//! Geometry Primitives: Point
//!
//! Object: Distance mark
//!
//! Acronym: DISMAR
//!
//! Code: 44

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Dismar;

impl Layerable for Dismar {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::DISMAR06);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            anchor: Anchor::Center,
            icon_rotation_alignment: Some(IconRotationAlignment::Map),
            ..Default::default()
        })]
    }
}

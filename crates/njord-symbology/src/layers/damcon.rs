//! Geometry Primitives: Point, Line, Area
//!
//! Object: Dam
//!
//! Acronym: DAMCON
//!
//! Code: 38

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Damcon;

impl Layerable for Damcon {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CHINFO06);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.line_layer_with_color(Color::CSTLN, LineColorOpts::default()),
            b.area_layer_with_fill_color(Color::LANDF, None),
        ]
    }
}

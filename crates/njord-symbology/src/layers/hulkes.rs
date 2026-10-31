//! Geometry Primitives: Point, Area
//!
//! Object: Hulk
//!
//! Acronym: HULKES
//!
//! Code: 65

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Hulkes;

impl Layerable for Hulkes {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::HULKES01);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::CHBRN, None),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 2.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(Sprite::HULKES01),
                anchor: Anchor::Center,
                icon_allow_overlap: true,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
        ]
    }
}

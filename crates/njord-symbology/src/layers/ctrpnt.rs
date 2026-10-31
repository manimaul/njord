//! Geometry Primitives: Point
//!
//! Object: Control point
//!
//! Acronym: CTRPNT
//!
//! Code: 33

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ctrpnt;

impl Layerable for Ctrpnt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::POSGEN04);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.point_layer_with_label(
                &Label::Property("OBJNAM".into()),
                PointLabelOpts {
                    text_anchor: Some(Anchor::BottomLeft),
                    text_offset: Some(Offset::Coord(1.0, 0.0)),
                    highlight_color: Color::LANDA,
                    ..Default::default()
                },
            ),
        ]
    }
}

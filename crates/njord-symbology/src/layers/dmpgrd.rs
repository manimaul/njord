//! Geometry Primitives: Point, Area
//!
//! Object: Dumping ground
//!
//! Acronym: DMPGRD
//!
//! Code: 48

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGD;

#[derive(Clone, Copy, Debug, Default)]
pub struct Dmpgrd;

impl Layerable for Dmpgrd {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::CHINFO07);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 1.0,
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
        ]
    }
}

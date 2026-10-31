//! Geometry Primitives: Point, Area
//!
//! Object: Checkpoint
//!
//! Acronym: CHKPNT
//!
//! Code: 28

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Chkpnt;

impl Layerable for Chkpnt {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::POSGEN04);
        feature.line_color(Color::CHBLK);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(Color::CHBLK, LineColorOpts::default()),
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
        ]
    }
}

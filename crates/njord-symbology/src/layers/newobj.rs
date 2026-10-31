//! Geometry Primitives: Point, Line, Area
//!
//! Object: New object
//!
//! Acronym: NEWOBJ
//!
//! Code: 89

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Newobj;

const SYMBOL: Sprite = Sprite::NEWOBJ01;

impl Layerable for Newobj {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(SYMBOL);
        feature.line_color(Color::CHBLK);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                anchor: Anchor::Center,
                ..Default::default()
            }),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                anchor: Anchor::Center,
                ..Default::default()
            }),
            b.line_layer_with_color(Color::CHBLK, LineColorOpts::default()),
        ]
    }
}

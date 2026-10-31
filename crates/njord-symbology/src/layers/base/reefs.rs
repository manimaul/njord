//! Natural Earth base layer: reefs (line geometry) (`base/Reefs.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Reefs;

impl Layerable for Reefs {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_pattern(Sprite::FOULAR01P);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(Sprite::FOULAR01P),
                ..Default::default()
            }),
            b.area_layer_with_fill_pattern(Some(Sprite::FOULAR01P)),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 0.5,
                    ..Default::default()
                },
            ),
        ]
    }
}

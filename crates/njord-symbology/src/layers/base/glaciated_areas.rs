//! Natural Earth base layer: glaciated areas.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct GlaciatedAreas;

impl Layerable for GlaciatedAreas {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::ICEARE04P);
        feature.area_pattern(Sprite::ICEARE04P);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(Sprite::ICEARE04P),
                ..Default::default()
            }),
            b.area_layer_with_fill_pattern(Some(Sprite::ICEARE04P)),
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

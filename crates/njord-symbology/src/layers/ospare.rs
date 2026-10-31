//! Geometry Primitives: Area
//!
//! Object: Offshore production area
//!
//! Acronym: OSPARE
//!
//! Code: 88

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Ospare;

const SYMBOL: Sprite = Sprite::CTYARE51;
const COLOR: Color = Color::CHMGD;

impl Layerable for Ospare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    width: 2.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                ..Default::default()
            }),
        ]
    }
}

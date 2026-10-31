//! Geometry Primitives: Point, Area
//!
//! Object: Incineration area
//!
//! Acronym: ICNARE
//!
//! Code: 67

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Icnare;

const LINE_COLOR: Color = Color::CHMGF;
const SYMBOL: Sprite = Sprite::CHINFO07;

impl Layerable for Icnare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(LINE_COLOR);
        feature.area_pattern(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    width: 1.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts {
                symbol: Some(SYMBOL),
                ..Default::default()
            }),
        ]
    }
}

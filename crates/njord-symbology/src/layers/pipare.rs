//! Geometry Primitives: Point, Area
//!
//! Object: Pipeline area
//!
//! Acronym: PIPARE
//!
//! Code: 92

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Pipare;

const LINE_COLOR: Color = Color::CHMGD;
const SYMBOL: Sprite = Sprite::CHINFO07;

impl Layerable for Pipare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let catpip = Catpip::of(&feature.props);
        let color = if catpip
            .iter()
            .any(|c| matches!(c, Catpip::OUTFALL_PIPE | Catpip::INTAKE_PIPE))
        {
            Color::CHGRD
        } else {
            match Prodct::of(&feature.props).first() {
                Some(Prodct::WATER) => Color::CHGRD,
                Some(_) | None => Color::CHMGD,
            }
        };
        feature.line_color(color);
        feature.point_symbol(SYMBOL);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Sprite(SYMBOL),
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
        ]
    }
}

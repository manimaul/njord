//! Geometry Primitives: Point, Area
//!
//! Object: Production / storage area
//!
//! Acronym: PRDARE
//!
//! Code: 97

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Prdare;

impl Layerable for Prdare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let line_color = match Convis::of(&feature.props) {
            Some(Convis::VISUAL_CONSPICUOUS) => Color::CHBLK,
            Some(Convis::NOT_VISUAL_CONSPICUOUS) | None => Color::LANDF,
        };

        feature.line_color(line_color);

        let symbol = match Catpra::of(&feature.props) {
            Some(Catpra::QUARRY) => Some(Sprite::QUARRY01),
            Some(Catpra::MINE | Catpra::STOCKPILE | Catpra::POWER_STATION_AREA) => {
                Some(Sprite::PRDINS02)
            }
            Some(Catpra::REFINERY_AREA) => Some(Sprite::RFNERY11),
            Some(Catpra::TIMBER_YARD) => Some(Sprite::TMBYRD01),
            Some(Catpra::FACTORY_AREA) => Some(Sprite::PRDINS02),
            Some(Catpra::TANK_FARM) => Some(Sprite::TNKFRM11),
            Some(Catpra::WIND_FARM) => Some(Sprite::WNDFRM61),
            None => None,
        };

        if let Some(symbol) = symbol {
            feature.point_symbol(symbol);
            feature.line_pattern(symbol);
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_colors(
                &[Color::LANDF, Color::CHBLK],
                LineColorsOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            b.line_layer_with_pattern(LinePatternOpts::default()),
        ]
    }
}

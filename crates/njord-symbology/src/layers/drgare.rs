//! Geometry Primitives: Area
//!
//! Object: Dredged area
//!
//! Acronym: DRGARE
//!
//! Code: 46

use crate::layers::prelude::*;

const AREA_FILL_COLORS: [Color; 5] = [
    Color::DEPIT,
    Color::DEPVS,
    Color::DEPMS,
    Color::DEPMD,
    Color::DEPDW,
];

#[derive(Clone, Copy, Debug, Default)]
pub struct Drgare;

impl Layerable for Drgare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        let config = &cx.config;
        let mut ac = Color::DEPVS;
        if let Some(drval1) = float_value(&feature.props, "DRVAL1") {
            ac = if drval1 <= 0.0 {
                Color::DEPIT
            } else if drval1 <= config.shallow_depth {
                Color::DEPVS
            } else if drval1 <= config.safety_depth {
                Color::DEPMS
            } else if drval1 <= config.deep_depth {
                Color::DEPMD
            } else {
                Color::DEPDW
            };
        }
        feature.area_color(ac);
        feature.area_pattern(Sprite::DRGARE01P);
        feature.line_color(Color::CHGRD);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_colors(&AREA_FILL_COLORS),
            b.area_layer_with_fill_pattern(None),
            b.line_layer_with_colors(
                &[Color::CHGRD],
                LineColorsOpts {
                    width: 0.5,
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
        ]
    }
}

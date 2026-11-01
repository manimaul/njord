//! Geometry Primitives: Line, Area
//!
//! Object: Depth area
//!
//! Acronym: DEPARE
//!
//! Code: 42

use crate::layers::prelude::*;

const AREA_FILL_COLORS: &[Color] = &[
    Color::DEPIT,
    Color::DEPVS,
    Color::DEPMS,
    Color::DEPMD,
    Color::DEPDW,
];

#[derive(Clone, Copy, Debug, Default)]
pub struct Depare;

impl Layerable for Depare {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        // DRVAL1 is the lower (shallower, sometimes negative) end of the range, DRVAL2 the deeper.
        vec![
            b.area_layer_with_fill_colors(AREA_FILL_COLORS),
            b.line_layer_with_colors(
                &[Color::CHGRD],
                LineColorsOpts {
                    width: 0.5,
                    ..Default::default()
                },
            ),
        ]
    }

    /// The colour band by DRVAL1 against the configured thresholds (`<=` at each), DEPIT for a
    /// drying area (DRVAL1 < 0 and DRVAL2 <= 0), CHBLK when either bound is missing. Compared in
    /// `f32`, as the thresholds are held ([`SymbologyConfig`](crate::SymbologyConfig)).
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        let config = &cx.config;
        let mut ac = Color::CHBLK;
        if let Some(shallow_range) = float_value(&feature.props, "DRVAL1")
            && let Some(deep_range) = float_value(&feature.props, "DRVAL2")
        {
            ac = if shallow_range < 0.0 && deep_range <= 0.0 {
                Color::DEPIT
            } else if shallow_range <= config.shallow_depth {
                Color::DEPVS
            } else if shallow_range <= config.safety_depth {
                Color::DEPMS
            } else if shallow_range <= config.deep_depth {
                Color::DEPMD
            } else if shallow_range > config.deep_depth {
                Color::DEPDW
            } else {
                panic!("unexpected DRVAL1 {shallow_range}")
            };
        }
        feature.area_color(ac);
        feature.line_color(Color::CHGRD);
    }
}

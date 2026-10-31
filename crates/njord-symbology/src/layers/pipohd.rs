//! Geometry Primitives: Line
//!
//! Object: Pipeline overhead
//!
//! Acronym: PIPOHD
//!
//! Code: 85
//!
//! S-52 lookup: `LS(SOLD,3,CHGRD)`, plus `SY(RACNSP01)` when radar conspicuous (CONRAD 1 or 3).
//! The `clr` text is drawn by [`ClearanceLabel`].
//!
//! [`ClearanceLabel`]: crate::layers::clearance_label::ClearanceLabel

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Pipohd;

impl Layerable for Pipohd {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Conrad::of(&feature.props) {
            Some(Conrad::RADAR_CONSPICUOUS | Conrad::RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR) => {
                feature.line_pattern(Sprite::RACNSP01)
            }
            Some(Conrad::NOT_RADAR_CONSPICUOUS) | None => {}
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    width: 2.0,
                    ..Default::default()
                },
            ),
            b.line_layer_with_pattern(LinePatternOpts {
                include_polygon_lines: false,
                symbol_placement: Placement::LineCenter,
                icon_rotation_alignment: Some(IconRotationAlignment::Viewport),
                icon_allow_overlap: Some(true),
                ..Default::default()
            }),
        ]
    }
}

//! Geometry Primitives: Line, Area
//!
//! Object: Conveyor
//!
//! Acronym: CONVYR
//!
//! Code: 34

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Convyr;

impl Layerable for Convyr {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    width: 0.5,
                    ..Default::default()
                },
            ),
            b.line_layer_with_pattern(LinePatternOpts::default()),
        ]
    }

    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Conrad::of(&feature.props) {
            Some(Conrad::RADAR_CONSPICUOUS | Conrad::RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR) => {
                feature.line_pattern(Sprite::RACNSP01)
            }
            Some(Conrad::NOT_RADAR_CONSPICUOUS) | None => {}
        }
    }
}

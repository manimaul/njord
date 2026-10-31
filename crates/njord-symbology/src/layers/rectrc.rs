//! Geometry Primitives: Line, Area
//!
//! Object: Recommended track
//!
//! Acronym: RECTRC
//!
//! Code: 109

use crate::layers::bearing::{bearing_label, bearing_label_layer};
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Rectrc;

impl Layerable for Rectrc {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        bearing_label(feature);
        let trafic = Trafic::of(&feature.props);
        match Cattrk::of(&feature.props) {
            Some(Cattrk::BASED_ON_A_SYSTEM_OF_MARKS) => match trafic {
                Some(Trafic::INBOUND | Trafic::OUTBOUND | Trafic::ONE_WAY) => {
                    feature.line_pattern(Sprite::RECTRC58)
                }
                Some(Trafic::TWO_WAY) => feature.line_pattern(Sprite::RECTRC56),
                None => feature.line_pattern(Sprite::RECDEF51),
            },
            Some(Cattrk::NOT_BASED_ON_A_SYSTEM_OF_MARKS) | None => match trafic {
                Some(Trafic::INBOUND | Trafic::OUTBOUND | Trafic::ONE_WAY) => {
                    feature.line_pattern(Sprite::RECTRC57)
                }
                Some(Trafic::TWO_WAY) => feature.line_pattern(Sprite::RECTRC55),
                None => feature.line_pattern(Sprite::RECDEF51),
            },
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            // Map rotation alignment lines the icon's x-axis up with the line.
            b.line_layer_with_spaced_pattern(
                false,
                SpacedLinePatternOpts {
                    icon_rotate: Some(90.0),
                    spacing: Some(15.0),
                    ..Default::default()
                },
            ),
            bearing_label_layer(b),
        ]
    }
}

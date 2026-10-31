//! Geometry Primitives: Line
//!
//! Object: Fence/wall
//!
//! Acronym: FNCLNE
//!
//! Code: 52

use crate::layers::prelude::*;

const LINE_COLORS: [Color; 2] = [Color::CHBLK, Color::LANDF];

#[derive(Clone, Copy, Debug, Default)]
pub struct Fnclne;

impl Layerable for Fnclne {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Convis::of(&feature.props) {
            Some(Convis::VISUAL_CONSPICUOUS) => feature.line_color(Color::CHBLK),
            Some(Convis::NOT_VISUAL_CONSPICUOUS) | None => feature.line_color(Color::LANDF),
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_colors(
            &LINE_COLORS,
            LineColorsOpts {
                width: 1.0,
                ..Default::default()
            },
        )]
    }
}

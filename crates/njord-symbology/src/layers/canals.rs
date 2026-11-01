//! Geometry Primitives: Line, Area
//!
//! Object: Canal
//!
//! Acronym: CANALS
//!
//! Code: 23

use crate::layers::prelude::*;

/// Canals, with a configurable fill colour; `Rivers` and the Natural Earth
/// `RiversLakeCenterlines` delegate to it.
#[derive(Clone, Copy, Debug)]
pub struct Canals {
    pub fill_color: Color,
}

impl Default for Canals {
    fn default() -> Self {
        Canals {
            fill_color: Color::DEPVS,
        }
    }
}

impl Layerable for Canals {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.area_color(self.fill_color);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHBLK,
                LineColorOpts {
                    width: 0.5,
                    ..Default::default()
                },
            ),
            b.area_layer_with_fill_color(self.fill_color, None),
        ]
    }
}

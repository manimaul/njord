//! Geometry Primitives: Area
//!
//! Object: Sloping ground
//!
//! Acronym: SLOGRD
//!
//! Code: 127

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Slogrd;

impl Layerable for Slogrd {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::LANDF, None),
            b.line_layer_with_color(Color::CSTLN, LineColorOpts::default()),
        ]
    }
}

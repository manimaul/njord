//! Geometry Primitives: Line
//!
//! Object: Cable, overhead
//!
//! Acronym: CBLOHD

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Cblohd;

impl Layerable for Cblohd {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.line_layer_with_color(
            Color::CHBLK,
            LineColorOpts {
                width: 0.5,
                style: LineStyle::CustomDash(10.0, 5.0),
                ..Default::default()
            },
        )]
    }
}

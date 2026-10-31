//! Geometry Primitives: Point, Area
//!
//! Object: Sea area / named water area
//!
//! Acronym: SEAARE
//!
//! Code: 119

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Seaare;

impl Layerable for Seaare {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.area_layer_with_text(
            &Label::Property("OBJNAM".into()),
            AreaTextOpts {
                text_color: Color::SNDG2,
                halo_color: Color::DEPDW,
                text_justify: TextJustify::Left,
                text_offset: Some(Offset::Coord(4.0, 2.0)),
                text_optional: Some(true),
            },
        )]
    }
}

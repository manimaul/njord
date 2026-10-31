//! Geometry Primitives: Point, Line, Area
//!
//! Object: Land area
//!
//! Acronym: LNDARE
//!
//! Code: 71
//!
//! The land area names, split out of `Lndare` so they are registered with the low-priority
//! labels, late in the draw order (`LndareLabel.kt`).

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct LndareLabel;

impl Layerable for LndareLabel {
    fn source_layer(&self) -> Option<&str> {
        Some("LNDARE")
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.area_layer_with_text(
            &Label::Property("OBJNAM".into()),
            AreaTextOpts {
                halo_color: Color::LANDA,
                text_optional: Some(true),
                ..Default::default()
            },
        )]
    }
}

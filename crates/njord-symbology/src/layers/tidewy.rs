//! Geometry Primitives: Line, Area
//!
//! Object: Tideway
//!
//! Acronym: TIDEWY
//!
//! Code: 147
//!
//! S-52 lookup (area): `LS(DASH,1,CHGRF);TX(OBJNAM,1,2,3,'15110',0,0,CHBLK,25)` - a dashed outline
//! and a centred name, no fill. The presentation library has no line lookup, so a line gets the
//! same treatment.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Tidewy;

impl Layerable for Tidewy {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHGRF,
                LineColorOpts {
                    width: 1.0,
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            b.text_layer(
                json!(["get", "OBJNAM"]),
                TextLayerOpts {
                    filter: Some(json!(["has", "OBJNAM"])),
                    ..Default::default()
                },
            ),
        ]
    }
}

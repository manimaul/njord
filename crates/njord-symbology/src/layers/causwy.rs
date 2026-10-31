//! Geometry Primitives: Line, Area
//!
//! Object: Causeway
//!
//! Acronym: CAUSWY
//!
//! Code: 26

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CSTLN;

const AREA_FILL_COLORS: [Color; 2] = [Color::CHBRN, Color::DEPIT];

#[derive(Clone, Copy, Debug, Default)]
pub struct Causwy;

impl Layerable for Causwy {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Watlev::of(&feature.props) {
            Some(
                Watlev::PARTLY_SUBMERGED_AT_HIGH_WATER
                | Watlev::ALWAYS_UNDER_WATER_SUBMERGED
                | Watlev::AWASH
                | Watlev::SUBJECT_TO_INUNDATION_OR_FLOODING
                | Watlev::COVERS_AND_UNCOVERS,
            ) => feature.area_color(Color::DEPIT),
            Some(Watlev::ALWAYS_DRY | Watlev::FLOATING) | None => feature.area_color(Color::CHBRN),
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_colors(&AREA_FILL_COLORS),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
        ]
    }
}

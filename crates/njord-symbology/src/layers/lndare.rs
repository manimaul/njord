//! Geometry Primitives: Point, Line, Area
//!
//! Object: Land area
//!
//! Acronym: LNDARE
//!
//! Code: 71

use crate::layers::prelude::*;

/// Kotlin's `open class Lndare` with its `open val areaColor`; `Buisgl`, `Slcons` and the
/// Natural Earth `Land` / `MinorIslands` delegate to it.
#[derive(Clone, Copy, Debug)]
pub struct Lndare {
    pub area_color: Color,
}

impl Default for Lndare {
    fn default() -> Self {
        Lndare {
            area_color: Color::LANDA,
        }
    }
}

impl Layerable for Lndare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::LNDARE01);
        feature.area_color(self.area_color);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(self.area_color, None),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 2.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_allow_overlap: true,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
        ]
    }
}

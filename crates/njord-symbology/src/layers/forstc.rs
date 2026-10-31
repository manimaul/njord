//! Geometry Primitives: Point, Line, Area
//!
//! Object: Fortified structure
//!
//! Acronym: FORSTC
//!
//! Code: 59

use crate::layers::prelude::*;

const LINE_COLORS: [Color; 2] = [Color::LANDF, Color::CHBLK];

#[derive(Clone, Copy, Debug, Default)]
pub struct Forstc;

impl Layerable for Forstc {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Convis::of(&feature.props) {
            Some(Convis::VISUAL_CONSPICUOUS) => {
                feature.point_symbol(Sprite::FORSTC11);
                feature.line_color(Color::CHBLK);
                feature.area_color(Color::CHBRN);
            }
            Some(Convis::NOT_VISUAL_CONSPICUOUS) | None => {
                feature.point_symbol(Sprite::FORSTC11);
                feature.line_color(Color::LANDF);
                feature.area_color(Color::CHBRN);
            }
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_color(Color::CHBRN, None),
            b.line_layer_with_colors(&LINE_COLORS, LineColorsOpts::default()),
        ]
    }
}

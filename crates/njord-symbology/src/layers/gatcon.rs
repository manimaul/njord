//! Geometry Primitives: Point, Line, Area
//!
//! Object: Gate
//!
//! Acronym: GATCON
//!
//! Code: 61

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Gatcon;

const AREA_COLOR: Color = Color::CHBRN;
const LINE_COLOR: Color = Color::CSTLN;

impl Layerable for Gatcon {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if let Some(catgat) = Catgat::of(&feature.props) {
            match catgat {
                Catgat::FLOOD_BARRAGE_GATE | Catgat::CAISSON => {
                    feature.point_symbol(Sprite::GATCON04);
                    feature.line_pattern(Sprite::GATCON04);
                }
                Catgat::LOCK_GATE => {
                    feature.point_symbol(Sprite::GATCON03);
                    feature.line_pattern(Sprite::GATCON03);
                }
                Catgat::DYKE_GATE => {}
            }
        }
        feature.area_color(AREA_COLOR);
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.area_layer_with_fill_color(AREA_COLOR, None),
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    width: 2.0,
                    ..Default::default()
                },
            ),
            b.line_layer_with_pattern(LinePatternOpts {
                include_polygon_lines: false,
                symbol_placement: Placement::LineCenter,
                icon_rotation_alignment: Some(IconRotationAlignment::Viewport),
                icon_allow_overlap: Some(true),
                ..Default::default()
            }),
        ]
    }
}

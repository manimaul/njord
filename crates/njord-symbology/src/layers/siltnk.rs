//! Geometry Primitives: Point, Area
//!
//! Object: Silo / tank
//!
//! Acronym: SILTNK
//!
//! Code: 125

use crate::layers::prelude::*;

const AC: Color = Color::CHBRN;
const LINE_COLORS: &[Color] = &[Color::CHBLK, Color::LANDF];

#[derive(Clone, Copy, Debug, Default)]
pub struct Siltnk;

impl Layerable for Siltnk {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let convis = Convis::of(&feature.props);
        let conspicuous = convis == Some(Convis::VISUAL_CONSPICUOUS);
        match Catsil::of(&feature.props) {
            Some(Catsil::SILO_IN_GENERAL) => {
                if conspicuous {
                    feature.point_symbol(Sprite::SILBUI11)
                } else {
                    feature.point_symbol(Sprite::SILBUI01)
                }
            }
            Some(Catsil::TANK_IN_GENERAL) => {
                if conspicuous {
                    feature.point_symbol(Sprite::TNKCON12)
                } else {
                    feature.point_symbol(Sprite::TNKCON02)
                }
            }
            Some(Catsil::GRAIN_ELEVATOR) => {
                if conspicuous {
                    feature.point_symbol(Sprite::TOWERS03)
                } else {
                    feature.point_symbol_n(Sprite::TOWERS01, 2)
                }
            }
            Some(Catsil::WATER_TOWER) => {
                if conspicuous {
                    feature.point_symbol(Sprite::TOWERS02)
                } else {
                    feature.point_symbol_n(Sprite::TOWERS12, 2)
                }
            }
            None => feature.point_symbol(Sprite::SILBUI01),
        }
        if conspicuous {
            feature.line_color(Color::CHBLK);
        } else {
            feature.line_color(Color::LANDF);
        }
        feature.area_color(AC);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(AC, None),
            b.line_layer_with_colors(LINE_COLORS, LineColorsOpts::default()),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Property(None),
                anchor: Anchor::Bottom,
                icon_allow_overlap: true,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Property(Some(2)),
                anchor: Anchor::Bottom,
                icon_allow_overlap: true,
                icon_rotation_alignment: Some(IconRotationAlignment::Viewport),
                ..Default::default()
            }),
        ]
    }
}

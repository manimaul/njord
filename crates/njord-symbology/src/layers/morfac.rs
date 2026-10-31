//! Geometry Primitives: Point, Line, Area
//!
//! Object: Mooring/warping facility
//!
//! Acronym: MORFAC
//!
//! Code: 84

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Morfac;

impl Layerable for Morfac {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Catmor::of(&feature.props) {
            Some(Catmor::DOLPHIN) => feature.point_symbol(Sprite::MORFAC03),
            Some(Catmor::DEVIATION_DOLPHIN) => feature.point_symbol(Sprite::MORFAC04),
            Some(Catmor::BOLLARD) => feature.point_symbol(Sprite::PILPNT02),
            Some(Catmor::POST_OR_PILE) => feature.point_symbol(Sprite::PILPNT02),
            Some(Catmor::MOORING_BUOY) => feature.point_symbol_n(Sprite::BOYMOR11, 2),
            Some(Catmor::TIE_UP_WALL | Catmor::CHAIN_WIRE_CABLE) | None => {
                feature.point_symbol(Sprite::MORFAC03)
            }
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::CHBRN, None),
            b.line_layer_with_color(
                Color::CHBRN,
                LineColorOpts {
                    width: 1.0,
                    ..Default::default()
                },
            ),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 2.0,
                    filter: Some(json!([
                        filters::ALL,
                        filters::eq_type_line_string(),
                        Catmor::TIE_UP_WALL.filter_eq()
                    ])),
                    ..Default::default()
                },
            ),
            b.line_layer_with_color(
                Color::CHMGF,
                LineColorOpts {
                    width: 1.0,
                    style: LineStyle::CustomDash(5.0, 5.0),
                    filter: Some(json!([
                        filters::ALL,
                        filters::eq_type_line_string(),
                        Catmor::CHAIN_WIRE_CABLE.filter_eq()
                    ])),
                    ..Default::default()
                },
            ),
            b.line_layer_with_color(
                Color::CSTLN,
                LineColorOpts {
                    width: 2.0,
                    filter: Some(json!([
                        filters::ALL,
                        filters::eq_type_line_string(),
                        Catmor::CHAIN_WIRE_CABLE.filter_not_eq(),
                        Catmor::TIE_UP_WALL.filter_not_eq()
                    ])),
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Property(None),
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.point_layer_from_symbol(PointSymbolOpts {
                symbol: Symbol::Property(Some(2)),
                icon_rotation_alignment: Some(IconRotationAlignment::Viewport),
                ..Default::default()
            }),
        ]
    }
}

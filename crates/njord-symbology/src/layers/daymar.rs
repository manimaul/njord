//! Geometry Primitives: Point
//!
//! Object: Daymark
//!
//! Acronym: DAYMAR
//!
//! Code: 39

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Daymar;

impl Layerable for Daymar {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(match Topshp::of(&feature.props) {
            Some(Topshp::SQUARE | Topshp::RECTANGLE_HORIZONTAL | Topshp::RECTANGLE_VERTICAL) => {
                Sprite::DAYSQR01
            }
            Some(Topshp::TRIANGLE_POINT_UP) => Sprite::DAYTRI01,
            Some(Topshp::TRIANGLE_POINT_DOWN) => Sprite::DAYTRI05,
            _ => Sprite::DAYSQR01,
        });
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            icon_offset: Some(Offset::Coord(0.0, -18.0)),
            ..Default::default()
        })]
    }
}

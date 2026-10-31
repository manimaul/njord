//! Geometry Primitives: Point, Line, Area
//!
//! Object: Fishing facility
//!
//! Acronym: FSHFAC
//!
//! Code: 55

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Fshfac;

impl Layerable for Fshfac {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.line_color(Color::CHGRD);
        match Catfif::of(&feature.props) {
            Some(Catfif::FISHING_STAKE) => {
                feature.point_symbol(Sprite::FSHFAC03);
                feature.area_pattern(Sprite::FSHFAC03);
            }
            Some(Catfif::FISH_TRAP | Catfif::FISH_WEIR | Catfif::TUNNY_NET) => {
                feature.point_symbol(Sprite::FSHFAC02);
                feature.area_pattern(Sprite::FSHFAC04P);
            }
            None => {
                feature.point_symbol(Sprite::FSHFAC02);
                feature.area_pattern(Sprite::FSHHAV02P);
            }
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    width: 1.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts::default()),
        ]
    }
}

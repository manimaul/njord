//! Geometry Primitives: Point, Line, Area
//!
//! Object: Berth
//!
//! Acronym: BERTHS
//!
//! Code: 10

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Berths;

impl Layerable for Berths {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::BRTHNO01);
        feature.line_color(Color::CHMGD);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        let label = Label::Property("OBJNAM".into());
        vec![
            b.area_layer_with_point_symbol(AreaPointSymbolOpts::default()),
            b.point_layer_from_symbol(PointSymbolOpts::default()),
            b.line_layer_with_color(
                Color::CHMGD,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
            b.area_layer_with_text(
                &label,
                AreaTextOpts {
                    text_color: Color::CHBLK,
                    halo_color: Color::CHWHT,
                    ..Default::default()
                },
            ),
            b.point_layer_with_label(
                &label,
                PointLabelOpts {
                    label_color: Color::CHBLK,
                    highlight_color: Color::CHWHT,
                    ..Default::default()
                },
            ),
        ]
    }
}

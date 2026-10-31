//! Geometry Primitives: Point, Area
//!
//! Object: Land region
//!
//! Acronym: LNDRGN
//!
//! Code: 73

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lndrgn;

impl Layerable for Lndrgn {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        feature.point_symbol(Sprite::POSGEN04);
        if Catlnd::of(&feature.props)
            .iter()
            .any(|c| matches!(c, Catlnd::SWAMP | Catlnd::MARSH))
        {
            feature.area_pattern(Sprite::MARSHES1P);
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.area_layer_with_fill_pattern(None),
            b.area_layer_with_text(
                &Label::Property("OBJNAM".into()),
                AreaTextOpts {
                    text_color: Color::CHBLK,
                    halo_color: Color::CHWHT,
                    ..Default::default()
                },
            ),
        ]
    }
}

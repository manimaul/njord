//! Geometry Primitives: Area
//!
//! Object: Restricted area
//!
//! Acronym: RESARE
//!
//! Code: 112

use crate::layers::prelude::*;

const LINE_COLOR: Color = Color::CHMGD;

#[derive(Clone, Copy, Debug, Default)]
pub struct Resare;

impl Layerable for Resare {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let restrictions = Restrn::of(&feature.props);
        if restrictions
            .iter()
            .any(|it| matches!(it, Restrn::ENTRY_RESTRICTED | Restrn::ENTRY_PROHIBITED))
        {
            feature.point_symbol(Sprite::ENTRES51);
        }
        if restrictions.iter().any(|it| {
            matches!(
                it,
                Restrn::ANCHORING_PROHIBITED | Restrn::ANCHORING_RESTRICTED
            )
        }) {
            feature.point_symbol(Sprite::ACHRES51);
        }
        feature.line_color(LINE_COLOR);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                LINE_COLOR,
                LineColorOpts {
                    style: LineStyle::CustomDash(3.0, 2.0),
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
        ]
    }
}

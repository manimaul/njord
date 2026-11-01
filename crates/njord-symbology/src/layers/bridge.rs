//! Acronym: BRIDGE (bridge).

use crate::layers::clearance_label::opening_bridge;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Bridge;

impl Layerable for Bridge {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let opening = Catbrg::of(&feature.props).iter().any(|c| {
            matches!(
                c,
                Catbrg::OPENING_BRIDGE
                    | Catbrg::SWING_BRIDGE
                    | Catbrg::LIFTING_BRIDGE
                    | Catbrg::BASCULE_BRIDGE
                    | Catbrg::DRAW_BRIDGE
                    | Catbrg::TRANSPORTER_BRIDGE
            )
        });
        if opening {
            feature.point_symbol(Sprite::BRIDGE01);
            feature.line_pattern(Sprite::BRIDGE01);
            opening_bridge(feature);
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    width: 4.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.line_layer_with_pattern(LinePatternOpts::default()),
        ]
    }
}

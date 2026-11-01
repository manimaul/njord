//! Geometry Primitives: Point
//!
//! Object: Current - non - gravitational
//!
//! Acronym: CURENT
//!
//! Code: 36

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Curent;

impl Layerable for Curent {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if float_value(&feature.props, "ORIENT").is_some() {
            feature.point_symbol(Sprite::CURENT01);
        } else {
            feature.point_symbol(Sprite::CURDEF01);
        }

        let mut point_label = string_value(&feature.props, "OBJNAM").unwrap_or_default();
        if let Some(current_velocity) = float_value(&feature.props, "CURVEL").filter(|v| *v > 0.0) {
            if !point_label.is_empty() {
                point_label.push(' ');
            }
            point_label.push_str(&format!("{} kts (max)", float_to_string(current_velocity)));
        }
        if !point_label.trim().is_empty() {
            feature.props.insert("_PL".into(), point_label.into());
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                icon_rotate: Some(IconRot::Property("ORIENT".into())),
                ..Default::default()
            }),
            b.point_layer_with_label(
                &Label::Property("INFORM".into()),
                PointLabelOpts {
                    text_justify: TextJustify::Left,
                    text_anchor: Some(Anchor::BottomLeft),
                    text_offset: Some(Offset::Coord(2.0, 0.0)),
                    ..Default::default()
                },
            ),
            b.point_layer_with_label(
                &Label::Property("_PL".into()),
                PointLabelOpts {
                    text_anchor: Some(Anchor::BottomRight),
                    text_justify: TextJustify::Right,
                    text_offset: Some(Offset::Coord(-2.0, 0.0)),
                    ..Default::default()
                },
            ),
        ]
    }
}

/// The shortest `f32` decimal, printed by [`double_to_string`] (`1.0`, not `1`).
fn float_to_string(x: f32) -> String {
    double_to_string(format!("{x}").parse().unwrap())
}

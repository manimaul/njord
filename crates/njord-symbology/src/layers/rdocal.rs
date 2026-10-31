//! Geometry Primitives: Point, Line
//!
//! Object: Radio calling-in point
//!
//! Acronym: RDOCAL
//!
//! Code: 104

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Rdocal;

impl Layerable for Rdocal {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if feature.props.contains_key("ORIENT") && Trafic::of(&feature.props).is_some() {
            feature.point_symbol(Sprite::RDOCAL02);
        } else {
            feature.point_symbol(Sprite::RCLDEF01);
        }
        // S-52: TE('Nr %s','OBJNAM',...,1,-1,...) above TE('ch %s','COMCHA',...,1,1,...), both
        // left justified one text body right of the symbol.
        let lines: Vec<String> = [
            string_value(&feature.props, "OBJNAM").map(|it| format!("Nr {it}")),
            string_value(&feature.props, "COMCHA").map(|it| format!("ch {it}")),
        ]
        .into_iter()
        .flatten()
        .collect();
        if !lines.is_empty() {
            feature.props.insert("_L".into(), lines.join("\n").into());
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                icon_rotate: Some(IconRot::Property("ORIENT".into())),
                ..Default::default()
            }),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                icon_rotate: Some(IconRot::Property("ORIENT".into())),
                ..Default::default()
            }),
            b.line_layer_with_label(&Label::Property("_L".into()), LineLabelOpts::default()),
            b.point_layer_with_label(
                &Label::Property("_L".into()),
                PointLabelOpts {
                    label_color: Color::CHBLK,
                    highlight_color: Color::CHWHT,
                    text_anchor: Some(Anchor::Left),
                    text_justify: TextJustify::Left,
                    text_offset: Some(Offset::Coord(1.2, 0.0)),
                },
            ),
        ]
    }
}

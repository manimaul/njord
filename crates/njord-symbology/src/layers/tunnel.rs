//! Geometry Primitives: Point, Line, Area
//!
//! Object: Tunnel
//!
//! Acronym: TUNNEL
//!
//! Code: 151
//!
//! Attributes: BURDEP, CONDTN, HORACC, HORCLR, NOBJNM, OBJNAM, STATUS, VERACC, VERCLR

use crate::layers::prelude::*;

const AREA_FILL_COLORS: &[Color] = &[Color::DEPVS];
const LINE_COLORS: &[Color] = &[Color::CHBLK, Color::CHGRD];

#[derive(Clone, Copy, Debug, Default)]
pub struct Tunnel;

impl Layerable for Tunnel {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        if float_value(&feature.props, "BURDEP").is_some() {
            feature.area_color(Color::DEPVS);
            feature.line_color(Color::CHBLK);
        } else {
            feature.line_color(Color::CHGRD);
        }
        let name = string_value(&feature.props, "NOBJNM")
            .or_else(|| string_value(&feature.props, "OBJNAM"));
        if let Some(name) = name {
            feature.props.insert("_name".into(), name.into());
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        let label = Label::Property("_name".into());
        vec![
            b.area_layer_with_fill_colors(AREA_FILL_COLORS),
            b.line_layer_with_colors(
                LINE_COLORS,
                LineColorsOpts {
                    style: LineStyle::DashLine,
                    width: 1.0,
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
            b.line_layer_with_label(
                &label,
                LineLabelOpts {
                    label_color: Color::CHBLK,
                    highlight_color: Color::CHWHT,
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

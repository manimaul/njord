//! Geometry Primitives: Point
//!
//! Object: Light
//!
//! Acronym: LIGHTS
//!
//! Code: 75

use crate::layers::light_description::LightDescription;
use crate::layers::prelude::*;

/// The light description text before the height (see [`LightDescription`]).
pub const DESCRIPTION_HEAD: &str = "_LDH";
/// The light description text after the height.
pub const DESCRIPTION_TAIL: &str = "_LDT";
const FEET_PER_METER: f64 = 3.28084;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lights;

impl Layerable for Lights {
    /// The flare by the first colour, plus `SI` — the name of the sector icon the server renders
    /// on demand (`build_sector_svg`), carrying the bearings and every theme's colours — for a
    /// sector light or a major light (VALNMR >= 10, drawn as an all-round arc).
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        let light_color = match Colour::first(&feature.props) {
            Some(Colour::Red) => {
                feature.point_symbol(Sprite::LIGHTS11);
                feature.line_color(Color::LITRD);
                Color::LITRD
            }
            Some(Colour::Green) => {
                feature.point_symbol(Sprite::LIGHTS12);
                feature.line_color(Color::LITGN);
                Color::LITGN
            }
            Some(Colour::Yellow | Colour::White | Colour::Amber | Colour::Orange) => {
                feature.point_symbol(Sprite::LIGHTS13);
                feature.line_color(Color::LITYW);
                Color::LITYW
            }
            _ => {
                feature.line_color(Color::LITYW);
                feature.point_symbol(Sprite::LITDEF11);
                Color::LITYW
            }
        };

        let sectr1 = double_value(&feature.props, "SECTR1");
        let sectr2 = double_value(&feature.props, "SECTR2");
        let valnmr = double_value(&feature.props, "VALNMR");
        let major_light = valnmr.is_some_and(|v| v >= 10.0);
        let colors = |color: Color| {
            [ThemeMode::Day, ThemeMode::Dusk, ThemeMode::Night]
                .map(|mode| cx.colors.color_from(color, &mode.into()).to_owned())
                .join("_")
        };
        let radius = match light_color {
            Color::LITRD => 68,
            Color::LITGN => 74,
            _ => 80,
        };

        if major_light || (sectr1.is_some() && sectr2.is_some()) {
            let s1 = double_to_string(sectr1.unwrap_or(0.0));
            let s2 = double_to_string(sectr2.unwrap_or(0.0));
            let si = format!(
                "sector_{s1}_{s2}_{}_{}_{radius}",
                colors(light_color),
                colors(Color::CHBLK)
            );
            feature.props.insert("SI".into(), si.into());
        }

        // S-52 LIGHTS05: "The LITDSN text string is not used for sector lights because it would
        // cause clatter". A major light drawn with an all-round SI arc still gets one.
        if (sectr1.is_none() || sectr2.is_none())
            && let Some(d) = LightDescription::from(&feature.props)
        {
            feature.props.insert(DESCRIPTION_HEAD.into(), d.head.into());
            feature.props.insert(DESCRIPTION_TAIL.into(), d.tail.into());
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Bottom,
                icon_allow_overlap: true,
                icon_rotate: Some(IconRot::Degrees(135.0)),
                filter: Some(json!([
                    filters::ALL,
                    filters::eq_type_point(),
                    ["!has", "SI"]
                ])),
                ..Default::default()
            }),
            Layer {
                filter: Some(json!([
                    filters::ALL,
                    filters::eq_type_point(),
                    ["has", "SI"]
                ])),
                layout: Some(Layout {
                    symbol_placement: Some(Placement::Point),
                    icon_image: Some(json!(["get", "SI"])),
                    icon_anchor: Some(Anchor::Center),
                    icon_allow_overlap: Some(true),
                    icon_ignore_placement: Some(true),
                    icon_size: Some(1.0),
                    icon_rotation_alignment: Some(IconRotationAlignment::Map),
                    ..Default::default()
                }),
                ..b.layer("LIGHTS_sector", LayerType::Symbol)
            },
            description(b),
        ]
    }
}

/// S-52 LIGHTS05 writes the description with `TX('LITDSN',3,2,3,'15110',2,0,CHBLK,23)` when the
/// flare is at 135° (always, here): left justified, vertically centred, two text bodies right
/// of the light.
fn description(b: &LayerBuilder) -> Layer {
    Layer {
        filter: Some(json!([
            filters::ALL,
            filters::eq_type_point(),
            ["has", DESCRIPTION_HEAD]
        ])),
        layout: Some(Layout {
            text_font: Some(vec![Font::RobotoBold]),
            text_anchor: Some(Anchor::Left),
            text_justify: Some(TextJustify::Left),
            text_field: Some(description_text(b.depth)),
            text_offset: Some(Offset::Coord(2.0, 0.0).property()),
            text_size: Some(14.0),
            symbol_placement: Some(Placement::Point),
            ..Default::default()
        }),
        paint: Some(b.label_paint(Color::SNDG2, Color::DEPDW)),
        ..b.layer(format!("{}_description", b.key), LayerType::Symbol)
    }
}

/// `head + height + tail` - see [`LightDescription`] for why the height is spliced in here rather
/// than pre-rendered. A zero height is guarded for the same reason as in `clearance_label`.
fn description_text(depth: Depth) -> Value {
    json!([
        "concat",
        ["get", DESCRIPTION_HEAD],
        [
            "case",
            ["all", ["has", "HEIGHT"], [">", ["get", "HEIGHT"], 0]],
            height(depth),
            ""
        ],
        ["get", DESCRIPTION_TAIL],
    ])
}

/// Metres per the spec's numeric rule: whole, or one decimal when the fraction is non-zero.
fn height(depth: Depth) -> Value {
    match depth {
        Depth::Meters => json!([
            "concat",
            [
                "number-format",
                ["get", "HEIGHT"],
                {"locale": "en-US", "min-fraction-digits": 0, "max-fraction-digits": 1}
            ],
            "m"
        ]),
        Depth::Feet | Depth::Fathoms => json!([
            "concat",
            [
                "to-string",
                ["round", ["*", ["get", "HEIGHT"], FEET_PER_METER]]
            ],
            "ft"
        ]),
    }
}

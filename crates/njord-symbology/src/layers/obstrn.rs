//! Geometry Primitives: Point, Line, Area
//!
//! Object: Obstruction
//!
//! Acronym: OBSTRN
//!
//! Code: 86
//!
//! Two layerables read the OBSTRN source layer: [`ObstrnArea`] draws the area fill
//! and outline with the land layers, [`Obstrn`] the symbol and depth text with the other
//! obstructions. [`Obstrn`] reuses
//! the [`soundg`] text layers for its depth text.

use crate::layers::prelude::*;
use crate::layers::soundg;

#[derive(Clone, Copy, Debug, Default)]
pub struct ObstrnArea;

impl Layerable for ObstrnArea {
    fn source_layer(&self) -> Option<&str> {
        Some("OBSTRN")
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        let area_fill_colors: Vec<Color> = DepthColor::ALL.iter().map(|d| d.color()).collect();
        vec![
            b.area_layer_with_fill_colors(&area_fill_colors),
            b.area_layer_with_fill_pattern(None),
            b.line_layer_with_color(
                Color::CHGRD,
                LineColorOpts {
                    style: LineStyle::DashLine,
                    ..Default::default()
                },
            ),
        ]
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub struct Obstrn;

impl Layerable for Obstrn {
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        let text_layers = soundg::text_layers(b);
        let mut layers = vec![
            b.point_layer_from_symbol(PointSymbolOpts {
                anchor: Anchor::Center,
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
            b.area_layer_with_point_symbol(AreaPointSymbolOpts {
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                ..Default::default()
            }),
        ];
        layers.extend(text_layers);
        layers
    }

    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        let state = ObstrnState::new(feature);

        let mut sy_set = false;
        let mut show_depth = state.usable_depth_value;
        let mut fill_depth_color = true;

        match state.category {
            Some(Catobs::SNAG_STUMP | Catobs::WELLHEAD | Catobs::DIFFUSER | Catobs::CRIB) => {
                if !state.usable_depth_value {
                    feature.point_symbol(Sprite::ISODGR01);
                    sy_set = true;
                    show_depth = false;
                }
            }
            Some(Catobs::FISH_HAVEN) => {
                feature.point_symbol(Sprite::FSHHAV01);
                sy_set = true;
                show_depth = false;
            }
            Some(Catobs::FOUL_AREA | Catobs::FOUL_GROUND) => {
                feature.area_pattern(Sprite::FOULAR01P)
            }
            Some(Catobs::GROUND_TACKLE) => {
                feature.point_symbol(Sprite::ACHARE02);
                sy_set = true;
                show_depth = false;
            }
            Some(Catobs::ICE_BOOM | Catobs::BOOM) => feature.point_symbol(Sprite::FLTHAZ02),
            None => {}
        }

        let mut check_accuracy = true;
        if !sy_set {
            match state.water_level_effect {
                Some(Watlev::COVERS_AND_UNCOVERS) => {
                    check_accuracy = false;
                    feature.exclude_area_point_symbol();
                    feature.point_symbol(Sprite::OBSTRN03);
                }
                Some(Watlev::ALWAYS_DRY) => {
                    check_accuracy = false;
                    feature.exclude_area_point_symbol();
                    feature.point_symbol(Sprite::OBSTRN11);
                }
                Some(Watlev::ALWAYS_UNDER_WATER_SUBMERGED) => {
                    feature.exclude_area_point_symbol();
                    let (shown, hidden) = match state.depth_color(cx) {
                        DepthColor::DEEP_WATER | DepthColor::MEDIUM_DEPTH => {
                            (Sprite::DANGER02, Sprite::OBSTRN02)
                        }
                        DepthColor::SAFETY_DEPTH | DepthColor::VERY_SHALLOW => {
                            (Sprite::DANGER01, Sprite::OBSTRN01)
                        }
                        DepthColor::COVERS_UNCOVERS => (Sprite::DANGER03, Sprite::OBSTRN03),
                    };
                    feature.point_symbol(if show_depth { shown } else { hidden });
                }
                Some(Watlev::FLOATING) => {
                    show_depth = false;
                    fill_depth_color = false;
                    feature.point_symbol(Sprite::FLTHAZ02);
                }
                Some(
                    Watlev::PARTLY_SUBMERGED_AT_HIGH_WATER
                    | Watlev::AWASH
                    | Watlev::SUBJECT_TO_INUNDATION_OR_FLOODING,
                )
                | None => {
                    check_accuracy = false;
                    feature.point_symbol(Sprite::ISODGR01);
                }
            }
        }

        if check_accuracy
            && state
                .quality_of_sounding
                .iter()
                .any(|q| matches!(q, Quasou::DOUBTFUL_SOUNDING | Quasou::UNRELIABLE_SOUNDING))
        {
            show_depth = false;
            feature.point_symbol(Sprite::LOWACC01);
        }

        if fill_depth_color {
            feature.area_color(state.depth_color(cx).color());
        }
        if let Some(meters) = state.meters.filter(|_| show_depth) {
            add_sounding_conversions(&mut feature.props, meters as f64);
        }
    }
}

/// The attributes an obstruction's symbology reads (`ObstrnState`).
pub struct ObstrnState {
    /// VALSOU, read as an `f32`.
    pub meters: Option<f32>,
    pub category: Option<Catobs>,
    pub water_level_effect: Option<Watlev>,
    pub quality_of_sounding: Vec<Quasou>,
    /// The first QUASOU says the depth is known (or a safe clearance).
    pub usable_depth_value: bool,
}

impl ObstrnState {
    pub fn new(feature: &ChartFeature) -> Self {
        let quality_of_sounding = Quasou::of(&feature.props);
        let usable_depth_value = quality_of_sounding.first().is_some_and(|q| {
            matches!(
                q,
                Quasou::DEPTH_KNOWN
                    | Quasou::NO_BOTTOM_FOUND_AT_VALUE_SHOWN
                    | Quasou::LEAST_DEPTH_KNOWN
                    | Quasou::LEAST_DEPTH_UNKNOWN_SAFE_CLEARANCE_AT_VALUE_SHOWN
                    | Quasou::MAINTAINED_DEPTH
            )
        });
        ObstrnState {
            meters: float_value(&feature.props, "VALSOU"),
            category: Catobs::of(&feature.props),
            water_level_effect: Watlev::of(&feature.props),
            quality_of_sounding,
            usable_depth_value,
        }
    }

    /// The colour band: COVERS_UNCOVERS unless always submerged, then by a usable VALSOU against
    /// the thresholds (VERY_SHALLOW without one).
    pub fn depth_color(&self, cx: &Context) -> DepthColor {
        let config = &cx.config;
        match self.water_level_effect {
            Some(Watlev::ALWAYS_UNDER_WATER_SUBMERGED) => self
                .meters
                .filter(|_| self.usable_depth_value)
                .map(|m| {
                    if m <= config.shallow_depth {
                        DepthColor::VERY_SHALLOW
                    } else if m <= config.safety_depth {
                        DepthColor::SAFETY_DEPTH
                    } else if m <= config.deep_depth {
                        DepthColor::MEDIUM_DEPTH
                    } else if m > config.deep_depth {
                        DepthColor::DEEP_WATER
                    } else {
                        panic!("unexpected VALSOU {m}")
                    }
                })
                .unwrap_or(DepthColor::VERY_SHALLOW),
            _ => DepthColor::COVERS_UNCOVERS,
        }
    }
}

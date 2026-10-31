//! Geometry Primitives: Point
//!
//! Object: Top mark
//!
//! Acronym: TOPMAR
//!
//! Code: 144
//!
//! S-52_PresLib_v4.0
//! Topmark objects are to be symbolized through
//! consideration of their platforms e.g. a buoy. Therefore this conditional
//! symbology procedure searches for platforms by looking for other objects
//! that are located at the same position. Based on the finding whether the
//! platform is rigid or floating, the respective upright or sloping symbol is
//! selected and presented at the objects location. Buoy symbols and
//! topmark symbols have been carefully designed to fit to each other when
//! combined at the same position. The result is a composed symbol that
//! looks like the traditional symbols the mariner is used to.
//!
//! The platform comes from [`ChartFeature::associated_layer_names`]: any feature sharing the
//! position makes it floating, none makes it rigid.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TopmarPlatform {
    Floating,
    Rigid,
}

impl TopmarPlatform {
    /// The Kotlin constant name, written to `_PTFM`.
    pub fn name(self) -> &'static str {
        match self {
            TopmarPlatform::Floating => "FLOATING",
            TopmarPlatform::Rigid => "RIGID",
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub struct Topmar;

impl Layerable for Topmar {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let assoc = feature.associated_layer_names.clone();
        let platform = if assoc.is_empty() {
            TopmarPlatform::Rigid
        } else {
            TopmarPlatform::Floating
        };
        feature.props.insert("_PTFM".into(), platform.name().into());
        feature.props.insert("_ASOC".into(), assoc.into());
        let shape = Topshp::of(&feature.props);
        feature.point_symbol(match platform {
            TopmarPlatform::Floating => floating(shape),
            TopmarPlatform::Rigid => rigid(shape),
        });
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            icon_offset: Some(Offset::EvalEq {
                key: "_PTFM".into(),
                value: TopmarPlatform::Rigid.name().into(),
                eq: (0.0, 0.0),   // RIGID
                neq: (0.0, -8.0), // FLOATING
            }),
            ..Default::default()
        })]
    }
}

fn floating(shape: Option<Topshp>) -> Sprite {
    use Topshp::*;
    let Some(shape) = shape else {
        return Sprite::TMARDEF2;
    };
    match shape {
        CONE_POINT_UP => Sprite::TOPMAR02,
        CONE_POINT_DOWN => Sprite::TOPMAR04,
        SPHERE => Sprite::TOPMAR10,
        TWO_SPHERE => Sprite::TOPMAR12,
        CYLINDER_CAN => Sprite::TOPMAR13,
        BOARD => Sprite::TOPMAR14,
        X_SHAPE => Sprite::TOPMAR65,
        UPRIGHT_CROSS => Sprite::TOPMAR17,
        CUBE_POINT_UP => Sprite::TOPMAR16,
        TWO_CONES_POINT_TO_POINT => Sprite::TOPMAR08,
        TWO_CONES_BASE_TO_BASE => Sprite::TOPMAR07,
        RHOMBUS_DIAMOND => Sprite::TOPMAR14,
        TWO_CONES_POINTS_UPWARD => Sprite::TOPMAR05,
        TWO_CONES_POINTS_DOWNWARD => Sprite::TOPMAR06,
        BESOM_POINT_UP_BROOM_OR_PERCH => Sprite::TMARDEF2,
        BESOM_POINT_DOWN_BROOM_OR_PERCH => Sprite::TMARDEF2,
        FLAG => Sprite::TMARDEF2,
        SPHERE_OVER_RHOMBUS => Sprite::TOPMAR10,
        SQUARE => Sprite::TOPMAR13,
        RECTANGLE_HORIZONTAL => Sprite::TOPMAR14,
        RECTANGLE_VERTICAL => Sprite::TOPMAR13,
        TRAPEZIUM_UP => Sprite::TOPMAR14,
        TRAPEZIUM_DOWN => Sprite::TOPMAR14,
        TRIANGLE_POINT_UP => Sprite::TOPMAR02,
        TRIANGLE_POINT_DOWN => Sprite::TOPMAR04,
        CIRCLE => Sprite::TOPMAR10,
        TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER => Sprite::TOPMAR17,
        T_SHAPE => Sprite::TOPMAR18,
        TRIANGLE_POINTING_UP_OVER_A_CIRCLE => Sprite::TOPMAR02,
        UPRIGHT_CROSS_OVER_A_CIRCLE => Sprite::TOPMAR17,
        RHOMBUS_OVER_A_CIRCLE => Sprite::TOPMAR14,
        CIRCLE_OVER_A_TRIANGLE_POINTING_UP => Sprite::TOPMAR10,
        OTHER_SHAPE => Sprite::TMARDEF2,
    }
}

fn rigid(shape: Option<Topshp>) -> Sprite {
    use Topshp::*;
    let Some(shape) = shape else {
        return Sprite::TMARDEF1;
    };
    match shape {
        CONE_POINT_UP => Sprite::TOPMAR22,
        CONE_POINT_DOWN => Sprite::TOPMAR24,
        SPHERE => Sprite::TOPMAR30,
        TWO_SPHERE => Sprite::TOPMAR32,
        CYLINDER_CAN => Sprite::TOPMAR33,
        BOARD => Sprite::TOPMAR34,
        X_SHAPE => Sprite::TOPMAR85,
        UPRIGHT_CROSS => Sprite::TOPMAR86,
        CUBE_POINT_UP => Sprite::TOPMAR36,
        TWO_CONES_POINT_TO_POINT => Sprite::TOPMAR28,
        TWO_CONES_BASE_TO_BASE => Sprite::TOPMAR27,
        RHOMBUS_DIAMOND => Sprite::TOPMAR14,
        TWO_CONES_POINTS_UPWARD => Sprite::TOPMAR25,
        TWO_CONES_POINTS_DOWNWARD => Sprite::TOPMAR26,
        BESOM_POINT_UP_BROOM_OR_PERCH => Sprite::TOPMAR88,
        BESOM_POINT_DOWN_BROOM_OR_PERCH => Sprite::TOPMAR87,
        FLAG => Sprite::TMARDEF1,
        SPHERE_OVER_RHOMBUS => Sprite::TOPMAR30,
        SQUARE => Sprite::TOPMAR33,
        RECTANGLE_HORIZONTAL => Sprite::TOPMAR34,
        RECTANGLE_VERTICAL => Sprite::TOPMAR33,
        TRAPEZIUM_UP => Sprite::TOPMAR34,
        TRAPEZIUM_DOWN => Sprite::TOPMAR34,
        TRIANGLE_POINT_UP => Sprite::TOPMAR22,
        TRIANGLE_POINT_DOWN => Sprite::TOPMAR24,
        CIRCLE => Sprite::TOPMAR30,
        TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER => Sprite::TOPMAR86,
        T_SHAPE => Sprite::TOPMAR89,
        TRIANGLE_POINTING_UP_OVER_A_CIRCLE => Sprite::TOPMAR22,
        UPRIGHT_CROSS_OVER_A_CIRCLE => Sprite::TOPMAR86,
        RHOMBUS_OVER_A_CIRCLE => Sprite::TOPMAR14,
        CIRCLE_OVER_A_TRIANGLE_POINTING_UP => Sprite::TOPMAR30,
        OTHER_SHAPE => Sprite::TMARDEF1,
    }
}

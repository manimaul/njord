//! Geometry Primitives: Point, Area
//!
//! Object: Wreck
//!
//! Acronym: WRECKS
//!
//! Code: 159

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Wrecks;

impl Layerable for Wrecks {
    /// WRECKS01 (hull showing above water) visible above the surface; WRECKS05 (fishbone with
    /// dotted circle) dangerous, submerged, depth unknown; WRECKS04 (fishbone) non-dangerous;
    /// DANGER01 (hazard symbol with depth text) depth known, at or under the safety depth;
    /// ISODGR01 (magenta "screw head") hazard in otherwise safe water.
    fn pre_tile_encode(&self, feature: &mut ChartFeature, cx: &Context) {
        match Catwrk::of(&feature.props) {
            Some(Catwrk::DANGEROUS_WRECK) => feature.point_symbol(Sprite::WRECKS05),
            Some(
                Catwrk::WRECK_SHOWING_MAST_MASTS
                | Catwrk::WRECK_SHOWING_ANY_PORTION_OF_HULL_OR_SUPERSTRUCTURE,
            ) => feature.point_symbol(Sprite::WRECKS01),
            Some(Catwrk::DISTRIBUTED_REMAINS_OF_WRECK | Catwrk::NON_DANGEROUS_WRECK) | None => {
                // Named as in Kotlin, though true means *deeper* than the safety depth.
                let under_safety =
                    float_value(&feature.props, "VALSOU").map(|v| v > cx.config.safety_depth);
                if (Watlev::of(&feature.props) == Some(Watlev::ALWAYS_UNDER_WATER_SUBMERGED)
                    && under_safety.is_none())
                    || under_safety == Some(true)
                {
                    feature.point_symbol(Sprite::WRECKS04);
                } else if under_safety == Some(false) {
                    feature.point_symbol(Sprite::DANGER01);
                } else {
                    feature.point_symbol(Sprite::ISODGR01);
                }
            }
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts {
            anchor: Anchor::Center,
            icon_rotation_alignment: Some(IconRotationAlignment::Map),
            ..Default::default()
        })]
    }
}

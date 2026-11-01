//! Geometry Primitives: Point
//!
//! Object: Underwater rock / awash rock
//!
//! Acronym: UWTROC
//!
//! Code: 152
//!
//! S-52 chooses the symbol by WATLEV (always under water: UWTROC04; covers and uncovers, awash
//! or flooding: UWTROC03); everything else gets the isolated danger symbol.

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Uwtroc;

impl Layerable for Uwtroc {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        match Watlev::of(&feature.props) {
            Some(Watlev::ALWAYS_UNDER_WATER_SUBMERGED) => feature.point_symbol(Sprite::UWTROC04),
            Some(
                Watlev::AWASH
                | Watlev::SUBJECT_TO_INUNDATION_OR_FLOODING
                | Watlev::PARTLY_SUBMERGED_AT_HIGH_WATER
                | Watlev::COVERS_AND_UNCOVERS,
            ) => feature.point_symbol(Sprite::UWTROC03),
            Some(Watlev::ALWAYS_DRY | Watlev::FLOATING) | None => {
                feature.point_symbol(Sprite::ISODGR01)
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

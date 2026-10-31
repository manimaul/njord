//! Geometry Primitives: Point
//!
//! Object: Buoy, special purpose/general
//!
//! Acronym: BOYSPP
//!
//! Code: 19

use crate::layers::boylat;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Boyspp;

impl Layerable for Boyspp {
    /// The first CATSPM with a symbol of its own wins, else the BOYSHP symbol (`Boylat`).
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let special = Catspm::of(&feature.props)
            .into_iter()
            .find_map(|c| match c {
                Catspm::LANBY_LARGE_AUTOMATIC_NAVIGATIONAL_BUOY => Some(Sprite::BOYSUP02),
                Catspm::MARK_WITH_UNKNOWN_PURPOSE => Some(Sprite::BOYDEF03),
                _ => None,
            });
        match special {
            Some(sprite) => feature.point_symbol(sprite),
            None => boylat::boyshp(feature),
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

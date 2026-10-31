//! Geometry Primitives: Point
//!
//! Object: Beacon, special purpose/general
//!
//! Acronym: BCNSPP
//!
//! Code: 9

use crate::layers::bcnlat;
use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Bcnspp;

impl Layerable for Bcnspp {
    /// The first CATSPM with a symbol of its own wins, else the BCNSHP symbol (`Bcnlat`).
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let special = Catspm::of(&feature.props)
            .into_iter()
            .find_map(|c| match c {
                Catspm::NOTICE_MARK => Some(Sprite::NOTBRD11),
                Catspm::REFUGE_BEACON => Some(Sprite::BCNSPP13),
                Catspm::MARK_WITH_UNKNOWN_PURPOSE => Some(Sprite::BCNDEF13),
                _ => None,
            });
        match special {
            Some(sprite) => feature.point_symbol(sprite),
            None => bcnlat::bcnshp(feature),
        }
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![b.point_layer_from_symbol(PointSymbolOpts::default())]
    }
}

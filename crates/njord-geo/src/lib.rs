//! Tile geometry for the Njord port (RUST_PORT_PLAN.md §4.1, §5.2): the Web Mercator tile system,
//! the `OgrGeometry` operations the tile path needs (over `geo` instead of GDAL/GEOS), the MVT
//! encoder and the pure parts of the tile encoder.
//!
//! Fixtures: P-TE-1 [`tile_system`], P-TE-2 [`mvt`], P-TE-3 / P-TE-4 [`tile_encoder`], LB-1
//! [`geometry`] (best effort; divergences from GEOS are listed in the test).

pub mod geometry;
pub mod mvt;
pub mod tile_encoder;
pub mod tile_system;

pub use mvt::VectorTileEncoder;
pub use tile_encoder::{UnexpectedObject, filter_props, find_base_map_scale};
pub use tile_system::{TILE_SYSTEM, TWEAK_ZOOM_TO_SCALE_BY, TileSystem};

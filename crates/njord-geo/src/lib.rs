//! Tile geometry for Njord: Web Mercator tile math, tile-pixel geometry operations and MVT
//! encoding.
//!
//! The crate is pure Rust and has no GDAL or database dependency. Its parts, in the order the tile
//! path uses them:
//!
//! - [`tile_system`]: the 4096 px Web Mercator [`TileSystem`] ([`TILE_SYSTEM`]), which turns
//!   WGS-84 positions into tile and pixel coordinates, builds tile clip polygons and maps chart
//!   scales to zoom levels.
//! - [`tile_encoder`]: the pure decisions of tile assembly, [`filter_props`] and
//!   [`find_base_map_scale`].
//! - [`geometry`]: the geometry operations the tile path needs (validity repair, clipping,
//!   transformation into tile pixels), implemented over [`geo`].
//! - [`mvt`]: the Mapbox Vector Tile encoder, [`VectorTileEncoder`], and a decoder into a readable
//!   JSON view ([`mvt::view`]).
//!
//! The golden fixtures under `testdata/geo/` pin each module; the tests in `tests/` compare
//! against them.
//!
//! # Examples
//!
//! Locating the tile that holds a position, and encoding one feature into it:
//!
//! ```
//! use geo_types::{Geometry, Point};
//! use njord_geo::{TILE_SYSTEM, VectorTileEncoder};
//! use njord_model::JsonObject;
//!
//! // Seattle at zoom 10: the integer part is the tile, the fraction the position inside it.
//! let tile = TILE_SYSTEM.lat_lng_to_tile_xy(-122.4, 47.6, 10);
//! assert_eq!((tile.x.floor(), tile.y.floor()), (163.0, 357.0));
//!
//! // The same position in pixels relative to that tile's top-left corner.
//! let px = TILE_SYSTEM.lat_lng_to_tile_bounded_xy(-122.4, 47.6, 163, 357, 10);
//! assert!((0.0..4096.0).contains(&px.x) && (0.0..4096.0).contains(&px.y));
//!
//! let mut encoder = VectorTileEncoder::new();
//! encoder.add_feature("SOUNDG", &JsonObject::new(), &Geometry::Point(Point(px)));
//! assert!(encoder.has_features(&[]));
//! let bytes = encoder.encode();
//! assert!(!bytes.is_empty());
//! ```

pub mod geometry;
pub mod mvt;
pub mod tile_encoder;
pub mod tile_system;

pub use mvt::VectorTileEncoder;
pub use tile_encoder::{UnexpectedObject, filter_props, find_base_map_scale};
pub use tile_system::{TILE_SYSTEM, TWEAK_ZOOM_TO_SCALE_BY, TileSystem};

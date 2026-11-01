//! Web Mercator tile math with 4096 px tiles.
//!
//! Every formula keeps a fixed floating-point operation order, so results are bit-identical to
//! the golden fixture `testdata/geo/tile_system.json`. The map size ([`TileSystem::map_size`]) is
//! computed in 64 bits, so every zoom `0 ≤ z ≤ 24` the server serves is meaningful. The fixture was
//! recorded with a 32-bit map size, which overflows from z19 (`4096 << 19 == 2^31`) and makes its
//! pixel coordinates and clip polygons at z ≥ 19 garbage; the tests check those cases for sanity
//! rather than equality.

use std::f64::consts::PI;

use geo_types::{Coord, LineString, Polygon};

/// The Mercator latitude limit: the latitude whose projected `y` equals the map's half width.
const MAX_LAT: f64 = 85.05112878;
const MIN_LAT: f64 = -MAX_LAT;
const MIN_LNG: f64 = -180.0;
const MAX_LNG: f64 = 180.0;

/// Metres at the equator.
const EARTH_CIRCUMFERENCE: f64 = 40_075_016.686;
/// A 96 dpi screen pixel, in metres.
const SCREEN_PIXEL_SIZE_METERS: f64 = 0.0254 / 96.0;
/// Screen pixels per Web Mercator tile (MapLibre's 256, not the 4096 tile extent).
const SCREEN_TILE_SIZE: f64 = 256.0;

/// What [`TileSystem::scale_to_zoom_int`] multiplies the fractional zoom by before rounding, so a
/// chart becomes visible slightly before its compilation scale.
pub const TWEAK_ZOOM_TO_SCALE_BY: f64 = 0.92;

/// Clamps `num` to `min_value..=max_value`: `min(max(num, min_value), max_value)`.
pub fn clip(num: f64, min_value: f64, max_value: f64) -> f64 {
    num.max(min_value).min(max_value)
}

/// A Web Mercator tile pyramid with square tiles of [`tile_size`](Self::tile_size) pixels.
///
/// Njord always uses [`TILE_SYSTEM`], whose tiles are 4096 px (the MVT extent).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct TileSystem {
    /// Pixels along one side of a tile.
    tile_size: i64,
}

impl Default for TileSystem {
    fn default() -> Self {
        TileSystem::new(4096)
    }
}

/// The 4096 px tile system every Njord tile uses.
pub const TILE_SYSTEM: TileSystem = TileSystem::new(4096);

impl TileSystem {
    /// A tile system whose tiles are `tile_size` pixels square.
    pub const fn new(tile_size: i64) -> Self {
        TileSystem { tile_size }
    }

    /// Pixels along one side of a tile.
    pub const fn tile_size(&self) -> i64 {
        self.tile_size
    }

    /// Map width and height in pixels at zoom `z`: `tile_size << z`, in 64 bits.
    pub const fn map_size(&self, z: u32) -> i64 {
        self.tile_size << z
    }

    /// Fractional zoom at which a chart of scale `1:scale_denominator` is shown at its natural
    /// size, at `latitude_degrees`.
    pub fn scale_to_zoom(&self, scale_denominator: f64, latitude_degrees: f64) -> f64 {
        let lat_rad = latitude_degrees * PI / 180.0;
        (EARTH_CIRCUMFERENCE * lat_rad.cos()
            / (SCREEN_TILE_SIZE * scale_denominator * SCREEN_PIXEL_SIZE_METERS))
            .log2()
    }

    /// Inverse of [`scale_to_zoom`](Self::scale_to_zoom): the scale denominator at `zoom`.
    pub fn zoom_to_scale(&self, zoom: f64, latitude_degrees: f64) -> f64 {
        let lat_rad = latitude_degrees * PI / 180.0;
        EARTH_CIRCUMFERENCE * lat_rad.cos()
            / (SCREEN_TILE_SIZE * 2f64.powf(zoom) * SCREEN_PIXEL_SIZE_METERS)
    }

    /// The whole zoom at which a chart of scale `1:scale_denominator` becomes visible.
    ///
    /// This is [`scale_to_zoom`](Self::scale_to_zoom) `× tweak_by`, rounded half to even and
    /// saturated to `i32`. Callers that have no reason to differ pass [`TWEAK_ZOOM_TO_SCALE_BY`].
    pub fn scale_to_zoom_int(
        &self,
        scale_denominator: f64,
        latitude_degrees: f64,
        tweak_by: f64,
    ) -> i32 {
        (self.scale_to_zoom(scale_denominator, latitude_degrees) * tweak_by).round_ties_even()
            as i32
    }

    /// WGS-84 position of a pixel at zoom `z`; pixels outside the map are clamped onto it.
    fn pixel_xy_to_lat_lng(&self, pixel: Coord, z: u32) -> Coord {
        let m_size = self.map_size(z) as f64;
        let x = (clip(pixel.x, 0.0, m_size - 1.0) / m_size) - 0.5;
        let y = 0.5 - (clip(pixel.y, 0.0, m_size - 1.0) / m_size);
        let lat = 90.0 - 360.0 * (-y * 2.0 * PI).exp().atan() / PI;
        let lng = 360.0 * x;
        Coord { x: lng, y: lat }
    }

    /// Whole pixel containing a WGS-84 position at zoom `z`. Positions past the Mercator limits
    /// or the antimeridian are clamped, and the result is truncated to the pixel grid.
    fn lat_lng_to_pixel_xy(&self, lng: f64, lat: f64, z: u32) -> Coord {
        let latitude = clip(lat, MIN_LAT, MAX_LAT);
        let longitude = clip(lng, MIN_LNG, MAX_LNG);

        let x = (longitude + 180.0) / 360.0;
        let sin_lat = (latitude * PI / 180.0).sin();
        let y = 0.5 - ((1.0 + sin_lat) / (1.0 - sin_lat)).ln() / (4.0 * PI);

        let m_size = self.map_size(z) as f64;
        Coord {
            x: clip(x * m_size + 0.5, 0.0, m_size - 1.0).trunc(),
            y: clip(y * m_size + 0.5, 0.0, m_size - 1.0).trunc(),
        }
    }

    /// Fractional tile coordinate of a WGS-84 position: the containing pixel divided by the
    /// tile size, so the integer part is the tile and the fraction the position inside it.
    pub fn lat_lng_to_tile_xy(&self, lng: f64, lat: f64, z: u32) -> Coord {
        let p = self.lat_lng_to_pixel_xy(lng, lat, z);
        let size = self.tile_size as f64;
        Coord {
            x: p.x / size,
            y: p.y / size,
        }
    }

    /// Whole-pixel position of a WGS-84 coordinate relative to the top-left corner of tile
    /// `x`/`y`/`z`: what feature geometry is transformed into before MVT encoding. Positions
    /// outside the tile come out negative or past the extent.
    pub fn lat_lng_to_tile_bounded_xy(&self, lng: f64, lat: f64, x: i64, y: i64, z: u32) -> Coord {
        let p = self.lat_lng_to_pixel_xy(lng, lat, z);
        Coord {
            x: p.x - (x * self.tile_size) as f64,
            y: p.y - (y * self.tile_size) as f64,
        }
    }

    /// The WGS-84 polygon covering tile `x`/`y`/`z`, grown by `expand_pixels` on every side.
    ///
    /// Corners past the map are clamped onto it. The ring runs top-left, top-right, bottom-right,
    /// bottom-left and back (clockwise in WGS-84).
    pub fn create_tile_clip_polygon(&self, x: i64, y: i64, z: u32, expand_pixels: i32) -> Polygon {
        let size = self.tile_size as f64;
        let expand = f64::from(expand_pixels);
        let px = x as f64 * size;
        let py = y as f64 * size;
        let corner = |x: f64, y: f64| self.pixel_xy_to_lat_lng(Coord { x, y }, z);
        let tl = corner(px - expand, py - expand);
        let tr = corner(px + size + expand, py - expand);
        let br = corner(px + size + expand, py + size + expand);
        let bl = corner(px - expand, py + size + expand);
        Polygon::new(LineString(vec![tl, tr, br, bl, tl]), vec![])
    }
}

//! WKB ↔ `geo-types`.
//!
//! Geometry columns are read as `st_asbinary(…)` (ISO WKB, no SRID) and decoded with geozero's
//! WKB reader into [`geo_types::Geometry`], the input type of `njord-geo`. Query parameters go
//! the other way. Reading through `st_asbinary` rather than decoding the `geometry` column's EWKB
//! keeps the SQL plain and leaves the sqlx version free of geozero's sqlx integration.

use geo_types::Geometry;
use geozero::wkb::Wkb;
use geozero::{CoordDimensions, ToGeo, ToWkb};

use crate::Error;

/// 2D WKB of `geometry`, for `st_geomfromwkb($n, 4326)` parameters.
///
/// # Errors
///
/// [`Error::Geometry`] when geozero cannot encode the geometry.
pub fn to_wkb(geometry: &Geometry) -> Result<Vec<u8>, Error> {
    geometry
        .to_wkb(CoordDimensions::xy())
        .map_err(|e| Error::Geometry(e.to_string()))
}

/// Decodes ISO WKB, as `st_asbinary` writes it.
///
/// Every stored geometry is 2D (forced on insert); OGR's pre-ISO 2.5D type codes, which ingest
/// sends, are not read here.
///
/// # Errors
///
/// [`Error::Geometry`] when `wkb` is not valid WKB.
pub fn from_wkb(wkb: &[u8]) -> Result<Geometry, Error> {
    Wkb(wkb)
        .to_geo()
        .map_err(|e| Error::Geometry(e.to_string()))
}

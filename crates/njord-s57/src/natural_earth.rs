//! Natural Earth shapefiles, and the helpers that place them in `base_features`.
//!
//! Shapefiles are read with GDAL's `ESRI Shapefile` driver and the same field mapping as S-57
//! cells rather than the pure-Rust `shapefile`/`dbase` crates: ingest repairs every geometry
//! with GDAL's `make_valid` anyway (NE land and lake polygons need it, and `geo` has no
//! equivalent), and GDAL decides the DBF numeric field types (`N(w,0)` → integer, otherwise
//! real), the `.cpg` recoding and the ring assembly the fixture pins.

use std::path::{Path, PathBuf};

use crate::feature::{Feature, read_layer};
use crate::{Error, ogr};

/// One `.shp` opened with GDAL.
///
/// It is `Send` but not `Sync`, like [`GdalCell`](crate::GdalCell).
pub struct Shapefile {
    path: PathBuf,
    dataset: ogr::Dataset,
}

impl Shapefile {
    /// Opens a `.shp` with GDAL's `ESRI Shapefile` driver.
    ///
    /// # Errors
    ///
    /// Returns an [`Error`] if GDAL cannot open the file.
    pub fn open(path: impl AsRef<Path>) -> Result<Shapefile, Error> {
        let path = path.as_ref().to_path_buf();
        let dataset = ogr::Dataset::open(&path, "ESRI Shapefile")?;
        Ok(Shapefile { path, dataset })
    }

    /// The `.shp` file this was opened from.
    pub fn path(&self) -> &Path {
        &self.path
    }

    /// One layer per `.shp`, named after the file without its extension.
    pub fn layer_names(&self) -> Vec<String> {
        self.dataset.layer_names()
    }

    /// The number of features in every layer together.
    pub fn feature_count(&self) -> u64 {
        self.layer_names()
            .iter()
            .filter_map(|l| self.dataset.layer(l))
            .map(|l| l.feature_count())
            .sum()
    }

    /// Every feature of one layer in file order, with the S-57 field mapping
    /// ([`Feature::props`]); `None` when there is no such layer.
    pub fn features(&self, layer: &str) -> Option<Vec<Feature>> {
        self.dataset
            .layer(layer)
            .map(|mut l| read_layer(&mut l, layer))
    }
}

/// The `base_features` layer for a shapefile or layer name.
///
/// Drops the extension and an `ne_10m_` / `ne_50m_` / `ne_110m_` prefix, and folds every
/// `bathymetry*` and `lakes*` variant into `bathymetry` and `lakes`.
pub fn normalize_layer_name(filename: &str) -> String {
    let base = filename.rsplit_once('.').map_or(filename, |(b, _)| b);
    let suffix = ["ne_10m_", "ne_50m_", "ne_110m_"]
        .iter()
        .find_map(|p| base.strip_prefix(p))
        .unwrap_or(base);
    if suffix.starts_with("bathymetry") {
        "bathymetry".into()
    } else if suffix.starts_with("lakes") {
        "lakes".into()
    } else {
        suffix.into()
    }
}

/// The scale denominator of a Natural Earth file: 1:110M for `_110m_`, 1:50M for `_50m_`,
/// otherwise 1:10M.
pub fn scale_from_filename(filename: &str) -> i32 {
    if filename.contains("_110m_") {
        110_000_000
    } else if filename.contains("_50m_") {
        50_000_000
    } else {
        10_000_000
    }
}

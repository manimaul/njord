//! S-57 cell and Natural Earth shapefile reading (RUST_PORT_PLAN.md §4.2), ported from the
//! Kotlin `libgdal` module (`OgrS57Dataset.kt`, `OgrLayer.kt`, `OgrFeature.kt`,
//! `GdalDataset.kt`, `Gdal.kt`, `OgrShapefileDataset.kt`) and the reading half of the server's
//! `ChartIngest.kt` and `NaturalEarthIngest.kt`. Database insertion stays in the server.
//!
//! This is the only crate that links GDAL. Everything downstream (the `features.props` JSONB,
//! `LNAM`/`LNAM_REFS`, `METERS` on soundings, `MINZ`/`MAXZ`, the `DSID_*`/`DSPM_*` metadata) is
//! shaped by GDAL's S57 driver under [`S57_OPTIONS`], so the reading contract is the trait
//! [`S57Reader`], implemented over GDAL by [`GdalCell`] and pinned by the P-IN-1…4 fixtures; a
//! future pure-Rust reader would be measured against the same files.
//!
//! # Building
//!
//! GDAL is bound through the georust `gdal` crate, pinned to a master commit because the released
//! 0.19 does not compile against GDAL 3.13 (`GDT_Byte` was renamed and `GDALRasterIOExtraArg`
//! gained a field). That commit ships prebuilt bindings for GDAL 3.8 through 3.13, so
//! `cargo test -p njord-s57` builds against Homebrew's 3.13 and Debian 13's 3.10 alike, without
//! bindgen. Every GDAL call is in the private `ogr` module.
//!
//! # Threads
//!
//! GDAL datasets are not thread-safe. [`GdalCell`] and [`Shapefile`] are `Send` but not `Sync`:
//! open one per worker and read it on that thread. [`Geometry`] and [`Feature`] own their data
//! and are `Send`, so read features can be handed to a database task.

mod chart;
mod feature;
mod natural_earth;
mod ogr;

use std::fmt;
use std::path::{Path, PathBuf};

use njord_model::ChartInsert;

pub use feature::Feature;
pub use natural_earth::{Shapefile, normalize_layer_name, scale_from_filename};
pub use ogr::Geometry;

/// `OGR_S57_OPTIONS`, set process-wide before the first dataset is opened.
///
/// This is the option set GDAL actually applies under the Kotlin string
/// `…,RECODE_BY_DSSI=ON:ADD_SOUNDG_DEPTH=ON`: the `:` typo makes GDAL read
/// `ON:ADD_SOUNDG_DEPTH=ON` as the (true) `RECODE_BY_DSSI` value, so `ADD_SOUNDG_DEPTH` was never
/// on, and every chart in production and every fixture was read without it. Spelled with a comma
/// (as RUST_PORT_PLAN.md §4.2 has it) GDAL adds a `DEPTH` field to each `SOUNDG` feature, which
/// P-IN-2 and P-IN-4 reject; `METERS` already carries the depth.
pub const S57_OPTIONS: &str = "RETURN_PRIMITIVES=OFF,RETURN_LINKAGES=OFF,LNAM_REFS=ON,UPDATES=APPLY,SPLIT_MULTIPOINT=ON,RECODE_BY_DSSI=ON";

/// The layers ingest never inserts (`ChartIngest.exLayers`): the DSID metadata record and the
/// spatial primitive layers.
pub const EXCLUDED_LAYERS: [&str; 5] = ["DSID", "IsolatedNode", "ConnectedNode", "Edge", "Face"];

/// GDAL's `RELEASE_NAME`, e.g. `3.13.1`; fixture headers record the version that wrote them.
pub fn gdal_version() -> String {
    ogr::version()
}

/// A failure to open a dataset, or why a cell cannot become a chart (`InsertError.msg`, which
/// ingest reports per chart).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Error(pub String);

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for Error {}

/// A bounding box in degrees (`OGREnvelope`).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Envelope {
    pub west: f64,
    pub south: f64,
    pub east: f64,
    pub north: f64,
}

/// One opened S-57 cell (a `.000` with any `.001…` updates beside it already applied): what
/// ingest needs from it.
///
/// Phase 3/4 ingest calls [`chart_insert`](S57Reader::chart_insert) for the `charts` row,
/// [`total_feature_count`](S57Reader::total_feature_count) for the progress report, then
/// [`features`](S57Reader::features) for each of [`inserted_layers`](S57Reader::inserted_layers),
/// inserting `geometry.make_valid()?.wkb()` with `props`.
pub trait S57Reader {
    /// The `.000` file this cell was opened from.
    fn path(&self) -> &Path;

    /// Every layer in the reader's order (`OgrS57Dataset.layerNames()`), including
    /// [`EXCLUDED_LAYERS`].
    fn layer_names(&self) -> Vec<String>;

    /// `OGR_L_GetFeatureCount` for one layer; `None` when the cell has no such layer.
    fn feature_count(&self, layer: &str) -> Option<u64>;

    /// Every feature of one layer, in reader order; `None` when the cell has no such layer.
    /// Geometries are as read: ingest repairs them with [`Geometry::make_valid`].
    fn features(&self, layer: &str) -> Option<Vec<Feature>>;

    /// [`layer_names`](S57Reader::layer_names) without [`EXCLUDED_LAYERS`].
    fn inserted_layers(&self) -> Vec<String> {
        self.layer_names()
            .into_iter()
            .filter(|l| !EXCLUDED_LAYERS.contains(&l.as_str()))
            .collect()
    }

    /// `featureCount(exLayers)`: the features of every inserted layer, which ingest reports as
    /// `totalFeatures`.
    fn total_feature_count(&self) -> u64 {
        self.inserted_layers()
            .iter()
            .filter_map(|l| self.feature_count(l))
            .sum()
    }

    /// `OgrS57Dataset.chartInsertInfo()` (P-IN-1), with D7 fixed:
    ///
    /// - `name` is `DSID_DSNM`, or the file name when that is missing or empty (Kotlin used
    ///   `""`, so unnamed cells overwrote each other).
    /// - `scale` is `DSPM_CSCL`; `zoom` is its zoom at the centre latitude of the coverage.
    /// - `updated`/`issued` are `DSID_UADT`/`DSID_ISDT` after updates (`""` when missing).
    /// - `covr` is the geometry of the one `CATCOV=1` `M_COVR` feature, or a `MultiPolygon` of
    ///   every polygon of all of them when there are several (Kotlin dropped `MultiPolygon`
    ///   members).
    /// - `dsid_props` is every prop of the first `DSID` feature.
    /// - `chart_txt` maps each sibling `*.TXT` (any case) to its text in the DSSI charset
    ///   (`DSSI_NALL`, else `DSSI_AALL`: 0 ASCII, 1 ISO 8859-1, otherwise UTF-8; invalid UTF-8
    ///   is replaced), sorted by file name.
    ///
    /// The error is the reason ingest reports for the failed chart.
    fn chart_insert(&self) -> Result<ChartInsert, Error> {
        chart::chart_insert(self)
    }
}

/// An S-57 cell read by GDAL's S57 driver under [`S57_OPTIONS`].
pub struct GdalCell {
    path: PathBuf,
    dataset: ogr::Dataset,
}

impl GdalCell {
    /// Opens a `.000` cell; GDAL applies the `.001…` update files found beside it.
    pub fn open(path: impl AsRef<Path>) -> Result<GdalCell, Error> {
        let path = path.as_ref().to_path_buf();
        let dataset = ogr::Dataset::open(&path, "S57")?;
        Ok(GdalCell { path, dataset })
    }
}

impl S57Reader for GdalCell {
    fn path(&self) -> &Path {
        &self.path
    }

    fn layer_names(&self) -> Vec<String> {
        self.dataset.layer_names()
    }

    fn feature_count(&self, layer: &str) -> Option<u64> {
        self.dataset.layer(layer).map(|l| l.feature_count())
    }

    fn features(&self, layer: &str) -> Option<Vec<Feature>> {
        self.dataset
            .layer(layer)
            .map(|mut l| feature::read_layer(&mut l, layer))
    }
}

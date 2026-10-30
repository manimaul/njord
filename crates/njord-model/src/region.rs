//! `GET /v1/regions`: the mobile region archive manifest.

use geojson::{GeoJson, Geometry};
use jiff::Timestamp;
use serde::{Deserialize, Serialize};

/// One configured region. The archive fields stay `null` until the region has been exported.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RegionManifestEntry {
    pub name: String,
    pub description: String,
    /// WKT of the region polygon.
    pub coverage: String,
    pub coverage_geo: GeoJson,
    /// Always a `Point` (Kotlin types it as one).
    #[serde(default)]
    pub label_point: Option<Geometry>,
    pub archive: Option<String>,
    #[serde(default)]
    pub archive_size: Option<i64>,
    #[serde(default)]
    pub archive_sha256: Option<String>,
    #[serde(default, with = "crate::instant::option")]
    pub created_at: Option<Timestamp>,
}

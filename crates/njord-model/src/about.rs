//! `GET /v1/about/version` and `GET /v1/tile_json`.

use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AboutJson {
    pub version: String,
    pub gdal_version: String,
    pub git_hash: String,
    pub git_branch: String,
    pub build_date: String,
}

/// TileJSON 2.2.0 for the chart vector tile source.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct TileJson {
    pub bounds: Vec<f64>,
    pub format: String,
    pub maxzoom: f32,
    pub minzoom: f32,
    pub scheme: String,
    pub tilejson: String,
    /// e.g. `http://localhost:9000/v1/tile/{z}/{x}/{y}`
    pub tiles: Vec<String>,
}

impl Default for TileJson {
    fn default() -> Self {
        Self {
            bounds: vec![-180.0, -85.05112877980659, 180.0, 85.0511287798066],
            format: "pbf".into(),
            maxzoom: 30.0,
            minzoom: 0.0,
            scheme: "xyz".into(),
            tilejson: "2.2.0".into(),
            tiles: Vec::new(),
        }
    }
}

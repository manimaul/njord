//! Charts: `/v1/chart`, `/v1/chart_catalog`, `/v1/chart_editions`, uploads and the tile cache.

use serde::{Deserialize, Serialize};

use crate::{IndexMap, JsonObject};
use geojson::{Bbox, Feature, Geometry};

/// An ingested chart (`GET /v1/chart?name=`).
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Chart {
    pub name: String,
    pub scale: i32,
    #[serde(rename = "file_name")]
    pub file_name: String,
    pub updated: String,
    pub issued: String,
    pub zoom: i32,
    pub covr: Feature,
    pub bounds: Option<Bbox>,
    pub layers: Vec<String>,
    #[serde(rename = "dsid_props")]
    pub dsid_props: JsonObject,
    #[serde(rename = "chart_txt")]
    pub chart_txt: IndexMap<String, String>,
    pub feature_count: i32,
}

/// Body of `POST /v1/chart`, and the chart row ingest writes.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ChartInsert {
    pub name: String,
    pub scale: i32,
    pub file_name: String,
    pub updated: String,
    pub issued: String,
    pub zoom: i32,
    pub covr: Feature,
    pub dsid_props: JsonObject,
    pub chart_txt: IndexMap<String, String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ChartItem {
    pub name: String,
}

/// One page of `GET /v1/chart_catalog`. `next_name` is the name to start the next page at: the
/// catalog is paged by `charts.name`, the primary key, so the cursor is lexicographic rather than
/// monotonic. `None` once the last page has been served.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartCatalog {
    pub total_chart_count: i32,
    pub next_name: Option<String>,
    pub page: Vec<ChartItem>,
}

/// `GET /v1/chart_editions`: every ingested chart keyed by `charts.name` (e.g. `US5WA22M.000`),
/// valued by an opaque revision key `"<DSID_UPDN>:<DSID_UADT>:<DSID_ISDT>"`
/// (e.g. `"6:20251209:20260520"`).
///
/// `enc_cron` rebuilds the same key from NOAA's `ENCProdCat_19115.xml` and re-downloads any cell
/// whose key differs. Keys are compared as opaque strings and never ordered. `DSID_EDTN` is
/// deliberately not part of the key: GDAL overwrites it with the value from the last applied
/// update file, which NOAA publishes as `0` for some cells.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct ChartEditions {
    pub editions: IndexMap<String, String>,
}

/// One feature of `GET /v1/tile/{z}/{x}/{y}?info=true`.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ChartFeatureInfo {
    pub layer: String,
    pub props: JsonObject,
    pub geom: Option<Geometry>,
}

/// `POST /v1/enc_save` / `POST /v1/enc_url` response: the zip saved for ingest.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EncUpload {
    pub zip_file: String,
}

/// Body of `POST /v1/enc_url`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct EncUrlRequest {
    pub url: String,
}

/// `GET /v1/cache`: zoom level → number of cached `.mvt` tiles.
pub type TileCacheCounts = IndexMap<i32, i32>;

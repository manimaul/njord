//! Features: `/v1/feature/…` and `/v1/geojson`.

use serde::{Deserialize, Serialize};

use crate::JsonObject;
use crate::chart::Chart;
use geojson::GeoJson;

/// `GET /v1/feature/lnam/{lnam}`.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FeatureRecord {
    pub id: i64,
    pub layer: String,
    pub props: JsonObject,
    pub geom: GeoJson,
    #[serde(rename = "chart_name")]
    pub chart_name: String,
    pub zoom_min: i32,
    pub zoom_max: i32,
}

/// `GET /v1/feature/layer/{layer}?start_id=`: one page, resumed from `last_id`.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LayerQueryResultPage {
    pub last_id: i64,
    pub items: Vec<LayerQueryResult>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LayerQueryResult {
    pub id: i64,
    pub lat: f64,
    pub lng: f64,
    pub zoom: f32,
    pub props: JsonObject,
    pub chart_name: String,
    pub geom_type: String,
}

/// A feature inserted by `POST /v1/geojson?chart_name=&layer_name=`.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct FeatureInsert {
    pub layer_name: String,
    pub chart: Chart,
    pub geo: GeoJson,
}

/// Kotlin's `Pair`, which the server responds with directly: `POST /v1/geojson` returns
/// `Pair<Int, FeatureInsert>` (rows inserted, the insert).
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Pair<A, B> {
    pub first: A,
    pub second: B,
}

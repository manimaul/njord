//! `GET /v1/geojson?chart_name=&layer_name=` (`GeoJsonHandler.kt`).

use std::sync::Arc;

use axum::Json;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::query_param;
use crate::state::AppState;

/// One layer of one chart as a GeoJSON `FeatureCollection` (empty for an unknown chart or
/// layer); an empty 404 when a parameter is missing or the database fails.
///
/// D14: Kotlin cannot decode the rows of a layer that has features and answers 404 for it. The
/// port serves them: each feature is `row_to_json` of `(id, layer, type, geometry, properties)`,
/// as Kotlin's query builds it, so it carries the numeric row `id` and a `layer` member (golden
/// `api_04_charts/geojson_existing_layer`, `port`).
pub async fn geojson(
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let (Some(chart), Some(layer)) = (
        query_param(&query, "chart_name"),
        query_param(&query, "layer_name"),
    ) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    match state.db.geojson_layer(chart, layer).await {
        Ok(collection) => Json(collection).into_response(),
        Err(e) => {
            tracing::error!("geojson {chart} {layer}: {e}");
            StatusCode::NOT_FOUND.into_response()
        }
    }
}

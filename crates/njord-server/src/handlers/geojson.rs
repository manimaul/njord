//! `GET/POST /v1/geojson?chart_name=&layer_name=`: one chart layer as GeoJSON, and inserting
//! one.

use std::sync::Arc;

use axum::Json;
use axum::extract::rejection::JsonRejection;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::{FeatureInsert, Pair};

use super::{Signed, query_param};
use crate::state::AppState;

/// `POST`, signed: inserts the GeoJSON body into `chart_name` as `layer_name`.
///
/// Answers a [`Pair`] of the rows inserted and the `FeatureInsert`. 400 when a parameter is
/// missing, the chart is unknown or the body is not GeoJSON; 500 when nothing was inserted.
#[utoipa::path(post, path = "/v1/geojson", tag = "charts", operation_id = "insert_geojson",
    summary = "Insert GeoJSON features into a chart layer",
    description = "Answers the number of rows inserted (`first`) and the insert (`second`).",
    params(
        ("chart_name" = String, Query, example = "US5WA22M.000", description = "An existing chart's `DSID_DSNM`"),
        ("layer_name" = String, Query, example = "DEPARE", description = "The S-57 object class acronym")),
    request_body(content = Object, content_type = "application/json", description = "A GeoJSON object"),
    responses(
        (status = 200, description = "Rows inserted and the insert", body = Pair<i32, FeatureInsert>),
        (status = 400, description = "A parameter missing, the chart unknown, or the body not GeoJSON"),
        (status = 401, description = "No valid admin signature"),
        (status = 500, description = "Nothing was inserted")),
    security(("admin_signature" = [])))]
pub async fn insert(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
    body: Result<Json<geojson::GeoJson>, JsonRejection>,
) -> Response {
    let Ok(Json(geo)) = body else {
        return super::bad_request_text(
            "Failed to convert request body to class io.madrona.njord.geojson.GeoJsonObject",
        );
    };
    let (Some(chart_name), Some(layer_name)) = (
        query_param(&query, "chart_name"),
        query_param(&query, "layer_name"),
    ) else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let chart = match state.db.find_chart(chart_name).await {
        Ok(Some(chart)) => chart,
        Ok(None) => return StatusCode::BAD_REQUEST.into_response(),
        Err(e) => {
            tracing::error!("geojson insert {chart_name}: {e}");
            return StatusCode::BAD_REQUEST.into_response();
        }
    };
    let insert = FeatureInsert {
        layer_name: layer_name.to_owned(),
        chart,
        geo,
    };
    match state.db.geojson_insert(&insert).await {
        Ok(count) if count > 0 => Json(Pair {
            first: count as i64,
            second: insert,
        })
        .into_response(),
        Ok(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
        Err(e) => {
            tracing::error!("geojson insert {chart_name} {layer_name}: {e}");
            StatusCode::BAD_REQUEST.into_response()
        }
    }
}

/// `GET`: one layer of one chart as a GeoJSON `FeatureCollection` (empty for an unknown chart or
/// layer); an empty 404 when a parameter is missing or the database fails.
///
/// Each feature is `row_to_json` of `(id, layer, type, geometry, properties)`, so it carries the
/// numeric row `id` and a `layer` member (golden `api_04_charts/geojson_existing_layer`, its
/// `port` expectation).
#[utoipa::path(get, path = "/v1/geojson", tag = "charts", operation_id = "geojson",
    summary = "One layer of one chart as GeoJSON",
    description = "A `FeatureCollection`, empty for an unknown chart or layer. Each feature carries \
the numeric row `id` and a `layer` member.",
    params(
        ("chart_name" = String, Query, example = "US5WA22M.000", description = "The chart's `DSID_DSNM`"),
        ("layer_name" = String, Query, example = "DEPARE", description = "The S-57 object class acronym")),
    responses(
        (status = 200, description = "The `FeatureCollection`", body = Object),
        (status = 404, description = "A parameter missing, or a database failure")))]
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

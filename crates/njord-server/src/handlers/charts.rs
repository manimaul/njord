//! `GET /v1/chart?name=` (`ChartHandler.kt`), `GET /v1/chart_catalog[?name=]`
//! (`ChartCatalogHandler.kt`) and `GET /v1/chart_editions` (`ChartEditionsHandler.kt`).

use std::sync::Arc;

use axum::Json;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::query_param;
use crate::state::AppState;

/// The chart named `name` with its layers and feature count; an empty 404 when `name` is
/// missing or unknown, or the database fails (Kotlin's DAO answers null on error).
pub async fn chart(
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let Some(name) = query_param(&query, "name") else {
        return StatusCode::NOT_FOUND.into_response();
    };
    match state.db.find_chart(name).await {
        Ok(Some(chart)) => Json(chart).into_response(),
        Ok(None) => StatusCode::NOT_FOUND.into_response(),
        Err(e) => {
            tracing::error!("chart {name}: {e}");
            StatusCode::NOT_FOUND.into_response()
        }
    }
}

/// One page of chart names from `name` (the previous page's `nextName`) on; an empty 500 when
/// the database fails.
pub async fn chart_catalog(
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    match state.db.list_charts(query_param(&query, "name")).await {
        Ok(catalog) => Json(catalog).into_response(),
        Err(e) => {
            tracing::error!("chart catalog: {e}");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

/// Every chart's `"<DSID_UPDN>:<updated>:<issued>"`, unpaginated (the `enc_cron` diff); an empty
/// 500 when the database fails.
pub async fn chart_editions(State(state): State<Arc<AppState>>) -> Response {
    match state.db.chart_editions().await {
        Ok(editions) => Json(editions).into_response(),
        Err(e) => {
            tracing::error!("chart editions: {e}");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

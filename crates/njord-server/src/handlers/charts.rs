//! `GET/POST/DELETE /v1/chart` (`ChartHandler.kt`), `GET /v1/chart_catalog[?name=]`
//! (`ChartCatalogHandler.kt`) and `GET /v1/chart_editions` (`ChartEditionsHandler.kt`).

use std::sync::Arc;

use axum::Json;
use axum::extract::rejection::JsonRejection;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::ChartInsert;

use super::{Signed, query_param};
use crate::regions::WORLD_REGION_NAME;
use crate::state::AppState;

/// `POST /v1/chart`, signed: inserts the chart row, replacing one of the same name and its
/// features (one transaction, D5), and answers the row; 400 when the insert fails or the body is
/// not a `ChartInsert` (Ktor's text).
pub async fn insert(
    _: Signed,
    State(state): State<Arc<AppState>>,
    body: Result<Json<ChartInsert>, JsonRejection>,
) -> Response {
    let Ok(Json(chart)) = body else {
        return super::bad_request_text(
            "Failed to convert request body to class io.madrona.njord.model.ChartInsert",
        );
    };
    match state.db.insert_chart(&chart, true).await {
        Ok(inserted) => Json(inserted).into_response(),
        Err(e) => {
            tracing::error!("chart insert {}: {e}", chart.name);
            StatusCode::BAD_REQUEST.into_response()
        }
    }
}

/// `DELETE /v1/chart?name=`, signed: deletes the chart (its `DSID_DSNM`, the key `enc_cron`
/// diffs) and its features, then everything that would keep serving it: the tile cache, and the
/// export state of every non-`WORLD` region whose coverage it intersects (resolved before the
/// delete, while the coverage still exists), waking the export worker. 202 when a chart was
/// deleted, 204 when none had that name, 400 without `name`.
pub async fn delete(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> StatusCode {
    let Some(name) = query_param(&query, "name") else {
        return StatusCode::BAD_REQUEST;
    };
    let regions: Vec<(String, String)> = state
        .config
        .region_exports
        .iter()
        .filter(|r| r.name != WORLD_REGION_NAME)
        .map(|r| (r.name.clone(), r.coverage.clone()))
        .collect();
    let containing = match state.db.regions_containing_chart(name, &regions).await {
        Ok(regions) => regions,
        Err(e) => {
            tracing::warn!("regions containing {name}: {e}");
            Vec::new()
        }
    };
    match state.db.delete_chart(name).await {
        Ok(true) => {
            state.tile_cache.clear().await;
            for region in &containing {
                if let Err(e) = state.db.clear_region_export_state(region).await {
                    tracing::warn!("clear export state {region}: {e}");
                }
            }
            if !containing.is_empty() {
                state.wake_region_worker();
            }
            StatusCode::ACCEPTED
        }
        Ok(false) => StatusCode::NO_CONTENT,
        Err(e) => {
            tracing::error!("chart delete {name}: {e}");
            StatusCode::BAD_REQUEST
        }
    }
}

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

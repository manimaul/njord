//! Charts: `GET/POST/DELETE /v1/chart`, `GET /v1/chart_catalog[?name=]` and
//! `GET /v1/chart_editions`.

use std::sync::Arc;

use axum::Json;
use axum::extract::rejection::JsonRejection;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::ChartInsert;

use njord_db::NewChangeEvent;
use njord_model::{ChangeCause, ChangeKind, ChangeOutcome};

use super::{Signed, query_param};
use crate::regions::WORLD_REGION_NAME;
use crate::state::AppState;

/// `POST /v1/chart`, signed: inserts a chart row and answers it.
///
/// A chart of the same name is replaced with its features, in one transaction. 400 when the
/// insert fails or the body is not a `ChartInsert` (with a text message,
/// [`super::bad_request_text`]).
///
/// The change is recorded as an Admin `new` (change 0: the row has no features) or `update`
/// (`−before`); a failed insert as an `error`.
#[utoipa::path(post, path = "/v1/chart", tag = "charts", operation_id = "insert_chart",
    summary = "Insert a chart row",
    description = "A chart of the same name is replaced, with its features, in one transaction. The \
change is recorded in the change history as `new` or `update` by `admin`.",
    request_body = ChartInsert,
    responses(
        (status = 200, description = "The chart as stored", body = njord_model::Chart),
        (status = 400, description = "The body is not a `ChartInsert` (text message), or the insert failed (empty)"),
        (status = 401, description = "No valid admin signature")),
    security(("admin_signature" = [])))]
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
    match state.db.insert_chart_tracked(&chart, true).await {
        Ok((inserted, before)) => {
            let event = NewChangeEvent::installed(
                ChangeKind::Chart,
                &inserted.name,
                before,
                0,
                ChangeCause::Admin,
            );
            record(&state, &event).await;
            Json(inserted).into_response()
        }
        Err(e) => {
            tracing::error!("chart insert {}: {e}", chart.name);
            let before = state
                .db
                .chart_feature_count(&chart.name)
                .await
                .unwrap_or_default();
            let event = NewChangeEvent::install_failed(
                ChangeKind::Chart,
                &chart.name,
                before,
                before.unwrap_or(0),
                ChangeCause::Admin,
                &e.to_string(),
            );
            record(&state, &event).await;
            StatusCode::BAD_REQUEST.into_response()
        }
    }
}

/// Writes `event` to the change history; a failure is logged, never answered.
async fn record(state: &AppState, event: &NewChangeEvent) {
    if let Err(e) = state.db.record_change_event(event).await {
        tracing::error!("change event {} {}: {e}", event.outcome, event.name);
    }
}

/// The `cause` query parameter: `admin` when absent, `None` for an unknown value.
fn cause_param(query: &[(String, String)]) -> Option<ChangeCause> {
    match query_param(query, "cause") {
        None => Some(ChangeCause::Admin),
        Some(value) => value.parse().ok(),
    }
}

/// `DELETE /v1/chart?name=&cause=`, signed: deletes a chart and everything that would keep
/// serving it.
///
/// The chart is named by its `DSID_DSNM`, the key `enc_cron` diffs. Its features go with it,
/// then the tile cache and the export state of every non-`WORLD` region whose coverage it
/// intersects (resolved before the delete, while the coverage still exists), and the export
/// worker is woken. 202 when a chart was deleted, 204 when none had that name, 400 without
/// `name` or with a `cause` that is not `admin` or `noaa_enc_cron`.
///
/// The delete is recorded in the change history with `cause` (`admin` when absent, which is
/// what `njord-enc-cron` overrides); a database failure is recorded as an `error`, and a miss
/// (204) is not recorded.
#[utoipa::path(delete, path = "/v1/chart", tag = "charts", operation_id = "delete_chart",
    summary = "Delete a chart and everything that would keep serving it",
    description = "Its features go with it, then the tile cache and the export state of every region \
whose coverage it intersects, and the region export worker is woken. The delete is recorded in the \
change history with `cause`.",
    params(
        ("name" = String, Query, example = "US5WA22M.000", description = "The chart's `DSID_DSNM`"),
        ("cause" = Option<String>, Query, description = "`admin` (default) or `noaa_enc_cron`")),
    responses(
        (status = 202, description = "Deleted"),
        (status = 204, description = "No chart had that name"),
        (status = 400, description = "`name` missing, or `cause` unknown"),
        (status = 401, description = "No valid admin signature")),
    security(("admin_signature" = [])))]
pub async fn delete(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> StatusCode {
    let Some(name) = query_param(&query, "name") else {
        return StatusCode::BAD_REQUEST;
    };
    let Some(cause) = cause_param(&query) else {
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
    match state.db.delete_chart(name, Some(cause)).await {
        Ok(Some(_)) => {
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
        Ok(None) => StatusCode::NO_CONTENT,
        Err(e) => {
            tracing::error!("chart delete {name}: {e}");
            let event = NewChangeEvent::failed(
                ChangeKind::Chart,
                name,
                ChangeOutcome::Delete,
                cause,
                &e.to_string(),
            );
            record(&state, &event).await;
            StatusCode::BAD_REQUEST
        }
    }
}

/// `GET /v1/chart?name=`: the chart named `name` with its layers and feature count.
///
/// An empty 404 when `name` is missing or unknown, or the database fails: a query error is
/// answered like a missing chart.
#[utoipa::path(get, path = "/v1/chart", tag = "charts", operation_id = "chart",
    summary = "One chart with its layers and feature count",
    params(("name" = String, Query, example = "US5WA22M.000", description = "The chart's `DSID_DSNM`")),
    responses(
        (status = 200, description = "The chart", body = njord_model::Chart),
        (status = 404, description = "`name` missing or unknown (also a database failure)")))]
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

/// `GET /v1/chart_catalog[?name=]`: one page of chart names from `name` (the previous page's
/// `nextName`) on; an empty 500 when the database fails.
#[utoipa::path(get, path = "/v1/chart_catalog", tag = "charts", operation_id = "chart_catalog",
    summary = "One page of chart names",
    description = "The catalog is paged by name: pass the previous page's `nextName` to get the next \
page, until it is `null`.",
    params(("name" = Option<String>, Query, description = "The name to start the page at (the previous page's `nextName`)")),
    responses(
        (status = 200, description = "The page", body = njord_model::ChartCatalog),
        (status = 500, description = "Database failure")))]
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

/// `GET /v1/chart_editions`: every chart's `"<DSID_UPDN>:<updated>:<issued>"`, unpaginated (the
/// `enc_cron` diff); an empty 500 when the database fails.
#[utoipa::path(get, path = "/v1/chart_editions", tag = "charts", operation_id = "chart_editions",
    summary = "Every chart's edition key",
    description = "Unpaginated: every chart's `\"<DSID_UPDN>:<updated>:<issued>\"`, the key the NOAA \
update job diffs against NOAA's catalog.",
    responses(
        (status = 200, description = "The editions", body = njord_model::ChartEditions),
        (status = 500, description = "Database failure")))]
pub async fn chart_editions(State(state): State<Arc<AppState>>) -> Response {
    match state.db.chart_editions().await {
        Ok(editions) => Json(editions).into_response(),
        Err(e) => {
            tracing::error!("chart editions: {e}");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

//! `GET /v1/feature/{by}/{arg}`: feature lookup by layer (`layer/{layer}?start_id=`) or by
//! `LNAM` (`lnam/{lnam}`).

use std::sync::Arc;

use axum::Json;
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::query_param;
use crate::state::AppState;

/// Looks a feature up by layer or by `LNAM`, as `by` says.
///
/// - `layer/{layer}`: the next page of the layer's feature positions after id `start_id`
///   (default and fallback 0); an unknown layer is an empty page.
/// - `lnam/{lnam}`: the feature with that `LNAM`.
///
/// Anything else, an unknown LNAM or a database failure is an empty 404.
pub async fn feature(
    State(state): State<Arc<AppState>>,
    Path((by, arg)): Path<(String, String)>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let not_found = || StatusCode::NOT_FOUND.into_response();
    match by.as_str() {
        "layer" => {
            let start_id = query_param(&query, "start_id")
                .and_then(|s| s.parse::<i64>().ok())
                .unwrap_or(0);
            match state.db.find_layer_positions_page(&arg, start_id).await {
                Ok(page) => Json(page).into_response(),
                Err(e) => {
                    tracing::error!("feature layer {arg}: {e}");
                    not_found()
                }
            }
        }
        "lnam" => match state.db.find_feature(&arg).await {
            Ok(Some(record)) => Json(record).into_response(),
            Ok(None) => not_found(),
            Err(e) => {
                tracing::error!("feature lnam {arg}: {e}");
                not_found()
            }
        },
        _ => not_found(),
    }
}

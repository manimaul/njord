//! `GET/DELETE /v1/cache`: the on-disk tile cache's counts, and clearing it.

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use axum::http::StatusCode;
use njord_model::TileCacheCounts;

use super::Signed;
use crate::state::AppState;

/// `DELETE`, signed: empties the tile cache; 204.
#[utoipa::path(delete, path = "/v1/cache", tag = "tiles", operation_id = "clear_cache",
    summary = "Empty the tile cache",
    responses((status = 204, description = "Emptied"), (status = 401, description = "No valid admin signature")),
    security(("admin_signature" = [])))]
pub async fn clear(_: Signed, State(state): State<Arc<AppState>>) -> StatusCode {
    state.tile_cache.clear().await;
    StatusCode::NO_CONTENT
}

/// The number of cached tiles per zoom level, `{"<z>": <count>}`.
#[utoipa::path(get, path = "/v1/cache", tag = "tiles", operation_id = "cache",
    summary = "Cached tiles per zoom level",
    description = "Zoom level to the number of cached `.mvt` tiles (`TileCacheCounts`).",
    responses((status = 200, description = "Zoom level to tile count", body = njord_model::IndexMap<i32, i32>)))]
pub async fn cache(State(state): State<Arc<AppState>>) -> Json<TileCacheCounts> {
    let state = state.clone();
    Json(
        tokio::task::spawn_blocking(move || state.tile_cache.counts())
            .await
            .unwrap_or_default(),
    )
}

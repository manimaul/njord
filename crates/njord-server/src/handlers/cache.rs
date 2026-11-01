//! `GET/DELETE /v1/cache`: the on-disk tile cache's counts, and clearing it.

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use axum::http::StatusCode;
use njord_model::TileCacheCounts;

use super::Signed;
use crate::state::AppState;

/// `DELETE`, signed: empties the tile cache; 204.
pub async fn clear(_: Signed, State(state): State<Arc<AppState>>) -> StatusCode {
    state.tile_cache.clear().await;
    StatusCode::NO_CONTENT
}

/// The number of cached tiles per zoom level, `{"<z>": <count>}`.
pub async fn cache(State(state): State<Arc<AppState>>) -> Json<TileCacheCounts> {
    let state = state.clone();
    Json(
        tokio::task::spawn_blocking(move || state.tile_cache.counts())
            .await
            .unwrap_or_default(),
    )
}

//! `GET /v1/cache` (`CacheHandler.kt`).

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use njord_model::TileCacheCounts;

use crate::state::AppState;

/// The number of cached tiles per zoom level, `{"<z>": <count>}`.
pub async fn cache(State(state): State<Arc<AppState>>) -> Json<TileCacheCounts> {
    let state = state.clone();
    Json(
        tokio::task::spawn_blocking(move || state.tile_cache.counts())
            .await
            .unwrap_or_default(),
    )
}

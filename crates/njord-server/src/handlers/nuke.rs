//! `DELETE /v1/nuke` (`NukeHandler.kt`), signed: truncates charts, features and base features,
//! clears the tile cache and deletes every region archive. 204, or 500 when the truncate fails.

use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;

use super::Signed;
use crate::state::AppState;

pub async fn nuke(_: Signed, State(state): State<Arc<AppState>>) -> StatusCode {
    if let Err(e) = state.db.truncate_all().await {
        tracing::error!("nuke: {e}");
        return StatusCode::INTERNAL_SERVER_ERROR;
    }
    state.tile_cache.clear().await;
    let regions = state.clone();
    let _ = tokio::task::spawn_blocking(move || regions.regions.clear()).await;
    StatusCode::NO_CONTENT
}

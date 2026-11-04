//! `DELETE /v1/nuke`, signed: truncates charts, features and base features,
//! clears the tile cache and deletes every region archive. 204, or 500 when the truncate fails.
//!
//! The change history is kept: the nuke is recorded there as one `nuke` event carrying the
//! features it removed (or an `error` attempting `nuke`), after the earlier events.

use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;
use njord_db::NewChangeEvent;
use njord_model::{ChangeCause, ChangeKind, ChangeOutcome};

use super::Signed;
use crate::state::AppState;

pub async fn nuke(_: Signed, State(state): State<Arc<AppState>>) -> StatusCode {
    let event = match state.db.truncate_all().await {
        Ok(before) => NewChangeEvent::nuked(before),
        Err(e) => {
            tracing::error!("nuke: {e}");
            let event = NewChangeEvent::failed(
                ChangeKind::All,
                NewChangeEvent::NUKE_NAME,
                ChangeOutcome::Nuke,
                ChangeCause::Admin,
                &e.to_string(),
            );
            if let Err(e) = state.db.record_change_event(&event).await {
                tracing::error!("nuke change event: {e}");
            }
            return StatusCode::INTERNAL_SERVER_ERROR;
        }
    };
    if let Err(e) = state.db.record_change_event(&event).await {
        tracing::error!("nuke change event: {e}");
    }
    state.tile_cache.clear().await;
    let regions = state.clone();
    let _ = tokio::task::spawn_blocking(move || regions.regions.clear()).await;
    StatusCode::NO_CONTENT
}

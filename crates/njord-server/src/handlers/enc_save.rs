//! `POST /v1/enc_save?filename=` and `DELETE /v1/enc_save`, signed: uploading a zip for ingest,
//! and the operator's reset.

use std::path::Path;
use std::sync::Arc;

use axum::Json;
use axum::body::Bytes;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::{ChangeCause, EncUpload, WsMsg};

use super::{Signed, query_param};
use crate::ingest::cause;
use crate::state::AppState;

/// Whether `name` is a file name an upload may be saved under.
///
/// A bare name only: not empty, no `/`, `\` or NUL, not `.` or `..`, ending in `.zip` in any
/// case, and not ending in `.zip.tmp`'s inner form (`x.zip.tmp` is refused because it does not
/// end in `.zip`). The cases are pinned by `testdata/security/paths.json` (`upload_filename`).
pub fn valid_upload_name(name: &str) -> bool {
    !name.is_empty()
        && !name.contains(['/', '\\', '\0'])
        && name != "."
        && name != ".."
        && name.len() > 4
        && name.to_ascii_lowercase().ends_with(".zip")
}

/// `POST /v1/enc_save?filename=`: stores the body as `<save>/<filename>` for the ingest worker.
///
/// The body is written to `<filename>.tmp` and renamed, so the ingest worker never claims a
/// partial upload; the `admin` cause sidecar ([`crate::ingest::cause`]) is written before the
/// rename. Answers `{"zipFile": filename}`.
///
/// The upload does not clear the lock, so an ingest in flight continues and this zip waits its
/// turn; `Extracting(0)` is announced only when nothing is running. A missing `filename` is
/// 400, as is a name [`valid_upload_name`] refuses.
pub async fn upload(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
    body: Bytes,
) -> Response {
    let Some(name) = query_param(&query, "filename") else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !valid_upload_name(name) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let dest = state.save_dir.join(name);
    let tmp = state.save_dir.join(format!("{name}.tmp"));
    let result = async {
        tokio::fs::create_dir_all(&state.save_dir).await?;
        tokio::fs::write(&tmp, &body).await?;
        cause::write_sidecar(&state.save_dir, name, ChangeCause::Admin).await?;
        if state.lock.is_free() {
            state
                .status
                .write_ignore_lock(&WsMsg::Extracting { progress: 0.0 });
        }
        tokio::fs::rename(&tmp, &dest).await
    };
    match result.await {
        Ok(()) => Json(EncUpload {
            zip_file: name.to_owned(),
        })
        .into_response(),
        Err(e) => {
            tracing::error!("enc_save {name}: {e}");
            let _ = tokio::fs::remove_file(&tmp).await;
            let _ = tokio::fs::remove_file(cause::sidecar_path(&state.save_dir, name)).await;
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

/// `DELETE /v1/enc_save`: the operator's reset.
///
/// Clears the lock (aborting any ingest in flight, whoever holds it), empties `save/` (the
/// cause sidecars with the zips) and `ingest/`, writes `Idle`, answers 202. 200 when the lock
/// could not be cleared, with nothing else changed (the status the existing clients expect).
pub async fn reset(_: Signed, State(state): State<Arc<AppState>>) -> StatusCode {
    if !state.lock.try_clear() {
        return StatusCode::OK;
    }
    recreate(&state.save_dir).await;
    recreate(&state.ingest_dir).await;
    state.status.write_ignore_lock(&WsMsg::Idle);
    StatusCode::ACCEPTED
}

async fn recreate(dir: &Path) {
    if let Err(e) = tokio::fs::remove_dir_all(dir).await
        && e.kind() != std::io::ErrorKind::NotFound
    {
        tracing::warn!("{}: {e}", dir.display());
    }
    if let Err(e) = tokio::fs::create_dir_all(dir).await {
        tracing::warn!("{}: {e}", dir.display());
    }
}

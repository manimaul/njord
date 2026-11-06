//! `POST /v1/enc_url`, signed: downloads a zip from a URL into `save/` in
//! the background and answers 202 with the name it will have.
//!
//! A non-2xx response is never saved (`error_for_status`), and the completed temp file must
//! start with the zip magic before it is renamed to `<name>.zip`; otherwise it is deleted and the
//! failure logged, so an error page never reaches the ingest worker. The `admin` cause sidecar
//! ([`crate::ingest::cause`]) is written before the rename.

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use axum::extract::rejection::JsonRejection;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use futures_util::StreamExt;
use njord_model::{ChangeCause, EncUpload, EncUrlRequest};
use tokio::io::AsyncWriteExt;

use super::Signed;
use crate::ingest::cause;
use crate::state::AppState;

/// The name a download is saved under: the URL's last path segment, without a query string,
/// when it ends in `.zip`, else `chart_<epoch millis>.zip`.
pub fn file_name_for(url: &str) -> String {
    let name = url.rsplit('/').next().unwrap_or("");
    let name = name.split('?').next().unwrap_or("");
    if !name.trim().is_empty() && name.to_ascii_lowercase().ends_with(".zip") {
        name.to_owned()
    } else {
        format!("chart_{}.zip", jiff::Timestamp::now().as_millisecond())
    }
}

/// Starts downloading the zip at the body's `url` and answers 202 with the name it will be
/// saved under.
///
/// A body that is not an `EncUrlRequest` is a 400 with a text message
/// ([`super::bad_request_text`]).
#[utoipa::path(post, path = "/v1/enc_url", tag = "ingest", operation_id = "download_enc",
    summary = "Download an ENC bundle from a URL for ingest",
    description = "Starts downloading the zip at `url` into `save/` and answers at once with the name \
it will be saved under: the URL's last path segment when it ends in `.zip`, else `chart_<epoch millis>.zip`.",
    request_body = EncUrlRequest,
    responses(
        (status = 202, description = "The name the zip will be saved under", body = EncUpload),
        (status = 400, description = "The body is not an `EncUrlRequest`", content_type = "text/plain", body = String),
        (status = 401, description = "No valid admin signature")),
    security(("admin_signature" = [])))]
pub async fn download(
    _: Signed,
    State(state): State<Arc<AppState>>,
    body: Result<Json<EncUrlRequest>, JsonRejection>,
) -> Response {
    let Ok(Json(request)) = body else {
        return super::bad_request_text(
            "Failed to convert request body to class io.madrona.njord.model.EncUrlRequest",
        );
    };
    let name = file_name_for(&request.url);
    let state = state.clone();
    let zip_file = name.clone();
    tokio::spawn(async move {
        if let Err(e) = fetch(&state, &request.url, &name).await {
            tracing::error!("failed to download {}: {e}", request.url);
        }
    });
    (StatusCode::ACCEPTED, Json(EncUpload { zip_file })).into_response()
}

/// Whether `head` starts with a zip magic: `PK\x03\x04` (an entry) or `PK\x05\x06` (an empty
/// archive).
fn looks_like_zip(head: &[u8]) -> bool {
    head.starts_with(b"PK\x03\x04") || head.starts_with(b"PK\x05\x06")
}

async fn fetch(state: &AppState, url: &str, name: &str) -> Result<(), String> {
    tracing::info!("downloading {url} -> {name}");
    tokio::fs::create_dir_all(&state.save_dir)
        .await
        .map_err(|e| e.to_string())?;
    let tmp = state.save_dir.join(format!("{name}.tmp"));
    let result = async {
        let response = state
            .http
            .get(url)
            .send()
            .await
            .and_then(|r| r.error_for_status())
            .map_err(|e| e.to_string())?;
        let content_length = response.content_length();
        let mut stream = response.bytes_stream();
        let mut file = tokio::fs::File::create(&tmp)
            .await
            .map_err(|e| e.to_string())?;
        let mut total: u64 = 0;
        let mut head = Vec::with_capacity(4);
        let mut last_logged: i64 = -1;
        while let Some(chunk) = stream.next().await {
            let chunk = chunk.map_err(|e| e.to_string())?;
            if head.len() < 4 {
                head.extend_from_slice(&chunk[..chunk.len().min(4 - head.len())]);
            }
            file.write_all(&chunk).await.map_err(|e| e.to_string())?;
            total += chunk.len() as u64;
            if let Some(len) = content_length.filter(|&l| l > 0) {
                let pct = (total * 100 / len) as i64 / 10 * 10;
                if pct > last_logged {
                    tracing::info!("downloading {name}: {pct}%");
                    last_logged = pct;
                }
            }
        }
        file.flush().await.map_err(|e| e.to_string())?;
        drop(file);
        if !looks_like_zip(&head) {
            return Err("response is not a zip archive".to_owned());
        }
        cause::write_sidecar(&state.save_dir, name, ChangeCause::Admin)
            .await
            .map_err(|e| e.to_string())?;
        tokio::fs::rename(&tmp, state.save_dir.join(name))
            .await
            .map_err(|e| e.to_string())?;
        tracing::info!("download complete: {name} ({total} bytes)");
        Ok(())
    };
    let outcome = result.await;
    if outcome.is_err() {
        let _ = tokio::fs::remove_file(&tmp).await;
        let _ = tokio::fs::remove_file(cause::sidecar_path(&state.save_dir, name)).await;
    }
    outcome
}

//! The web app: every request no route matches is answered from `webStaticContent`.
//!
//! This holds whatever the method, and an unknown path gets `index.html`, so deep links into the
//! single-page app resolve. There is no 404 for an unknown path.

use std::sync::Arc;

use axum::extract::State;
use axum::http::{HeaderValue, Uri, header};
use axum::response::{IntoResponse, Response};

use super::with_content_type;
use crate::state::AppState;

/// Answers a request no route matched with a file of the web app, or `index.html`.
///
/// The file is the one in `webStaticContent` named by the text after the request URI's last `/`
/// (query string included, nothing decoded, so only top-level files are reachable), with a content
/// type from its extension (`application/octet-stream` when unknown); otherwise `index.html` as
/// `text/html; charset=UTF-8` with status 200, an empty body when there is no `index.html`.
pub async fn fallback(State(state): State<Arc<AppState>>, uri: Uri) -> Response {
    let target = uri.path_and_query().map(|p| p.as_str()).unwrap_or("/");
    let name = target.rsplit('/').next().unwrap_or_default();
    let root = &state.config.web_static_content;
    if !name.is_empty() {
        let path = root.join(name);
        if tokio::fs::metadata(&path).await.is_ok_and(|m| m.is_file())
            && let Ok(bytes) = tokio::fs::read(&path).await
        {
            let mime = mime_guess::from_path(name).first_or_octet_stream();
            let mut response = bytes.into_response();
            if let Ok(value) = HeaderValue::from_str(mime.as_ref()) {
                response.headers_mut().insert(header::CONTENT_TYPE, value);
            }
            return response;
        }
    }
    let index = tokio::fs::read(root.join("index.html"))
        .await
        .unwrap_or_default();
    with_content_type("text/html; charset=UTF-8", index)
}

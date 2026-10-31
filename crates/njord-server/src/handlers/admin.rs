//! `GET /v1/admin` (`AdminHandler.kt`), behind HTTP basic auth (`adminUser` / `adminPass`).

use std::sync::Arc;

use axum::extract::State;
use axum::http::{HeaderMap, HeaderValue, StatusCode, header};
use axum::response::{IntoResponse, Response};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;

use crate::state::AppState;

/// Missing or wrong credentials: 401 with Ktor's `WWW-Authenticate: Basic realm="Admin",
/// charset=UTF-8`. Right credentials: an empty 501, because minting signatures arrives with the
/// mutating routes in Phase 4.
pub async fn admin(State(state): State<Arc<AppState>>, headers: HeaderMap) -> Response {
    let authorized = headers
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Basic "))
        .and_then(|b64| STANDARD.decode(b64.trim()).ok())
        .and_then(|bytes| String::from_utf8(bytes).ok())
        .is_some_and(|pair| {
            pair.split_once(':').is_some_and(|(user, pass)| {
                user == state.config.admin_user && pass == state.config.admin_pass
            })
        });
    if authorized {
        return StatusCode::NOT_IMPLEMENTED.into_response();
    }
    let mut response = StatusCode::UNAUTHORIZED.into_response();
    response.headers_mut().insert(
        header::WWW_AUTHENTICATE,
        HeaderValue::from_static("Basic realm=\"Admin\", charset=UTF-8"),
    );
    response
}

//! `GET /v1/admin`, behind HTTP basic auth (`adminUser` / `adminPass`), and
//! `POST /v1/admin/verify`: minting and refreshing admin signatures.

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use axum::extract::rejection::JsonRejection;
use axum::http::{HeaderMap, HeaderValue, StatusCode, header};
use axum::response::{IntoResponse, Response};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use njord_model::AdminSignature;

use crate::forwarded::ExternalBaseUrl;
use crate::state::AppState;

/// `GET /v1/admin`: mints an admin signature for valid basic-auth credentials.
///
/// Missing or wrong credentials: 401 with `WWW-Authenticate: Basic realm="Admin", charset=UTF-8`,
/// the exact challenge the conformance goldens record. Right credentials: a fresh `AdminResponse`
/// for the request's external base URL, the token every mutating call then carries as
/// `?signature=`.
#[utoipa::path(get, path = "/v1/admin", tag = "admin", operation_id = "admin",
    summary = "Mint an admin signature",
    description = "With the configured admin credentials as HTTP basic auth: a fresh signature for \
the request's external base URL. Its `signatureEncoded` is the `signature` query parameter every \
signed route takes.",
    responses(
        (status = 200, description = "The signature", body = njord_model::AdminResponse),
        (status = 401, description = "Missing or wrong credentials; `WWW-Authenticate: Basic realm=\"Admin\", charset=UTF-8`")),
    security(("admin_basic" = [])))]
pub async fn admin(
    State(state): State<Arc<AppState>>,
    ExternalBaseUrl(base_url): ExternalBaseUrl,
    headers: HeaderMap,
) -> Response {
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
        return Json(state.verifier.mint_now(&base_url)).into_response();
    }
    let mut response = StatusCode::UNAUTHORIZED.into_response();
    response.headers_mut().insert(
        header::WWW_AUTHENTICATE,
        HeaderValue::from_static("Basic realm=\"Admin\", charset=UTF-8"),
    );
    response
}

/// `POST /v1/admin/verify`: refreshes a valid admin signature (the UI's session refresh).
///
/// The body is an `AdminSignature`; when it verifies under the request's external base URL a
/// fresh `AdminResponse` is minted, otherwise an empty 401. A body that is not an
/// `AdminSignature` is a 400 with a text message ([`super::bad_request_text`]).
#[utoipa::path(post, path = "/v1/admin/verify", tag = "admin", operation_id = "verify_admin",
    summary = "Refresh an admin signature",
    description = "When the body verifies under the request's external base URL, a fresh signature \
is minted.",
    request_body = AdminSignature,
    responses(
        (status = 200, description = "The new signature", body = njord_model::AdminResponse),
        (status = 400, description = "The body is not an `AdminSignature`", content_type = "text/plain", body = String),
        (status = 401, description = "The signature does not verify")))]
pub async fn verify(
    State(state): State<Arc<AppState>>,
    ExternalBaseUrl(base_url): ExternalBaseUrl,
    body: Result<Json<AdminSignature>, JsonRejection>,
) -> Response {
    let Ok(Json(signature)) = body else {
        return super::bad_request_text(
            "Failed to convert request body to class io.madrona.njord.model.AdminSignature",
        );
    };
    if state
        .verifier
        .verify(&signature, &base_url, jiff::Timestamp::now())
    {
        Json(state.verifier.mint_now(&base_url)).into_response()
    } else {
        StatusCode::UNAUTHORIZED.into_response()
    }
}

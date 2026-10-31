//! One module per Kotlin `endpoints/*Handler.kt`.
//!
//! Every Kotlin handler registers all seven verbs and answers 405 with an empty body for the ones
//! it does not implement ([`api`]); a path no handler matches falls through to the web app
//! ([`web::fallback`]). A handler that answers 404 itself (`/v1/about/nope`) sends an empty body
//! with no content type.

pub mod about;
pub mod admin;
pub mod cache;
pub mod charts;
pub mod content;
pub mod feature;
pub mod geojson;
pub mod icon;
pub mod regions;
pub mod sector_svg;
pub mod style;
pub mod tile;
pub mod tile_json;
pub mod web;

use std::sync::Arc;

use axum::extract::{FromRequestParts, Query};
use axum::http::request::Parts;
use axum::http::{HeaderValue, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::routing::MethodRouter;

use crate::forwarded::Origin;
use crate::state::AppState;

/// Kotlin's `Route.addHandler`: the verbs `methods` implements, and an empty 405 for every other
/// one, `HEAD` and `OPTIONS` included (Ktor has no automatic `HEAD`; CORS preflights are answered
/// before routing). No Kotlin handler implements `HEAD`, so this sets it; a route that needs one
/// would build its `MethodRouter` without this helper.
pub fn api<S: Clone + Send + Sync + 'static>(methods: MethodRouter<S>) -> MethodRouter<S> {
    methods
        .head(method_not_allowed)
        .fallback(method_not_allowed)
}

pub async fn method_not_allowed() -> StatusCode {
    StatusCode::METHOD_NOT_ALLOWED
}

/// A body with an exact `Content-Type`, as Ktor's `respondBytes`/`respondText` write it.
pub fn with_content_type(content_type: &'static str, body: impl IntoResponse) -> Response {
    let mut response = body.into_response();
    response
        .headers_mut()
        .insert(header::CONTENT_TYPE, HeaderValue::from_static(content_type));
    response
}

/// Kotlin's `fromString<T>`: the variant whose name equals `value`, ignoring ASCII case.
pub fn from_name<T: Copy>(all: &[T], name: impl Fn(T) -> &'static str, value: &str) -> Option<T> {
    all.iter()
        .copied()
        .find(|&v| name(v).eq_ignore_ascii_case(value))
}

/// The first value of query parameter `key` (Ktor's `parameters[key]`).
pub fn query_param<'a>(query: &'a [(String, String)], key: &str) -> Option<&'a str> {
    query
        .iter()
        .find(|(k, _)| k == key)
        .map(|(_, v)| v.as_str())
}

/// A request carrying a valid admin signature (`requireSignature`): the `signature` query
/// parameter verifies against the external base URL ([`crate::signature`]). Rejects with an
/// empty 401 otherwise, malformed signatures included (decision D4).
pub struct Signed;

impl FromRequestParts<Arc<AppState>> for Signed {
    type Rejection = StatusCode;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &Arc<AppState>,
    ) -> Result<Self, StatusCode> {
        let query = Query::<Vec<(String, String)>>::try_from_uri(&parts.uri)
            .map(|q| q.0)
            .unwrap_or_default();
        let base_url = Origin::of(&parts.headers, &parts.uri).base_url();
        let valid = query_param(&query, "signature").is_some_and(|s| {
            state
                .verifier
                .verify_query(s, &base_url, jiff::Timestamp::now())
        });
        if valid {
            Ok(Signed)
        } else {
            Err(StatusCode::UNAUTHORIZED)
        }
    }
}

/// A mutating verb of the read-only server (Phase 4 implements them): 401 without a valid
/// signature, as Kotlin answers, and an empty 501 with one, so nothing is changed and a signed
/// client learns the operation is unavailable rather than that it succeeded.
pub async fn not_implemented(_: Signed) -> StatusCode {
    StatusCode::NOT_IMPLEMENTED
}

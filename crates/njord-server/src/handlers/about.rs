//! `GET /v1/about/{path...}` (`AboutHandler.kt`): build identity and the resource dictionaries.

use std::sync::Arc;

use axum::Json;
use axum::body::Bytes;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::AboutJson;

use super::with_content_type;
use crate::state::AppState;

/// The crate version with `-rust` appended. The conformance suite tells the implementations apart
/// by `rust` in this field (or an `implementation` key, which would change the golden's key set).
pub const VERSION: &str = concat!(env!("CARGO_PKG_VERSION"), "-rust");

pub fn about_json() -> AboutJson {
    AboutJson {
        version: VERSION.to_owned(),
        // The GDAL the ingest links through `njord-s57` (`RELEASE_NAME`, e.g. `3.13.1`).
        gdal_version: njord_s57::gdal_version(),
        git_hash: env!("NJORD_GIT_HASH").to_owned(),
        git_branch: env!("NJORD_GIT_BRANCH").to_owned(),
        build_date: env!("NJORD_BUILD_DATE").to_owned(),
    }
}

/// `/v1/about` itself, with no sub-path: Ktor's tailcard matches it and the handler answers 404.
pub async fn root() -> StatusCode {
    StatusCode::NOT_FOUND
}

/// `version`, `s57objects`, `s57attributes`, `expectedInput`, `colors`, `colors/custom`
/// (`application/json`); anything else is an empty 404.
pub async fn about(State(state): State<Arc<AppState>>, Path(path): Path<String>) -> Response {
    let doc = &state.about;
    let body: &Bytes = match path.as_str() {
        "version" => return Json(about_json()).into_response(),
        "s57objects" => &doc.s57objects,
        "s57attributes" => &doc.s57attributes,
        "expectedInput" => &doc.expected_input,
        "colors" => &doc.colors,
        "colors/custom" => &doc.colors_custom,
        _ => return StatusCode::NOT_FOUND.into_response(),
    };
    with_content_type("application/json", body.clone())
}

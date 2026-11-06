//! `GET /v1/about/{path...}`: build identity and the resource dictionaries.

use std::sync::Arc;

use axum::Json;
use axum::body::Bytes;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_model::AboutJson;

use super::with_content_type;
use crate::state::AppState;

/// The crate version with `-rust` appended.
///
/// The conformance suite tells the implementations apart
/// by `rust` in this field (or an `implementation` key, which would change the golden's key set).
pub const VERSION: &str = concat!(env!("CARGO_PKG_VERSION"), "-rust");

/// The `version` document: the crate version, git hash and branch, build date and GDAL release.
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

/// `/v1/about` itself, with no sub-path: an empty 404.
#[utoipa::path(get, path = "/v1/about", tag = "about", operation_id = "about_root",
    summary = "The about prefix itself",
    description = "Always an empty 404: the documents are under `/v1/about/{path}`.",
    responses((status = 404, description = "Empty")))]
pub async fn root() -> StatusCode {
    StatusCode::NOT_FOUND
}

/// `version`, `s57objects`, `s57attributes`, `expectedInput`, `colors`, `colors/custom`
/// (`application/json`); anything else is an empty 404.
#[utoipa::path(get, path = "/v1/about/{path}", tag = "about", operation_id = "about",
    summary = "Build identity and the S-57 and S-52 dictionaries",
    description = "`version`: the server build. `s57objects`, `s57attributes` and `expectedInput`: \
the S-57 object catalog, keyed by acronym. `colors`: the S-52 color table of each theme mode; \
`colors/custom`: the named custom color sets. Anything else is an empty 404.",
    params(("path" = String, Path, example = "version",
        description = "`version`, `s57objects`, `s57attributes`, `expectedInput`, `colors` or `colors/custom`")),
    responses(
        (status = 200, description = "The document named by `path`", body = crate::openapi::AboutDocument),
        (status = 404, description = "Unknown document")))]
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

//! `GET /v1/content/{content...}`: fonts and sprite sheets from `<resources>/www/`.

use std::path::{Path as FsPath, PathBuf};
use std::sync::Arc;

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::with_content_type;
use crate::state::AppState;

/// `/v1/content` itself, with no segments: an empty 404.
pub async fn root() -> StatusCode {
    StatusCode::NOT_FOUND
}

/// The file under `www/` named by the (percent-decoded) path.
///
/// Its content type comes from its extension ([`mime_type`]). An empty 404 for an unknown
/// extension, a missing file, a path with an empty, `.` or `..` segment ([`resource_name`]), or one
/// that resolves outside the resources directory through a symbolic link.
pub async fn content(State(state): State<Arc<AppState>>, Path(content): Path<String>) -> Response {
    let segments: Vec<&str> = content.split('/').collect();
    let Some(content_type) = segments.last().and_then(|s| mime_type(s)) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    let Some(name) = resource_name(&segments) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    match resource_bytes(&state.resources, &name).await {
        Some(bytes) => with_content_type(content_type, bytes),
        None => {
            tracing::error!("error finding resource '{name}'");
            StatusCode::NOT_FOUND.into_response()
        }
    }
}

/// The resource path `www/<segments joined by '/'>`, or `None` when any segment is empty, `.` or
/// `..`. The real path is also confined to the resources directory ([`resource_bytes`]), but
/// `www/../config/application.json` is inside it, so dot segments are refused here. Segments are
/// never re-encoded: the font stack `Roboto Bold` is a directory with a space in its name.
pub fn resource_name(segments: &[&str]) -> Option<String> {
    let ok = !segments.is_empty()
        && segments
            .iter()
            .all(|s| !s.is_empty() && *s != "." && *s != "..");
    ok.then(|| format!("www/{}", segments.join("/")))
}

/// The content type of a resource file name, by the lower-cased text after the last `.` (which must
/// be neither the first nor the last character).
pub fn mime_type(name: &str) -> Option<&'static str> {
    let dot = name.rfind('.').filter(|&i| i >= 1 && i < name.len() - 1)?;
    Some(match name[dot + 1..].to_ascii_lowercase().as_str() {
        "png" => "image/png",
        "pbf" => "application/x-protobuf",
        "svg" => "image/svg+xml",
        "json" => "application/json",
        "css" => "text/css",
        "txt" | "html" => "text/html",
        "js" => "text/javascript",
        _ => return None,
    })
}

/// The file's bytes when its canonical path is a regular file inside the
/// canonical resources directory.
pub async fn resource_bytes(resources: &FsPath, name: &str) -> Option<Vec<u8>> {
    let root = tokio::fs::canonicalize(resources).await.ok()?;
    let file: PathBuf = tokio::fs::canonicalize(resources.join(name)).await.ok()?;
    if !file.starts_with(&root) {
        tracing::warn!("Attack attempt detected: {name}");
        return None;
    }
    if !tokio::fs::metadata(&file).await.ok()?.is_file() {
        return None;
    }
    tokio::fs::read(&file).await.ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn names() {
        assert_eq!(
            resource_name(&["fonts", "Roboto Bold", "0-255.pbf"]).as_deref(),
            Some("www/fonts/Roboto Bold/0-255.pbf")
        );
        assert_eq!(resource_name(&["..", "config", "application.json"]), None);
        assert_eq!(resource_name(&["sprites", ".", "day.json"]), None);
        assert_eq!(resource_name(&["sprites", "", "day.json"]), None);
    }

    #[test]
    fn mime_types() {
        assert_eq!(mime_type("day_simplified@2x.PNG"), Some("image/png"));
        assert_eq!(mime_type("day_simplified.exe"), None);
        assert_eq!(mime_type(".json"), None);
        assert_eq!(mime_type("json."), None);
    }
}

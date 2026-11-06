//! Region archives: `GET/POST /v1/regions` (the manifest, and re-rendering a region) and
//! `GET /v1/regions/{archive}` (the archive download).

use std::io::SeekFrom;
use std::sync::Arc;

use axum::Json;
use axum::body::Body;
use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, HeaderValue, StatusCode, header};
use axum::response::{IntoResponse, Response};
use njord_model::RegionManifestEntry;
use tokio::io::{AsyncReadExt, AsyncSeekExt};

use super::{Signed, query_param, text};
use crate::state::AppState;

/// `POST /v1/regions?name=`, signed: forgets the named region's last export so it is stale again
/// and wakes the export worker; 202, the archive renders asynchronously. 400 (text) without
/// `name`, 404 (text) for a region that is not configured, 500 when the state could not be
/// cleared.
#[utoipa::path(post, path = "/v1/regions", tag = "regions", operation_id = "rerender_region",
    summary = "Re-render a region archive",
    description = "Forgets the region's last export so it is stale again and wakes the export \
worker; the archive renders asynchronously.",
    params(("name" = String, Query, example = "US_WEST", description = "A configured region's name")),
    responses(
        (status = 202, description = "Queued"),
        (status = 400, description = "`name` missing", content_type = "text/plain", body = String),
        (status = 401, description = "No valid admin signature"),
        (status = 404, description = "No such region", content_type = "text/plain", body = String),
        (status = 500, description = "The export state could not be cleared")),
    security(("admin_signature" = [])))]
pub async fn rerender(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let Some(name) = query_param(&query, "name") else {
        return text(
            StatusCode::BAD_REQUEST,
            "missing required query parameter: name",
        );
    };
    if !state.config.region_exports.iter().any(|r| r.name == name) {
        return text(
            StatusCode::NOT_FOUND,
            format!("no configured region named {name}"),
        );
    }
    if let Err(e) = state.db.clear_region_export_state(name).await {
        tracing::error!("clear export state {name}: {e}");
        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
    }
    state.wake_region_worker();
    StatusCode::ACCEPTED.into_response()
}

/// The manifest: every configured region, rendered or not.
#[utoipa::path(get, path = "/v1/regions", tag = "regions", operation_id = "regions",
    summary = "The region manifest",
    description = "Every configured region, rendered or not; the archive fields are `null` until \
the region has been exported.",
    responses((status = 200, description = "The manifest", body = Vec<RegionManifestEntry>)))]
pub async fn manifest(State(state): State<Arc<AppState>>) -> Response {
    let state = state.clone();
    let entries: Vec<RegionManifestEntry> =
        tokio::task::spawn_blocking(move || state.regions.manifest(&state.config.region_exports))
            .await
            .unwrap_or_default();
    Json(entries).into_response()
}

/// One archive (`application/x-sqlite3`, `Accept-Ranges: bytes`, `Content-Disposition:
/// attachment; filename=<archive>`), signed.
///
/// - No valid signature: empty 401. A (decoded) name containing `/` or `..`: empty 400. No such
///   file: empty 404.
/// - `Range: bytes=…` ([`parse_range`]: `a-b`, `a-`, `-n`, several ranges merged into the one
///   range covering them all): 206 with `Content-Range: bytes a-b/size`. A range that cannot be
///   parsed or lies past the end: 416 with `Content-Range: bytes */size` and no body.
#[utoipa::path(get, path = "/v1/regions/{archive}", tag = "regions", operation_id = "region_archive",
    summary = "Download a region archive",
    description = "The `.mbtiles` named in the manifest, as an attachment with `Accept-Ranges: bytes`. \
A `Range` request is answered with a single part covering every range asked for.",
    params(
        ("archive" = String, Path, example = "US_WEST.mbtiles", description = "The manifest's `archive` value"),
        ("Range" = Option<String>, Header, example = "bytes=0-1048575", description = "`bytes=a-b`, `bytes=a-` or `bytes=-n`; several ranges are merged into one")),
    responses(
        (status = 200, description = "The archive", content_type = "application/x-sqlite3", body = crate::openapi::Binary),
        (status = 206, description = "The requested range, with `Content-Range`", content_type = "application/x-sqlite3", body = crate::openapi::Binary),
        (status = 400, description = "The name contains `/` or `..`"),
        (status = 401, description = "No valid admin signature"),
        (status = 404, description = "No such archive"),
        (status = 416, description = "The range does not parse or lies past the end; `Content-Range: bytes */size`")),
    security(("admin_signature" = [])))]
pub async fn archive(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Path(name): Path<String>,
    headers: HeaderMap,
) -> Response {
    if name.contains('/') || name.contains("..") {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let path = state.regions.dir.join(&name);
    let size = match tokio::fs::metadata(&path).await {
        Ok(m) if m.is_file() => m.len(),
        _ => return StatusCode::NOT_FOUND.into_response(),
    };
    let mut response = Response::new(Body::empty());
    let h = response.headers_mut();
    h.insert(header::ACCEPT_RANGES, HeaderValue::from_static("bytes"));
    if let Ok(v) = HeaderValue::from_str(&format!("attachment; filename={name}")) {
        h.insert(header::CONTENT_DISPOSITION, v);
    }
    let range = headers
        .get(header::RANGE)
        .map(|v| v.to_str().ok().and_then(|s| parse_range(s, size)));
    let (status, start, len) = match range {
        None => (StatusCode::OK, 0, size),
        Some(None) => {
            *response.status_mut() = StatusCode::RANGE_NOT_SATISFIABLE;
            if let Ok(v) = HeaderValue::from_str(&format!("bytes */{size}")) {
                response.headers_mut().insert(header::CONTENT_RANGE, v);
            }
            return response;
        }
        Some(Some((first, last))) => {
            if let Ok(v) = HeaderValue::from_str(&format!("bytes {first}-{last}/{size}")) {
                response.headers_mut().insert(header::CONTENT_RANGE, v);
            }
            (StatusCode::PARTIAL_CONTENT, first, last - first + 1)
        }
    };
    let bytes = match read_range(&path, start, len).await {
        Ok(bytes) => bytes,
        Err(e) => {
            tracing::error!("{}: {e}", path.display());
            return StatusCode::INTERNAL_SERVER_ERROR.into_response();
        }
    };
    *response.status_mut() = status;
    response.headers_mut().insert(
        header::CONTENT_TYPE,
        HeaderValue::from_static("application/x-sqlite3"),
    );
    *response.body_mut() = Body::from(bytes);
    response
}

async fn read_range(path: &std::path::Path, start: u64, len: u64) -> std::io::Result<Vec<u8>> {
    let mut file = tokio::fs::File::open(path).await?;
    file.seek(SeekFrom::Start(start)).await?;
    let mut bytes = Vec::with_capacity(len as usize);
    file.take(len).read_to_end(&mut bytes).await?;
    Ok(bytes)
}

/// The inclusive byte range a `Range` header value asks for, or `None` when it does not parse
/// or the merged range is empty.
///
/// Several ranges are merged into the one range covering them all, so a download client always
/// gets a single-part 206; the cases are pinned by the conformance goldens.
pub fn parse_range(spec: &str, size: u64) -> Option<(u64, u64)> {
    let (unit, ranges) = spec.split_once('=')?;
    if !unit.trim().eq_ignore_ascii_case("bytes") {
        return None;
    }
    let mut merged: Option<(u64, u64)> = None;
    for part in ranges.split(',') {
        let part = part.trim();
        let (a, b) = part.split_once('-')?;
        let (first, last) = match (a.trim(), b.trim()) {
            ("", n) => {
                let n: u64 = n.parse().ok()?;
                (size.saturating_sub(n), size.checked_sub(1)?)
            }
            (a, "") => (a.parse().ok()?, size.checked_sub(1)?),
            (a, b) => {
                let (a, b): (u64, u64) = (a.parse().ok()?, b.parse().ok()?);
                if b < a {
                    return None;
                }
                (a, b.min(size.checked_sub(1)?))
            }
        };
        merged = Some(match merged {
            None => (first, last),
            Some((f, l)) => (f.min(first), l.max(last)),
        });
    }
    merged.filter(|(first, last)| first <= last && *first < size)
}

#[cfg(test)]
mod tests {
    use super::parse_range;

    #[test]
    fn ranges() {
        assert_eq!(parse_range("bytes=0-99", 1000), Some((0, 99)));
        assert_eq!(parse_range("bytes=-100", 1000), Some((900, 999)));
        assert_eq!(parse_range("bytes=500-", 1000), Some((500, 999)));
        assert_eq!(parse_range("bytes=0-9,20-29", 1000), Some((0, 29)));
        assert_eq!(parse_range("bytes=0-5000", 1000), Some((0, 999)));
        assert_eq!(parse_range("bytes=999999999999-", 1000), None);
        assert_eq!(parse_range("items=0-1", 1000), None);
        assert_eq!(parse_range("bytes=x-1", 1000), None);
    }
}

//! The web app: every request no route matches is answered from `webStaticContent`.
//!
//! This holds whatever the method, and an unknown path gets `index.html`, so deep links into the
//! single-page app resolve. There is no 404 for an unknown path.
//!
//! Trunk names every bundle it emits (css, js, wasm) after a hash of its content, and `index.html`
//! links them by those names, so a bundle never changes under its name: those are cached as
//! `immutable` for a year. Everything else, `index.html` above all, is `no-cache`, so a browser
//! always fetches the current page after a deploy and with it the bundles that page names.

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
            response
                .headers_mut()
                .insert(header::CACHE_CONTROL, cache_control(name));
            return response;
        }
    }
    let index = tokio::fs::read(root.join("index.html"))
        .await
        .unwrap_or_default();
    let mut response = with_content_type("text/html; charset=UTF-8", index);
    response
        .headers_mut()
        .insert(header::CACHE_CONTROL, HeaderValue::from_static(NO_CACHE));
    response
}

const NO_CACHE: &str = "no-cache";
const IMMUTABLE: &str = "public, max-age=31536000, immutable";

/// `immutable` for a file Trunk named after its content hash, `no-cache` for any other.
fn cache_control(name: &str) -> HeaderValue {
    HeaderValue::from_static(if is_content_hashed(name) { IMMUTABLE } else { NO_CACHE })
}

/// Whether `name` has Trunk's content hash: a stem ending in `-` and 16 hex digits, or that
/// followed by `_bg` for the wasm-bindgen module (`app-321620b7fef5b174.css`,
/// `njord-web-77267ecbfbbc5842_bg.wasm`).
fn is_content_hashed(name: &str) -> bool {
    let Some((stem, _ext)) = name.rsplit_once('.') else {
        return false;
    };
    let stem = stem.strip_suffix("_bg").unwrap_or(stem);
    stem.rsplit_once('-').is_some_and(|(prefix, hash)| {
        !prefix.is_empty() && hash.len() == 16 && hash.bytes().all(|b| b.is_ascii_hexdigit())
    })
}

#[cfg(test)]
mod tests {
    use super::is_content_hashed;

    #[test]
    fn trunk_hashed_names() {
        for name in [
            "app-321620b7fef5b174.css",
            "bootstrap.min-c6c6e17b5bb8f55f.css",
            "maplibre-gl-92127e18af653986.js",
            "njord-web-f15698c5a96f7e0d.js",
            "njord-web-77267ecbfbbc5842_bg.wasm",
        ] {
            assert!(is_content_hashed(name), "{name}");
        }
        for name in [
            "index.html",
            "njord.jpg",
            "favicon.ico",
            "apache_2_badge.svg",
            "njord-web.js",
            "app-321620b7fef5b17.css",
            "-321620b7fef5b174.css",
            "app-321620b7fef5b174",
        ] {
            assert!(!is_content_hashed(name), "{name}");
        }
    }
}

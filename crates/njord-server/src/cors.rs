//! CORS, as Ktor's `CORS` plugin applies it with the server's configuration (`ChartServerApp.kt`:
//! `anyHost()`, `allowHeader(ContentType)`, `allowMethod(Post, Delete, Put)` on top of the default
//! `GET, POST, HEAD`).
//!
//! Hand-written rather than `tower_http::cors`, which writes header names lower-case
//! (`content-type`), joins lists without a space and never refuses a request, while the
//! conformance goldens pin Ktor's exact header values and Ktor answers a disallowed cross-origin
//! request with an empty 403.

use axum::body::Body;
use axum::extract::Request;
use axum::http::{HeaderMap, HeaderValue, Method, StatusCode, header};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};

use crate::forwarded::Origin;

const ALLOWED_METHODS: [Method; 5] = [
    Method::DELETE,
    Method::GET,
    Method::HEAD,
    Method::POST,
    Method::PUT,
];
/// The `Access-Control-Allow-Methods` value: every allowed method, sorted.
const ALLOW_METHODS: &str = "DELETE, GET, HEAD, POST, PUT";
/// The `Access-Control-Allow-Headers` value: the configured non-simple header.
const ALLOW_HEADERS: &str = "Content-Type";
/// Request headers a preflight may ask for: the configured one plus the CORS-safelisted ones.
const REQUEST_HEADERS: [&str; 4] = [
    "content-type",
    "accept",
    "accept-language",
    "content-language",
];
/// Ktor's default `maxAgeInSeconds`, one day.
const MAX_AGE: &str = "86400";

/// The middleware. For a request with a single, well-formed `Origin` that is not the server's
/// own origin:
/// - any `OPTIONS` is a preflight: 200 with `Access-Control-Allow-Origin: *`, `-Methods`,
///   `-Headers` and `-Max-Age` and no body, or an empty 403 when `Access-Control-Request-Method`
///   is missing or not allowed, or a requested header is not allowed. It never reaches a route.
/// - any other request with an allowed method gets `Access-Control-Allow-Origin: *`; a `PATCH`
///   (or other method outside the list) is refused with an empty 403.
///
/// Requests without an `Origin`, with a malformed one, or from the same origin
/// ([`Origin::of`], so behind the same proxy host) pass through untouched.
pub async fn cors(request: Request, next: Next) -> Response {
    if !is_cross_origin(request.headers(), request.uri()) {
        return next.run(request).await;
    }
    if request.method() == Method::OPTIONS {
        return preflight(request.headers());
    }
    if !ALLOWED_METHODS.contains(request.method()) {
        return StatusCode::FORBIDDEN.into_response();
    }
    let mut response = next.run(request).await;
    response.headers_mut().insert(
        header::ACCESS_CONTROL_ALLOW_ORIGIN,
        HeaderValue::from_static("*"),
    );
    response
}

fn preflight(headers: &HeaderMap) -> Response {
    let method_allowed = headers
        .get(header::ACCESS_CONTROL_REQUEST_METHOD)
        .and_then(|v| v.to_str().ok())
        .and_then(|m| Method::from_bytes(m.as_bytes()).ok())
        .is_some_and(|m| ALLOWED_METHODS.contains(&m));
    let headers_allowed = headers
        .get_all(header::ACCESS_CONTROL_REQUEST_HEADERS)
        .iter()
        .filter_map(|v| v.to_str().ok())
        .flat_map(|v| v.split(','))
        .map(str::trim)
        .filter(|h| !h.is_empty())
        .all(|h| REQUEST_HEADERS.iter().any(|a| a.eq_ignore_ascii_case(h)));
    if !method_allowed || !headers_allowed {
        return StatusCode::FORBIDDEN.into_response();
    }
    let mut response = Response::new(Body::empty());
    let h = response.headers_mut();
    h.insert(
        header::ACCESS_CONTROL_ALLOW_ORIGIN,
        HeaderValue::from_static("*"),
    );
    h.insert(
        header::ACCESS_CONTROL_ALLOW_METHODS,
        HeaderValue::from_static(ALLOW_METHODS),
    );
    h.insert(
        header::ACCESS_CONTROL_ALLOW_HEADERS,
        HeaderValue::from_static(ALLOW_HEADERS),
    );
    h.insert(
        header::ACCESS_CONTROL_MAX_AGE,
        HeaderValue::from_static(MAX_AGE),
    );
    response
}

/// Whether CORS applies: a single, well-formed `Origin` other than the server's own.
fn is_cross_origin(headers: &HeaderMap, uri: &axum::http::Uri) -> bool {
    let mut values = headers.get_all(header::ORIGIN).iter();
    let Some(origin) = values.next().and_then(|v| v.to_str().ok()) else {
        return false;
    };
    if values.next().is_some() || !is_valid_origin(origin) {
        return false;
    }
    let own = Origin::of(headers, uri);
    normalize(&format!("{}://{}:{}", own.scheme, own.host, own.port)) != normalize(origin)
}

/// Ktor's `isValidOrigin`: `null`, or `scheme://host[:port]` with no percent sign, a scheme of
/// letters, digits, `+`, `-` and `.` starting with a letter, and a numeric port.
fn is_valid_origin(origin: &str) -> bool {
    if origin == "null" {
        return true;
    }
    if origin.is_empty() || origin.contains('%') {
        return false;
    }
    let Some((scheme, rest)) = origin.split_once("://") else {
        return false;
    };
    let scheme_ok = scheme.starts_with(|c: char| c.is_ascii_alphabetic())
        && scheme
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || "+-.".contains(c));
    if !scheme_ok || rest.is_empty() {
        return false;
    }
    match rest.rsplit_once(':') {
        Some((host, port)) if !rest.starts_with('[') || host.ends_with(']') => {
            !host.is_empty() && port.chars().all(|c| c.is_ascii_digit())
        }
        _ => true,
    }
}

/// `scheme://host:port`, lower-cased, with the scheme's default port filled in.
fn normalize(origin: &str) -> String {
    let lower = origin.to_ascii_lowercase();
    let Some((scheme, rest)) = lower.split_once("://") else {
        return lower;
    };
    let has_port = rest
        .rsplit_once(':')
        .is_some_and(|(_, p)| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()));
    if has_port {
        return lower;
    }
    match scheme {
        "http" | "ws" => format!("{lower}:80"),
        "https" | "wss" => format!("{lower}:443"),
        _ => lower,
    }
}

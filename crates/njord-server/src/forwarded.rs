//! The request's external origin (`ext/KtorExt.kt` `externalBaseUrl()`, over Ktor's
//! `XForwardedHeaders` and `ForwardedHeaders` plugins): the scheme, host and port a client used to
//! reach the server, through any reverse proxy. Style, TileJSON and admin-signature URLs are built
//! from it, so it must agree with Kotlin header for header.
//!
//! Client-sent forwarded headers are trusted, as in Kotlin: in production the ingress overwrites
//! them.

use std::convert::Infallible;

use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::{HeaderMap, Uri, header};

/// Where the client reached the server.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Origin {
    pub scheme: String,
    pub host: String,
    pub port: u16,
}

impl Origin {
    /// The local connection point, then the `X-Forwarded-*` headers, then RFC 7239 `Forwarded`
    /// (Ktor runs the plugins in installation order, so `Forwarded` wins when both are present).
    ///
    /// - Local: scheme `http`; host and port from `Host` (or the URI authority). Without a port
    ///   the scheme's default is used; Ktor would fall back to the local socket's port, which no
    ///   deployment relies on (the ingress always sends `X-Forwarded-Proto`).
    /// - `X-Forwarded-Proto` / `X-Forwarded-Protocol` sets the scheme and resets the port to that
    ///   scheme's default; `X-Forwarded-SSL` / `Front-End-Https` `on` (or `true`) means `https`.
    ///   `X-Forwarded-Host` / `X-Forwarded-Server` sets the host (and the port when it carries
    ///   one); `X-Forwarded-Port` sets the port. Comma-separated lists use the first value (the
    ///   proxy nearest the client), Ktor's default `useFirstProxy`.
    /// - `Forwarded`: the first element's `proto` (scheme and default port) and `host` (host,
    ///   and its port or the scheme's default).
    pub fn of(headers: &HeaderMap, uri: &Uri) -> Origin {
        let mut origin = local(headers, uri);
        origin.apply_x_forwarded(headers);
        origin.apply_forwarded(headers);
        origin
    }

    /// `externalBaseUrl()`: `scheme://host[:port]`, the port left out when it is the scheme's
    /// default (only `http`/80 and `https`/443 are elided).
    pub fn base_url(&self) -> String {
        let default = matches!(
            (self.scheme.as_str(), self.port),
            ("https", 443) | ("http", 80)
        );
        if default {
            format!("{}://{}", self.scheme, self.host)
        } else {
            format!("{}://{}:{}", self.scheme, self.host, self.port)
        }
    }

    fn set_scheme(&mut self, scheme: &str) {
        self.scheme = scheme.to_owned();
        if let Some(port) = default_port(scheme) {
            self.port = port;
        }
    }

    fn apply_x_forwarded(&mut self, headers: &HeaderMap) {
        if let Some(proto) = first_value(headers, &["x-forwarded-proto", "x-forwarded-protocol"]) {
            self.set_scheme(proto);
        }
        if let Some(flag) = first_value(headers, &["x-forwarded-ssl", "front-end-https"])
            && (flag.eq_ignore_ascii_case("on") || flag.eq_ignore_ascii_case("true"))
        {
            self.set_scheme("https");
        }
        if let Some(host) = first_value(headers, &["x-forwarded-host", "x-forwarded-server"]) {
            let (host, port) = split_host_port(host);
            self.host = host.to_owned();
            if let Some(port) = port {
                self.port = port;
            }
        }
        if let Some(port) = first_value(headers, &["x-forwarded-port"]).and_then(|p| p.parse().ok())
        {
            self.port = port;
        }
    }

    fn apply_forwarded(&mut self, headers: &HeaderMap) {
        let Some(value) = headers.get(header::FORWARDED).and_then(|v| v.to_str().ok()) else {
            return;
        };
        let Some(element) = value.split(',').next() else {
            return;
        };
        let pairs = element.split(';').filter_map(|pair| {
            let (k, v) = pair.split_once('=')?;
            Some((
                k.trim().to_ascii_lowercase(),
                v.trim().trim_matches('"').to_owned(),
            ))
        });
        let (mut proto, mut host) = (None, None);
        for (k, v) in pairs {
            match k.as_str() {
                "proto" => proto = Some(v),
                "host" => host = Some(v),
                _ => {}
            }
        }
        if let Some(proto) = proto {
            self.set_scheme(&proto);
        }
        if let Some(host) = host {
            let (host, port) = split_host_port(&host);
            self.host = host.to_owned();
            if let Some(port) = port.or_else(|| default_port(&self.scheme)) {
                self.port = port;
            }
        }
    }
}

/// `URLProtocol.byName[scheme].defaultPort`.
fn default_port(scheme: &str) -> Option<u16> {
    match scheme.to_ascii_lowercase().as_str() {
        "http" | "ws" => Some(80),
        "https" | "wss" => Some(443),
        "socks" => Some(1080),
        _ => None,
    }
}

fn local(headers: &HeaderMap, uri: &Uri) -> Origin {
    let authority = headers
        .get(header::HOST)
        .and_then(|v| v.to_str().ok())
        .or_else(|| uri.authority().map(|a| a.as_str()))
        .unwrap_or("localhost");
    let (host, port) = split_host_port(authority);
    Origin {
        scheme: "http".to_owned(),
        host: host.to_owned(),
        port: port.unwrap_or(80),
    }
}

/// The first non-blank value of the first header present, first entry of a comma list.
fn first_value<'a>(headers: &'a HeaderMap, names: &[&str]) -> Option<&'a str> {
    names.iter().find_map(|name| {
        let value = headers.get(*name)?.to_str().ok()?;
        let first = value.split(',').next()?.trim();
        (!first.is_empty()).then_some(first)
    })
}

/// `host[:port]`, keeping a bracketed IPv6 literal whole.
fn split_host_port(value: &str) -> (&str, Option<u16>) {
    let colon = if value.starts_with('[') {
        value
            .find(']')
            .and_then(|end| value[end..].find(':').map(|i| end + i))
    } else {
        value.rfind(':')
    };
    match colon {
        Some(i) => match value[i + 1..].parse() {
            Ok(port) => (&value[..i], Some(port)),
            Err(_) => (&value[..i], None),
        },
        None => (value, None),
    }
}

/// Extracts [`Origin::base_url`] for a handler.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ExternalBaseUrl(pub String);

impl<S: Send + Sync> FromRequestParts<S> for ExternalBaseUrl {
    type Rejection = Infallible;

    async fn from_request_parts(parts: &mut Parts, _: &S) -> Result<Self, Self::Rejection> {
        Ok(ExternalBaseUrl(
            Origin::of(&parts.headers, &parts.uri).base_url(),
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn base(pairs: &[(&'static str, &'static str)]) -> String {
        let mut headers = HeaderMap::new();
        headers.insert(header::HOST, "localhost:9000".parse().unwrap());
        for (k, v) in pairs {
            headers.insert(*k, v.parse().unwrap());
        }
        Origin::of(&headers, &Uri::from_static("/v1/tile_json")).base_url()
    }

    #[test]
    fn local_host_header() {
        assert_eq!(base(&[]), "http://localhost:9000");
        assert_eq!(base(&[("host", "example.org")]), "http://example.org");
        assert_eq!(base(&[("host", "[::1]:9000")]), "http://[::1]:9000");
    }

    #[test]
    fn x_forwarded() {
        let host = ("x-forwarded-host", "charts.example.org");
        assert_eq!(
            base(&[("x-forwarded-proto", "https"), host]),
            "https://charts.example.org"
        );
        assert_eq!(
            base(&[
                ("x-forwarded-proto", "https"),
                host,
                ("x-forwarded-port", "8443")
            ]),
            "https://charts.example.org:8443"
        );
        assert_eq!(
            base(&[
                ("x-forwarded-proto", "http"),
                host,
                ("x-forwarded-port", "80")
            ]),
            "http://charts.example.org"
        );
        assert_eq!(
            base(&[
                ("x-forwarded-proto", "https, http"),
                ("x-forwarded-host", "a.org, b.org")
            ]),
            "https://a.org"
        );
        assert_eq!(
            base(&[("x-forwarded-ssl", "on"), host]),
            "https://charts.example.org"
        );
        // Without a forwarded scheme the local port stays.
        assert_eq!(base(&[host]), "http://charts.example.org:9000");
        assert_eq!(
            base(&[("x-forwarded-host", "charts.example.org:8080")]),
            "http://charts.example.org:8080"
        );
    }

    #[test]
    fn rfc_7239() {
        assert_eq!(
            base(&[("forwarded", "proto=https;host=fw.example.org")]),
            "https://fw.example.org"
        );
        assert_eq!(
            base(&[(
                "forwarded",
                "Proto=https;Host=\"fw.example.org:8443\", proto=http"
            )]),
            "https://fw.example.org:8443"
        );
        assert_eq!(
            base(&[("forwarded", "host=fw.example.org")]),
            "http://fw.example.org"
        );
        // Forwarded wins over X-Forwarded-*.
        assert_eq!(
            base(&[
                ("x-forwarded-proto", "http"),
                ("x-forwarded-host", "x.org"),
                ("forwarded", "proto=https;host=fw.org")
            ]),
            "https://fw.org"
        );
    }
}

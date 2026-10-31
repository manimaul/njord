//! Kotlin behaviour the goldens do not pin: Ktor's CORS refusals and same-origin pass-through,
//! the verb semantics of every route, D8, region archive ranges, the Phase 3 answers of the
//! mutating verbs and the configuration merge.

mod common;

use std::sync::{Arc, LazyLock};

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use axum::response::Response;
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use common::resources;
use njord_model::AdminSignature;
use njord_server::{AppState, ChartsConfig, router};
use tower::ServiceExt;

/// No test here queries the database.
static STATE: LazyLock<Arc<AppState>> = LazyLock::new(|| common::offline_state("behaviour"));

async fn send(method: &str, path: &str, headers: &[(&str, &str)]) -> Response {
    send_to(STATE.clone(), method, path, headers).await
}

async fn send_to(
    state: Arc<AppState>,
    method: &str,
    path: &str,
    headers: &[(&str, &str)],
) -> Response {
    let mut builder = Request::builder()
        .method(method)
        .uri(path)
        .header(header::HOST, "localhost:9000");
    for (k, v) in headers {
        builder = builder.header(*k, *v);
    }
    router(state)
        .oneshot(builder.body(Body::empty()).unwrap())
        .await
        .unwrap()
}

const ORIGIN: (&str, &str) = ("origin", "http://example.test");

#[tokio::test]
async fn cors_refuses_what_ktor_refuses() {
    let r = send("PATCH", "/v1/tile_json", &[ORIGIN]).await;
    assert_eq!(r.status(), StatusCode::FORBIDDEN);
    let r = send(
        "OPTIONS",
        "/v1/chart",
        &[ORIGIN, ("access-control-request-method", "PATCH")],
    )
    .await;
    assert_eq!(r.status(), StatusCode::FORBIDDEN);
    let r = send(
        "OPTIONS",
        "/v1/chart",
        &[
            ORIGIN,
            ("access-control-request-method", "POST"),
            ("access-control-request-headers", "x-custom"),
        ],
    )
    .await;
    assert_eq!(r.status(), StatusCode::FORBIDDEN);
    let r = send("OPTIONS", "/v1/chart", &[ORIGIN]).await;
    assert_eq!(r.status(), StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn cors_skips_same_origin_and_malformed_origins() {
    for origin in ["http://localhost:9000", "not an origin"] {
        let r = send("GET", "/v1/tile_json", &[("origin", origin)]).await;
        assert_eq!(r.status(), StatusCode::OK);
        assert!(
            r.headers()
                .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
                .is_none(),
            "{origin}"
        );
    }
    // Same origin behind a proxy: the forwarded origin is the server's own.
    let r = send(
        "GET",
        "/v1/tile_json",
        &[
            ("origin", "https://charts.example.org"),
            ("x-forwarded-proto", "https"),
            ("x-forwarded-host", "charts.example.org"),
        ],
    )
    .await;
    assert!(
        r.headers()
            .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
            .is_none()
    );
    // CORS headers go on error responses too.
    let r = send("GET", "/v1/about/nope", &[ORIGIN]).await;
    assert_eq!(r.status(), StatusCode::NOT_FOUND);
    assert_eq!(r.headers()[header::ACCESS_CONTROL_ALLOW_ORIGIN], "*");
}

#[tokio::test]
async fn unimplemented_verbs_are_405_on_every_route() {
    let routes = [
        "/v1/about",
        "/v1/about/version",
        "/v1/tile_json",
        "/v1/style/meters/day",
        "/v1/icon/BOYLAT13.png",
        "/v1/sector_svg/DAY/0/250/LITRD",
        "/v1/content",
        "/v1/content/sprites/day_simplified.json",
        "/v1/chart_catalog",
        "/v1/chart_editions",
        "/v1/tile/0/0/0",
        "/v1/feature/layer/BOYLAT",
        "/v1/regions/x.mbtiles",
    ];
    for path in routes {
        for method in ["POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"] {
            let r = send(method, path, &[]).await;
            assert_eq!(
                r.status(),
                StatusCode::METHOD_NOT_ALLOWED,
                "{method} {path}"
            );
            assert!(
                r.headers().get(header::CONTENT_TYPE).is_none(),
                "{method} {path}"
            );
        }
    }
    assert_eq!(
        send("GET", "/v1/about", &[]).await.status(),
        StatusCode::NOT_FOUND
    );
    assert_eq!(
        send("GET", "/v1/content", &[]).await.status(),
        StatusCode::NOT_FOUND
    );
}

#[tokio::test]
async fn unknown_paths_serve_the_web_app_for_any_method() {
    for method in ["GET", "POST", "DELETE"] {
        let r = send(method, "/v1/nope", &[]).await;
        assert_eq!(r.status(), StatusCode::OK);
        assert_eq!(
            r.headers()[header::CONTENT_TYPE],
            "text/html; charset=UTF-8"
        );
    }
}

#[test]
fn overrides_replace_top_level_values() {
    let config = ChartsConfig::load(
        &resources(),
        Some(
            r#"{"port": 9001, "regionExports": [], "webStaticContent": "/srv/web", "unknown": 1}"#,
        ),
    )
    .unwrap();
    assert_eq!(config.port, 9001);
    assert!(config.region_exports.is_empty());
    assert_eq!(config.web_static_content, std::path::Path::new("/srv/web"));
    assert_eq!(config.admin_user, "admin");
    assert!(!config.use_tile_cache);
    assert!(ChartsConfig::load(&resources(), Some("[1]")).is_err());
    assert!(ChartsConfig::load(&resources(), Some(r#"{"port": "x"}"#)).is_err());
}

#[tokio::test]
async fn d8_out_of_range_tiles_are_400_before_anything_else() {
    for path in [
        "/v1/tile/-1/0/0",
        "/v1/tile/25/0/0",
        "/v1/tile/5/-1/0",
        "/v1/tile/5/32/0",
        "/v1/tile/5/0/32",
        "/v1/tile/0/1/0?info=true",
    ] {
        let r = send("GET", path, &[]).await;
        assert_eq!(r.status(), StatusCode::BAD_REQUEST, "{path}");
    }
    let r = send("GET", "/v1/tile/x/0/0", &[]).await;
    assert_eq!(r.status(), StatusCode::OK);
    assert_eq!(
        r.headers()[header::CONTENT_TYPE],
        "text/html; charset=UTF-8"
    );
}

/// A signature `GET /v1/admin` would mint for `http://localhost:9000` now.
fn signature(state: &AppState) -> String {
    let date = jiff::Timestamp::now();
    let uuid = "00000000-0000-4000-8000-000000000000".to_owned();
    let mac = njord_server::signature::hmac(
        &state.config.admin_key,
        &[
            "http://localhost:9000",
            &njord_model::instant::format(date),
            &uuid,
        ],
    );
    let sig = AdminSignature {
        date,
        signature: STANDARD.encode(mac),
        uuid,
        expiration_date: date,
    };
    let encoded = STANDARD.encode(serde_json::to_vec(&sig).unwrap());
    percent_encoding::utf8_percent_encode(&encoded, percent_encoding::NON_ALPHANUMERIC).to_string()
}

#[tokio::test]
async fn mutating_verbs_are_401_unsigned_and_501_signed() {
    let sig = signature(&STATE);
    for (method, path) in [
        ("POST", "/v1/chart"),
        ("DELETE", "/v1/chart?name=X"),
        ("POST", "/v1/geojson?chart_name=a&layer_name=b"),
        ("DELETE", "/v1/cache"),
        ("POST", "/v1/regions?name=WORLD"),
        ("POST", "/v1/enc_save?filename=a.zip"),
        ("DELETE", "/v1/enc_save"),
        ("POST", "/v1/enc_url"),
        ("DELETE", "/v1/nuke"),
    ] {
        assert_eq!(
            send(method, path, &[]).await.status(),
            StatusCode::UNAUTHORIZED,
            "{method} {path}"
        );
        // D4: malformed signatures are 401 with an empty body, never a 500.
        for bad in ["bogus", "eyJ4IjoxfQ%3D%3D"] {
            let sep = if path.contains('?') { '&' } else { '?' };
            let r = send(method, &format!("{path}{sep}signature={bad}"), &[]).await;
            assert_eq!(
                r.status(),
                StatusCode::UNAUTHORIZED,
                "{method} {path} {bad}"
            );
            assert!(
                axum::body::to_bytes(r.into_body(), usize::MAX)
                    .await
                    .unwrap()
                    .is_empty()
            );
        }
        let sep = if path.contains('?') { '&' } else { '?' };
        let signed = format!("{path}{sep}signature={sig}");
        assert_eq!(
            send(method, &signed, &[]).await.status(),
            StatusCode::NOT_IMPLEMENTED,
            "{method} {path}"
        );
    }
    // Signed under another external base URL: refused.
    let behind_proxy = [
        ("x-forwarded-proto", "https"),
        ("x-forwarded-host", "charts.example.org"),
    ];
    let r = send(
        "DELETE",
        &format!("/v1/cache?signature={sig}"),
        &behind_proxy,
    )
    .await;
    assert_eq!(r.status(), StatusCode::UNAUTHORIZED);
    // Basic auth on /v1/admin.
    assert_eq!(
        send("GET", "/v1/admin", &[]).await.status(),
        StatusCode::UNAUTHORIZED
    );
    let auth = format!("Basic {}", STANDARD.encode("admin:admin"));
    assert_eq!(
        send("GET", "/v1/admin", &[("authorization", &auth)])
            .await
            .status(),
        StatusCode::NOT_IMPLEMENTED
    );
}

#[tokio::test]
async fn region_archive_ranges() {
    let state = common::offline_state("behaviour_regions");
    let dir = state.config.chart_temp_data.join("regions");
    std::fs::create_dir_all(&dir).unwrap();
    let data: Vec<u8> = (0..1000u32).map(|i| (i % 251) as u8).collect();
    let name = "REGION_04_2026-01-01T00-00-00.mbtiles";
    std::fs::write(dir.join(name), &data).unwrap();
    let path = format!("/v1/regions/{name}?signature={}", signature(&state));

    assert_eq!(
        send_to(state.clone(), "GET", &format!("/v1/regions/{name}"), &[])
            .await
            .status(),
        StatusCode::UNAUTHORIZED
    );
    let r = send_to(state.clone(), "GET", &path, &[]).await;
    assert_eq!(r.status(), StatusCode::OK);
    assert_eq!(r.headers()[header::CONTENT_TYPE], "application/x-sqlite3");
    assert_eq!(r.headers()[header::ACCEPT_RANGES], "bytes");
    assert_eq!(
        r.headers()[header::CONTENT_DISPOSITION],
        format!("attachment; filename={name}")
    );
    assert!(r.headers().get(header::CONTENT_RANGE).is_none());
    assert_eq!(
        axum::body::to_bytes(r.into_body(), usize::MAX)
            .await
            .unwrap(),
        data
    );

    for (range, content_range, expected) in [
        ("bytes=0-99", "bytes 0-99/1000", &data[..100]),
        ("bytes=-100", "bytes 900-999/1000", &data[900..]),
        ("bytes=0-9,20-29", "bytes 0-29/1000", &data[..30]),
    ] {
        let r = send_to(state.clone(), "GET", &path, &[("range", range)]).await;
        assert_eq!(r.status(), StatusCode::PARTIAL_CONTENT, "{range}");
        assert_eq!(r.headers()[header::CONTENT_RANGE], content_range);
        assert_eq!(
            axum::body::to_bytes(r.into_body(), usize::MAX)
                .await
                .unwrap(),
            expected
        );
    }
    let r = send_to(
        state.clone(),
        "GET",
        &path,
        &[("range", "bytes=999999999999-")],
    )
    .await;
    assert_eq!(r.status(), StatusCode::RANGE_NOT_SATISFIABLE);
    assert_eq!(r.headers()[header::CONTENT_RANGE], "bytes */1000");
    assert!(r.headers().get(header::CONTENT_TYPE).is_none());

    let sig = signature(&state);
    for bad in ["..", "..%2Fx.mbtiles", "a%2Fb.mbtiles", "%2e%2e%2fx"] {
        let r = send_to(
            state.clone(),
            "GET",
            &format!("/v1/regions/{bad}?signature={sig}"),
            &[],
        )
        .await;
        assert_eq!(r.status(), StatusCode::BAD_REQUEST, "{bad}");
    }
    let r = send_to(
        state.clone(),
        "GET",
        &format!("/v1/regions/NOPE.mbtiles?signature={sig}"),
        &[],
    )
    .await;
    assert_eq!(r.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn tile_cache_round_trip_and_counts() {
    let state = common::offline_state("behaviour_cache");
    let cache = &state.tile_cache;
    assert_eq!(cache.get(4, 2, 5).await, None);
    cache.put(4, 2, 5, b"tile").await;
    cache.put(4, 3, 5, b"tile").await;
    cache.put(6, 1, 1, b"").await;
    assert_eq!(cache.get(4, 2, 5).await.as_deref(), Some(&b"tile"[..]));
    assert!(
        state
            .config
            .chart_temp_data
            .join("tiles/4/2/5.mvt")
            .is_file()
    );
    assert_eq!(cache.counts().into_iter().collect::<Vec<_>>(), vec![(4, 2)]);
    let r = send_to(state.clone(), "GET", "/v1/cache", &[]).await;
    assert_eq!(
        axum::body::to_bytes(r.into_body(), usize::MAX)
            .await
            .unwrap(),
        &br#"{"4":2}"#[..]
    );
    cache.clear().await;
    assert!(cache.counts().is_empty());
    assert!(state.config.chart_temp_data.join("tiles").is_dir());
}

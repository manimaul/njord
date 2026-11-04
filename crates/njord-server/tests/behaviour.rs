//! API behaviour the conformance goldens do not pin.
//!
//! CORS refusals and same-origin pass-through, the verb semantics of every route, the tile
//! coordinate bounds, region archive ranges, the answers of the mutating verbs and the
//! configuration merge.

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
async fn cors_refuses_disallowed_cross_origin_requests() {
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
async fn mutating_verbs_are_401_without_a_valid_signature() {
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
        ("GET", "/v1/regions/x.mbtiles"),
        ("GET", "/v1/change_reports"),
        ("GET", "/v1/change_reports/2026-10-31"),
    ] {
        assert_eq!(
            send(method, path, &[]).await.status(),
            StatusCode::UNAUTHORIZED,
            "{method} {path}"
        );
        // Malformed signatures are 401 with an empty body, never a 500.
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
}

/// `GET /v1/admin` mints a signature that the signed routes and `POST /v1/admin/verify` accept
/// under the same external base URL and refuse under another; `signatureEncoded` is what clients
/// send back, URL-path-encoded (`+` and `=` kept, `/` as `%2F`).
#[tokio::test]
async fn admin_mints_verifies_and_refreshes_signatures() {
    assert_eq!(
        send("GET", "/v1/admin", &[]).await.status(),
        StatusCode::UNAUTHORIZED
    );
    let wrong = format!("Basic {}", STANDARD.encode("nobody:nothing"));
    let r = send("GET", "/v1/admin", &[("authorization", &wrong)]).await;
    assert_eq!(r.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(
        r.headers()[header::WWW_AUTHENTICATE],
        "Basic realm=\"Admin\", charset=UTF-8"
    );
    let auth = format!("Basic {}", STANDARD.encode("admin:admin"));
    let r = send("GET", "/v1/admin", &[("authorization", &auth)]).await;
    assert_eq!(r.status(), StatusCode::OK);
    let body = axum::body::to_bytes(r.into_body(), usize::MAX)
        .await
        .unwrap();
    let minted: njord_model::AdminResponse = serde_json::from_slice(&body).unwrap();
    assert!(
        !minted.signature_encoded.contains('/')
            && !minted.signature_encoded.contains("%3D")
            && !minted.signature_encoded.contains("%2B"),
        "{}",
        minted.signature_encoded
    );
    assert_eq!(minted.signature.uuid.len(), 32, "{}", minted.signature.uuid);
    assert_eq!(
        minted.signature.expiration_date,
        minted
            .signature
            .date
            .checked_add(jiff::SignedDuration::from_secs(
                STATE.config.admin_expiration_seconds
            ))
            .unwrap()
    );
    // The encoded form passes requireSignature (404 is past the auth check).
    let r = send(
        "GET",
        &format!(
            "/v1/regions/nope.mbtiles?signature={}",
            minted.signature_encoded
        ),
        &[],
    )
    .await;
    assert_eq!(r.status(), StatusCode::NOT_FOUND);
    // verify refreshes it under the same base URL, refuses it under another and when tampered.
    let verify = |body: Vec<u8>, headers: Vec<(&'static str, &'static str)>| {
        let mut builder = Request::builder()
            .method("POST")
            .uri("/v1/admin/verify")
            .header(header::HOST, "localhost:9000")
            .header(header::CONTENT_TYPE, "application/json");
        for (k, v) in headers {
            builder = builder.header(k, v);
        }
        router(STATE.clone()).oneshot(builder.body(Body::from(body)).unwrap())
    };
    let json = serde_json::to_vec(&minted.signature).unwrap();
    let r = verify(json.clone(), vec![]).await.unwrap();
    assert_eq!(r.status(), StatusCode::OK);
    let refreshed: njord_model::AdminResponse = serde_json::from_slice(
        &axum::body::to_bytes(r.into_body(), usize::MAX)
            .await
            .unwrap(),
    )
    .unwrap();
    assert_ne!(refreshed.signature.uuid, minted.signature.uuid);
    let r = verify(
        json.clone(),
        vec![
            ("x-forwarded-proto", "https"),
            ("x-forwarded-host", "charts.example.org"),
        ],
    )
    .await
    .unwrap();
    assert_eq!(r.status(), StatusCode::UNAUTHORIZED);
    let mut tampered = minted.signature.clone();
    tampered.uuid = "not-the-signed-uuid".into();
    let r = verify(serde_json::to_vec(&tampered).unwrap(), vec![])
        .await
        .unwrap();
    assert_eq!(r.status(), StatusCode::UNAUTHORIZED);
    let r = verify(b"{\"not\": \"a signature\"}".to_vec(), vec![])
        .await
        .unwrap();
    assert_eq!(r.status(), StatusCode::BAD_REQUEST);
}

/// Routes that answer without touching the database: upload names, the text answers of
/// `POST /v1/regions`, the empty 400s, and the reset of an idle server.
#[tokio::test]
async fn write_routes_without_a_database() {
    let state = common::offline_state("behaviour_write");
    let sig = signature(&state);
    let signed = |path: &str| {
        let sep = if path.contains('?') { '&' } else { '?' };
        format!("{path}{sep}signature={sig}")
    };
    for bad in [
        "../evil.zip",
        "sub/evil.zip",
        "evil.txt",
        "..",
        "evil.zip%00",
        "x.zip.tmp",
        ".zip",
    ] {
        let r = send_to(
            state.clone(),
            "POST",
            &signed(&format!("/v1/enc_save?filename={bad}")),
            &[],
        )
        .await;
        assert_eq!(r.status(), StatusCode::BAD_REQUEST, "{bad}");
    }
    let r = send_to(state.clone(), "POST", &signed("/v1/enc_save"), &[]).await;
    assert_eq!(r.status(), StatusCode::BAD_REQUEST);
    let r = send_to(state.clone(), "DELETE", &signed("/v1/chart"), &[]).await;
    assert_eq!(r.status(), StatusCode::BAD_REQUEST);
    let r = send_to(state.clone(), "POST", &signed("/v1/regions"), &[]).await;
    assert_eq!(r.status(), StatusCode::BAD_REQUEST);
    assert_eq!(
        r.headers()[header::CONTENT_TYPE],
        "text/plain; charset=UTF-8"
    );
    assert_eq!(
        axum::body::to_bytes(r.into_body(), usize::MAX)
            .await
            .unwrap(),
        &b"missing required query parameter: name"[..]
    );
    let r = send_to(state.clone(), "POST", &signed("/v1/regions?name=NOPE"), &[]).await;
    assert_eq!(r.status(), StatusCode::NOT_FOUND);
    assert_eq!(
        axum::body::to_bytes(r.into_body(), usize::MAX)
            .await
            .unwrap(),
        &b"no configured region named NOPE"[..]
    );
    // The reset: lock cleared, directories recreated, status Idle.
    std::fs::create_dir_all(&state.save_dir).unwrap();
    std::fs::write(state.save_dir.join("left.zip"), "x").unwrap();
    std::fs::write(state.lock.path(), "someone-else").unwrap();
    let r = send_to(state.clone(), "DELETE", &signed("/v1/enc_save"), &[]).await;
    assert_eq!(r.status(), StatusCode::ACCEPTED);
    assert!(state.lock.is_free());
    assert!(
        state.save_dir.is_dir() && std::fs::read_dir(&state.save_dir).unwrap().next().is_none()
    );
    assert!(state.ingest_dir.is_dir());
    assert_eq!(
        state.status.read(),
        njord_server::ingest::IngestStatus::idle_json()
    );
    // An upload lands in save/ as <name> with its admin cause sidecar and no .tmp left, and
    // announces Extracting(0).
    let body = b"PK\x05\x06".to_vec();
    let r = router(state.clone())
        .oneshot(
            Request::builder()
                .method("POST")
                .uri(signed("/v1/enc_save?filename=A.ZIP"))
                .header(header::HOST, "localhost:9000")
                .body(Body::from(body.clone()))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(r.status(), StatusCode::OK);
    assert_eq!(
        axum::body::to_bytes(r.into_body(), usize::MAX)
            .await
            .unwrap(),
        &br#"{"zipFile":"A.ZIP"}"#[..]
    );
    assert_eq!(std::fs::read(state.save_dir.join("A.ZIP")).unwrap(), body);
    let mut names: Vec<String> = std::fs::read_dir(&state.save_dir)
        .unwrap()
        .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    assert_eq!(names, ["A.ZIP", "A.ZIP.meta.json"]);
    assert_eq!(
        std::fs::read_to_string(state.save_dir.join("A.ZIP.meta.json")).unwrap(),
        r#"{"cause":"admin"}"#
    );
    let status: njord_model::WsMsg = serde_json::from_str(&state.status.read()).unwrap();
    assert_eq!(status, njord_model::WsMsg::Extracting { progress: 0.0 });
    // The status socket is a GET route like any other: another verb is 405.
    let r = send_to(state.clone(), "POST", "/v1/ws/enc_process", &[]).await;
    assert_eq!(r.status(), StatusCode::METHOD_NOT_ALLOWED);
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

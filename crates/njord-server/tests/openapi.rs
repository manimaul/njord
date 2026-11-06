//! The OpenAPI document and Swagger UI (`src/openapi.rs`): every route is in the document, the
//! document is self-consistent, and the UI is served under `/v1/api_docs/`.
//!
//! No test here queries the database.

mod common;

use std::collections::BTreeSet;
use std::sync::{Arc, LazyLock};

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use axum::response::Response;
use njord_server::openapi::{API_DOCS_PATH, DARK_MODE_ROLES, OPENAPI_PATH};
use njord_server::{AppState, router};
use serde_json::Value;
use tower::ServiceExt;

static STATE: LazyLock<Arc<AppState>> = LazyLock::new(|| common::offline_state("openapi"));

/// Every route [`router`] mounts, as the document templates them: axum's `{*path}` wildcards
/// are `{path}` (`src/openapi.rs`). A route added to the router shows up here, or the test
/// fails; a route taken out of the document does too.
const ROUTES: [&str; 25] = [
    "/v1/about",
    "/v1/about/{path}",
    "/v1/admin",
    "/v1/admin/verify",
    "/v1/cache",
    "/v1/change_reports",
    "/v1/change_reports/{date}",
    "/v1/chart",
    "/v1/chart_catalog",
    "/v1/chart_editions",
    "/v1/content",
    "/v1/content/{path}",
    "/v1/enc_save",
    "/v1/enc_url",
    "/v1/feature/{by}/{arg}",
    "/v1/geojson",
    "/v1/icon/{name}",
    "/v1/nuke",
    "/v1/regions",
    "/v1/regions/{archive}",
    "/v1/sector_svg/{theme}/{sectr1}/{sectr2}/{color}",
    "/v1/style/{depth}/{theme}",
    "/v1/tile/{z}/{x}/{y}",
    "/v1/tile_json",
    "/v1/ws/enc_process",
];

async fn send(method: &str, path: &str) -> Response {
    let request = Request::builder()
        .method(method)
        .uri(path)
        .header(header::HOST, "localhost:9000")
        .body(Body::empty())
        .unwrap();
    router(STATE.clone()).oneshot(request).await.unwrap()
}

async fn body_text(response: Response) -> String {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    String::from_utf8(bytes.to_vec()).unwrap()
}

fn content_type(response: &Response) -> &str {
    response
        .headers()
        .get(header::CONTENT_TYPE)
        .map(|v| v.to_str().unwrap())
        .unwrap_or("")
}

async fn document() -> Value {
    let response = send("GET", OPENAPI_PATH).await;
    assert_eq!(response.status(), StatusCode::OK);
    assert!(
        content_type(&response).starts_with("application/json"),
        "{}",
        content_type(&response)
    );
    let text = body_text(response).await;
    // For inspection: `target/tmp/openapi.json`.
    let _ = std::fs::write(
        std::path::Path::new(env!("CARGO_TARGET_TMPDIR")).join("openapi.json"),
        &text,
    );
    serde_json::from_str(&text).unwrap()
}

#[tokio::test]
async fn document_lists_every_route() {
    let doc = document().await;
    assert_eq!(doc["openapi"], "3.1.0");
    assert_eq!(doc["info"]["title"], "Njord");
    assert_eq!(
        doc["info"]["version"],
        njord_server::handlers::about::VERSION
    );
    let paths: BTreeSet<&str> = doc["paths"]
        .as_object()
        .unwrap()
        .keys()
        .map(String::as_str)
        .collect();
    let want: BTreeSet<&str> = ROUTES.into_iter().collect();
    assert_eq!(paths, want);
}

/// Every `{param}` of a path template is declared by every operation on it, operation ids are
/// unique, every operation has a tag, a summary and a response, every `$ref` resolves, and the
/// two security schemes exist and are used.
#[tokio::test]
async fn document_is_consistent() {
    let doc = document().await;
    let schemas = doc["components"]["schemas"].as_object().unwrap();
    let security_schemes = doc["components"]["securitySchemes"].as_object().unwrap();
    assert!(security_schemes.contains_key("admin_signature"));
    assert!(security_schemes.contains_key("admin_basic"));
    assert_eq!(security_schemes["admin_signature"]["in"], "query");
    assert_eq!(security_schemes["admin_signature"]["name"], "signature");

    let mut operation_ids = BTreeSet::new();
    let mut used_schemes = BTreeSet::new();
    let mut failures = Vec::new();
    for (path, item) in doc["paths"].as_object().unwrap() {
        let template_params: BTreeSet<&str> = path
            .split('/')
            .filter_map(|s| s.strip_prefix('{').and_then(|s| s.strip_suffix('}')))
            .collect();
        for (method, op) in item.as_object().unwrap() {
            let at = format!("{} {path}", method.to_uppercase());
            let declared: BTreeSet<&str> = op["parameters"]
                .as_array()
                .map(|ps| {
                    ps.iter()
                        .filter(|p| p["in"] == "path")
                        .map(|p| p["name"].as_str().unwrap())
                        .collect()
                })
                .unwrap_or_default();
            if declared != template_params {
                failures.push(format!(
                    "{at}: path params {declared:?} != {template_params:?}"
                ));
            }
            if let Some(ps) = op["parameters"].as_array() {
                for p in ps.iter().filter(|p| p["in"] == "path") {
                    if p["required"] != true {
                        failures.push(format!("{at}: path param {} not required", p["name"]));
                    }
                }
            }
            let id = op["operationId"].as_str().unwrap_or("").to_owned();
            if id.is_empty() || !operation_ids.insert(id.clone()) {
                failures.push(format!("{at}: operationId {id:?} missing or duplicate"));
            }
            if op["tags"].as_array().is_none_or(Vec::is_empty) {
                failures.push(format!("{at}: no tag"));
            }
            if op["summary"].as_str().is_none_or(str::is_empty) {
                failures.push(format!("{at}: no summary"));
            }
            if op["responses"].as_object().is_none_or(|r| r.is_empty()) {
                failures.push(format!("{at}: no responses"));
            }
            if let Some(reqs) = op["security"].as_array() {
                for req in reqs {
                    for scheme in req.as_object().unwrap().keys() {
                        if !security_schemes.contains_key(scheme) {
                            failures.push(format!("{at}: unknown security scheme {scheme}"));
                        }
                        used_schemes.insert(scheme.clone());
                    }
                }
            }
        }
    }
    assert_eq!(
        used_schemes,
        BTreeSet::from(["admin_basic".to_owned(), "admin_signature".to_owned()])
    );

    fn refs<'a>(value: &'a Value, out: &mut Vec<&'a str>) {
        match value {
            Value::Object(map) => {
                for (k, v) in map {
                    if k == "$ref" {
                        out.push(v.as_str().unwrap());
                    } else {
                        refs(v, out);
                    }
                }
            }
            Value::Array(items) => items.iter().for_each(|v| refs(v, out)),
            _ => {}
        }
    }
    let mut all_refs = Vec::new();
    refs(&doc, &mut all_refs);
    assert!(!all_refs.is_empty());
    for r in all_refs {
        let name = r
            .strip_prefix("#/components/schemas/")
            .unwrap_or_else(|| panic!("unexpected $ref {r}"));
        if !schemas.contains_key(name) {
            failures.push(format!("$ref to missing schema {name}"));
        }
    }
    // The shared wire types reach the document through the handlers' bodies.
    for name in [
        "Chart",
        "ChartCatalog",
        "ChangeReport",
        "AdminResponse",
        "RegionManifestEntry",
        "Style",
        "AboutDocument",
        "FeatureLookup",
    ] {
        if !schemas.contains_key(name) {
            failures.push(format!("schema {name} missing"));
        }
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

/// The string enums' hand-written schemas (`njord-model`'s `string_enum!`) are string enums of
/// the wire names, and a timestamp is a date-time string.
#[tokio::test]
async fn enum_and_timestamp_schemas() {
    let doc = document().await;
    let schemas = &doc["components"]["schemas"];
    assert_eq!(
        schemas["ChangeItem"]["properties"]["outcome"]["$ref"],
        "#/components/schemas/ChangeOutcome"
    );
    assert_eq!(schemas["ChangeOutcome"]["type"], "string");
    assert_eq!(
        schemas["ChangeOutcome"]["enum"],
        serde_json::json!(["new", "update", "delete", "nuke", "error"])
    );
    // A type alias of a map (`ColorLibrary`, `TileCacheCounts`) must be spelled out where it is
    // named, or the derive emits a component called after the generic type.
    assert!(
        schemas.get("IndexMap").is_none(),
        "an alias leaked as a component"
    );
    assert_eq!(schemas["Binary"]["type"], "string");
    assert_eq!(schemas["Binary"]["format"], "binary");
    assert_eq!(
        doc["paths"]["/v1/cache"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]
            ["additionalProperties"]["type"],
        "integer"
    );
    let occurred = &schemas["ChangeItem"]["properties"]["occurredAt"];
    assert_eq!(occurred["type"], "string");
    assert_eq!(occurred["format"], "date-time");
    // serde's `rename_all = "camelCase"` and explicit renames are honored.
    let chart = schemas["Chart"]["properties"].as_object().unwrap();
    assert!(chart.contains_key("featureCount"));
    assert!(chart.contains_key("file_name"));
    assert!(!chart.contains_key("feature_count"));
}

#[tokio::test]
async fn swagger_ui_is_served() {
    let response = send("GET", API_DOCS_PATH).await;
    assert_eq!(response.status(), StatusCode::SEE_OTHER);
    assert_eq!(
        response.headers()[header::LOCATION],
        format!("{API_DOCS_PATH}/").as_str()
    );

    let response = send("GET", &format!("{API_DOCS_PATH}/")).await;
    assert_eq!(response.status(), StatusCode::OK);
    assert!(content_type(&response).starts_with("text/html"));
    let html = body_text(response).await;
    assert!(html.contains("swagger-ui"), "{html}");

    let response = send("GET", &format!("{API_DOCS_PATH}/swagger-initializer.js")).await;
    assert_eq!(response.status(), StatusCode::OK);
    let js = body_text(response).await;
    assert!(js.contains(OPENAPI_PATH), "{js}");

    let response = send("GET", &format!("{API_DOCS_PATH}/nope.js")).await;
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
}

/// The page is the server's own: no top bar (`BaseLayout`), the theme script, and `theme.css`
/// next to it, with `swagger-ui.css` rewritten so its dark mode takes the site's colours
/// (`openapi::themed_stylesheet`).
#[tokio::test]
async fn swagger_ui_follows_the_site_theme() {
    let html = body_text(send("GET", &format!("{API_DOCS_PATH}/")).await).await;
    for needle in [
        "./theme.css",
        "dark-mode",
        "data-theme",
        "localStorage.getItem(\"Theme\")",
    ] {
        assert!(html.contains(needle), "{needle} not in {html}");
    }
    assert_eq!(
        html,
        body_text(send("GET", &format!("{API_DOCS_PATH}/index.html")).await).await
    );

    let js = body_text(send("GET", &format!("{API_DOCS_PATH}/swagger-initializer.js")).await).await;
    assert!(js.contains("\"layout\": \"BaseLayout\""), "{js}");

    let response = send("GET", &format!("{API_DOCS_PATH}/theme.css")).await;
    assert_eq!(response.status(), StatusCode::OK);
    assert!(content_type(&response).starts_with("text/css"));
    let theme = body_text(response).await;
    for mode in ["dusk", "night"] {
        assert!(
            theme.contains(&format!(":root[data-theme=\"{mode}\"]")),
            "{theme}"
        );
    }

    let response = send("GET", &format!("{API_DOCS_PATH}/swagger-ui.css")).await;
    assert_eq!(response.status(), StatusCode::OK);
    assert!(content_type(&response).starts_with("text/css"));
    let css = body_text(response).await;
    assert!(
        css.contains("html.dark-mode"),
        "not Swagger UI's stylesheet"
    );
    for (color, role) in DARK_MODE_ROLES {
        let wrapped = css
            .matches(&format!("var(--nj-sw-{role}, {color})"))
            .count();
        assert!(
            wrapped > 0,
            "{color} ({role}) is no longer in Swagger UI's dark mode"
        );
        assert_eq!(
            css.matches(color).count(),
            wrapped,
            "{color} ({role}) still literal somewhere in the stylesheet"
        );
        assert!(
            theme.contains(&format!("--nj-sw-{role}:")),
            "theme.css sets no --nj-sw-{role}"
        );
    }
}

/// The document and the UI are plain axum routes, outside `handlers::api`: other verbs are
/// still 405, with axum's `Allow` header.
#[tokio::test]
async fn other_verbs_are_refused() {
    for path in [OPENAPI_PATH, &format!("{API_DOCS_PATH}/")] {
        let response = send("POST", path).await;
        assert_eq!(response.status(), StatusCode::METHOD_NOT_ALLOWED, "{path}");
    }
}

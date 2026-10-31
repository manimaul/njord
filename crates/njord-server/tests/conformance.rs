//! The conformance goldens (`conformance/golden/`, RUST_PORT_PLAN.md §5.5) that need no database,
//! replayed in-process against [`njord_server::router`] and compared as `conformance/lib/engine.py`
//! verify mode compares them: status; content type ignoring case and whitespace; captured
//! headers; and the body reduced by the golden's kind — `json` and `style` parsed, layer ids
//! normalised (`style`), masked by JSON pointer and the base URL replaced by `{BASE}`; `text` as
//! a `{BASE}`-normalised string; `bytes` as size and sha256; `html` (the web app) by status and
//! content type only. A golden's `port` block replaces the recorded fields, as for
//! `--server-impl rust`. JSON bodies are compared as `mask.differences` compares them: object key
//! order ignored, an integer equal to a float (key order and number types are pinned per wire
//! type by `njord-model`'s golden round trips), after `engine.comparable_body` drops the `null`
//! members of GeoJSON objects on both sides (Kotlin writes `"bbox": null`, `"crs": null`,
//! `"id": null`; the port leaves them out). A `tile` body is compared only when both sides decode
//! (as in `engine.compare`); this replay does not decode MVT, so it checks a tile route's status
//! and content type only.
//!
//! Every golden group is either replayed here or listed in [`SKIPPED`] with its reason, so a new
//! group is a visible decision.

mod common;

use std::sync::Arc;

use axum::body::Body;
use axum::http::{Request, header};
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use njord_model::AdminSignature;
use njord_server::{AppState, router};
use njord_testdata::{Failures, golden_dir, json_files, load};
use serde_json::{Map, Value, json};
use sha2::{Digest, Sha256};
use tokio::sync::OnceCell;
use tower::ServiceExt;

/// The base URL the in-process requests claim (`Host: localhost:9000`).
const BASE: &str = "http://localhost:9000";

/// Goldens that are not replayed here, with the reason. `*` ends a prefix pattern.
const SKIPPED: &[(&str, &str)] = &[
    (
        "api_03_tiles/tile_*",
        "tile bodies are compared decoded by P-TE-5 (tests/tiles.rs) and by the live pytest run, \
         whose mapbox_vector_tile decoding is not reproduced here",
    ),
    (
        "api_03_tiles/tile_info_14_2619_5744",
        "WGS-84 vertices from boolean operations differ from GEOS in the 11th decimal (geo's grid \
         snapping); the live suite compares them rounded to 7 decimals with canonical ring \
         starts, and P-TE-5 (tests/tiles.rs) to 1e-7",
    ),
    (
        "api_06_admin/admin_ok",
        "minting signatures (GET /v1/admin) is Phase 4",
    ),
    ("api_06_admin/verify_*", "POST /v1/admin/verify is Phase 4"),
    ("api_08_ingest/*", "ingest (Phase 4)"),
    (
        "api_09_mutations/*",
        "mutating sequence: insert, read back, delete (Phase 4)",
    ),
    (
        "api_10_regions/archive_full",
        "needs a REGION_04 archive rendered by the export worker (Phase 4); ranges are checked in \
         tests/behaviour.rs on a stand-in archive",
    ),
    (
        "api_10_regions/archive_range_*",
        "needs a rendered REGION_04 archive (Phase 4)",
    ),
    ("api_10_regions/regions_post_*", "mutating route (Phase 4)"),
    ("api_99_nuke/*", "mutating route (Phase 4)"),
];

/// Golden groups replayed here.
const REPLAYED: &[&str] = &[
    "api_01_about",
    "api_02_style",
    "api_03_tiles",
    "api_04_charts",
    "api_05_content",
    "api_06_admin",
    "api_07_auth",
    "api_10_regions",
    "api_11_forwarded",
    "api_12_errors",
];

fn skip_reason(name: &str) -> Option<&'static str> {
    SKIPPED.iter().find_map(|(pattern, reason)| {
        let matches = match pattern.strip_suffix('*') {
            Some(prefix) => {
                name.starts_with(prefix)
                    && ![
                        "api_03_tiles/tile_json",
                        "api_03_tiles/tile_non_integer",
                        "api_03_tiles/tile_info_14_2619_5744",
                    ]
                    .contains(&name)
            }
            None => name == *pattern,
        };
        matches.then_some(*reason)
    })
}

static STATE: OnceCell<Arc<AppState>> = OnceCell::const_new();

async fn state() -> Arc<AppState> {
    STATE
        .get_or_init(|| common::state("conformance"))
        .await
        .clone()
}

/// What `GET /v1/admin` would hand the suite: a fresh signature for `{BASE}` under the shipped
/// `adminKey`, URL-encoded for the query string.
fn mint_signature(admin_key: &str) -> String {
    let date = jiff::Timestamp::now();
    let uuid = "00000000-0000-4000-8000-000000000000".to_owned();
    let mac = njord_server::signature::hmac(
        admin_key,
        &[BASE, &njord_model::instant::format(date), &uuid],
    );
    let signature = AdminSignature {
        date,
        signature: STANDARD.encode(mac),
        uuid,
        expiration_date: date,
    };
    let encoded = STANDARD.encode(serde_json::to_vec(&signature).unwrap());
    percent_encoding::utf8_percent_encode(&encoded, percent_encoding::NON_ALPHANUMERIC).to_string()
}

struct Captured {
    status: u16,
    content_type: String,
    headers: Map<String, Value>,
    body: Value,
}

async fn capture(golden: &Value) -> Captured {
    let request = &golden["request"];
    let kind = request["kind"].as_str().unwrap();
    let state = state().await;
    let mut path = request["path"].as_str().unwrap().to_owned();
    if request["signed"].as_bool() == Some(true) {
        let sep = if path.contains('?') { '&' } else { '?' };
        path = format!(
            "{path}{sep}signature={}",
            mint_signature(&state.config.admin_key)
        );
    }
    let mut builder = Request::builder()
        .method(request["method"].as_str().unwrap())
        .uri(path)
        .header(header::HOST, "localhost:9000")
        .header(header::ACCEPT, "*/*")
        .header(header::USER_AGENT, "python-requests/2.34");
    for (k, v) in request["headers"].as_object().unwrap() {
        builder = builder.header(k.as_str(), v.as_str().unwrap());
    }
    let response = router(state)
        .oneshot(builder.body(Body::empty()).unwrap())
        .await
        .unwrap();
    let status = response.status().as_u16();
    let header_str = |name: &str| {
        response
            .headers()
            .get(name)
            .map(|v| v.to_str().unwrap().replace(BASE, "{BASE}"))
    };
    let content_type = header_str("content-type").unwrap_or_default();
    let mut headers = Map::new();
    for h in golden["capture_headers"].as_array().unwrap() {
        let h = h.as_str().unwrap();
        headers.insert(
            h.to_owned(),
            header_str(h).map(Value::String).unwrap_or(Value::Null),
        );
    }
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let text = || String::from_utf8_lossy(&bytes).replace(BASE, "{BASE}");
    let body = match kind {
        "bytes" => json!({"size": bytes.len(), "sha256": format!("{:x}", Sha256::digest(&bytes))}),
        "html" => json!({"size": bytes.len()}),
        "text" => Value::String(text()),
        _ if !bytes.is_empty() && content_type.contains("json") => {
            let mut body: Value = serde_json::from_slice(&bytes).unwrap();
            if kind == "style" {
                normalize_style(&mut body);
            }
            if kind == "info"
                && let Value::Array(items) = &mut body
            {
                sort_features(items, "props");
            }
            if kind == "geojson"
                && let Some(Value::Array(items)) = body.get_mut("features")
            {
                sort_features(items, "properties");
            }
            for pointer in golden["masks"].as_array().unwrap() {
                apply_mask(&mut body, pointer.as_str().unwrap());
            }
            normalize_base(&mut body);
            body
        }
        _ if !bytes.is_empty() => Value::String(text()),
        _ => Value::Null,
    };
    Captured {
        status,
        content_type,
        headers,
        body,
    }
}

/// `engine.expected_for(golden, "rust")`.
fn expected(golden: &Value) -> Map<String, Value> {
    let mut expected = golden["expected"].as_object().unwrap().clone();
    if let Some(port) = golden.get("port").and_then(Value::as_object) {
        for k in ["status", "content_type", "headers", "body"] {
            if let Some(v) = port.get(k) {
                expected.insert(k.to_owned(), v.clone());
            }
        }
    }
    expected
}

fn compare(golden: &Value, actual: &Captured) -> Result<(), String> {
    let mut expected = expected(golden);
    let kind = golden["request"]["kind"].as_str().unwrap();
    let exact = !(golden["volatile"].as_bool().unwrap_or(false) || kind == "html");
    // `engine.comparable_body`, on both sides: GeoJSON nulls dropped, then the same sort, so the
    // order cannot differ on a Python/Rust detail of the sort key.
    let mut actual_body = actual.body.clone();
    for body in [expected.get_mut("body"), Some(&mut actual_body)]
        .into_iter()
        .flatten()
    {
        drop_geojson_nulls(body);
        match (kind, body) {
            ("info", Value::Array(items)) => sort_features(items, "props"),
            ("geojson", Value::Object(body)) => {
                if let Some(Value::Array(items)) = body.get_mut("features") {
                    sort_features(items, "properties");
                }
            }
            _ => {}
        }
    }
    let mut diffs = Vec::new();
    if expected["status"] != json!(actual.status) {
        diffs.push(format!(
            "status: expected {}, got {}",
            expected["status"], actual.status
        ));
    }
    let norm = |s: &str| {
        s.to_ascii_lowercase()
            .split_whitespace()
            .collect::<String>()
    };
    let expected_ct = expected["content_type"].as_str().unwrap_or_default();
    if norm(expected_ct) != norm(&actual.content_type) {
        diffs.push(format!(
            "content-type: expected {expected_ct:?}, got {:?}",
            actual.content_type
        ));
    }
    for (h, ev) in expected["headers"].as_object().unwrap() {
        let av = actual.headers.get(h).unwrap_or(&Value::Null);
        if exact && ev != av || !exact && ev.is_null() != av.is_null() {
            diffs.push(format!("header {h}: expected {ev}, got {av}"));
        }
    }
    let compare_body = kind != "tile" || expected["body"].is_object() && actual_body.is_object();
    if exact
        && compare_body
        && let Some(d) = py_difference(&expected["body"], &actual_body, "")
    {
        diffs.push(format!("body {d}"));
    }
    if diffs.is_empty() {
        Ok(())
    } else {
        Err(diffs.join("; "))
    }
}

/// `engine._sorted_features`: by layer, `LNAM`, props and geometry, each as Python's
/// `json.dumps(sort_keys=True)` would order them (the key order of a sorted-key dump is all that
/// matters for a stable sort).
fn sort_features(items: &mut [Value], props_key: &str) {
    fn sorted(v: &Value) -> String {
        match v {
            Value::Object(m) => {
                let mut keys: Vec<&String> = m.keys().collect();
                keys.sort();
                let parts: Vec<String> = keys
                    .iter()
                    .map(|k| format!("{}: {}", json!(k), sorted(&m[*k])))
                    .collect();
                format!("{{{}}}", parts.join(", "))
            }
            Value::Array(a) => format!("[{}]", a.iter().map(sorted).collect::<Vec<_>>().join(", ")),
            other => other.to_string(),
        }
    }
    let key = |f: &Value| {
        let props = f.get(props_key).cloned().unwrap_or(Value::Null);
        let lnam = props
            .get("LNAM")
            .map(|l| {
                l.as_str()
                    .map(str::to_owned)
                    .unwrap_or_else(|| l.to_string())
            })
            .unwrap_or_default();
        let geometry = f
            .get("geometry")
            .or_else(|| f.get("geom"))
            .cloned()
            .unwrap_or(Value::Null);
        (
            f.get("layer")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_owned(),
            lnam,
            sorted(&props),
            sorted(&geometry),
        )
    };
    items.sort_by_cached_key(key);
}

/// `mask.differences`: the first difference by JSON pointer, Python's equality (key order
/// ignored, `1 == 1.0`, `true` not a number).
fn py_difference(a: &Value, b: &Value, path: &str) -> Option<String> {
    match (a, b) {
        (Value::Number(x), Value::Number(y)) => {
            (x.as_f64() != y.as_f64()).then(|| format!("{path}: {x} != {y}"))
        }
        (Value::Object(x), Value::Object(y)) => {
            let mut keys: Vec<&String> = x.keys().chain(y.keys()).collect();
            keys.sort();
            keys.dedup();
            keys.into_iter().find_map(|k| match (x.get(k), y.get(k)) {
                (Some(p), Some(q)) => py_difference(p, q, &format!("{path}/{k}")),
                (Some(_), None) => Some(format!("{path}/{k}: missing")),
                _ => Some(format!("{path}/{k}: unexpected")),
            })
        }
        (Value::Array(x), Value::Array(y)) => {
            if x.len() != y.len() {
                return Some(format!(
                    "{path}: {} items expected, got {}",
                    x.len(),
                    y.len()
                ));
            }
            x.iter()
                .zip(y)
                .enumerate()
                .find_map(|(i, (p, q))| py_difference(p, q, &format!("{path}/{i}")))
        }
        _ => (a != b).then(|| format!("{path}: {a} != {b}")),
    }
}

/// `mask.GEOJSON_TYPES`.
const GEOJSON_TYPES: [&str; 9] = [
    "Point",
    "MultiPoint",
    "LineString",
    "MultiLineString",
    "Polygon",
    "MultiPolygon",
    "GeometryCollection",
    "Feature",
    "FeatureCollection",
];

/// `mask.drop_geojson_nulls`: the `null` members of objects whose `"type"` is a GeoJSON type are
/// removed; nulls anywhere else stay.
fn drop_geojson_nulls(v: &mut Value) {
    match v {
        Value::Array(items) => items.iter_mut().for_each(drop_geojson_nulls),
        Value::Object(map) => {
            let geojson = map
                .get("type")
                .and_then(Value::as_str)
                .is_some_and(|t| GEOJSON_TYPES.contains(&t));
            if geojson {
                map.retain(|_, value| !value.is_null());
            }
            map.values_mut().for_each(drop_geojson_nulls);
        }
        _ => {}
    }
}

/// `mask.normalize_base`.
fn normalize_base(v: &mut Value) {
    match v {
        Value::String(s) => *s = s.replace(BASE, "{BASE}"),
        Value::Array(items) => items.iter_mut().for_each(normalize_base),
        Value::Object(map) => map.values_mut().for_each(normalize_base),
        _ => {}
    }
}

/// `mask.apply_masks` for one pointer: `*` matches every list index or object key; missing
/// targets are ignored; `""` masks the whole body.
fn apply_mask(v: &mut Value, pointer: &str) {
    fn apply(node: &mut Value, segments: &[String]) {
        let Some((head, rest)) = segments.split_first() else {
            *node = json!("<masked>");
            return;
        };
        match node {
            Value::Array(items) if head == "*" => items.iter_mut().for_each(|i| apply(i, rest)),
            Value::Array(items) => {
                if let Some(item) = head.parse::<usize>().ok().and_then(|i| items.get_mut(i)) {
                    apply(item, rest);
                }
            }
            Value::Object(map) if head == "*" => map.values_mut().for_each(|i| apply(i, rest)),
            Value::Object(map) => {
                if let Some(item) = map.get_mut(head) {
                    apply(item, rest);
                }
            }
            _ => {}
        }
    }
    if pointer.is_empty() {
        *v = json!("<masked>");
        return;
    }
    let segments: Vec<String> = pointer[1..]
        .split('/')
        .map(|s| s.replace("~1", "/").replace("~0", "~"))
        .collect();
    apply(v, &segments);
}

/// `mask.normalize_style`: a layer id's trailing counter becomes `#<ordinal among the layers
/// with the same stem>`.
fn normalize_style(style: &mut Value) {
    let Some(layers) = style.get_mut("layers").and_then(Value::as_array_mut) else {
        return;
    };
    let mut seen: std::collections::HashMap<String, usize> = Default::default();
    for layer in layers {
        let Some(id) = layer.get("id").and_then(Value::as_str) else {
            continue;
        };
        let stem = id.trim_end_matches(|c: char| c.is_ascii_digit());
        if stem.len() == id.len() {
            continue;
        }
        let n = seen.entry(stem.to_owned()).or_default();
        *n += 1;
        layer["id"] = json!(format!("{stem}#{n}"));
    }
}

/// Replays every golden of `group` (or the single golden `group/name`), skipping the listed ones.
async fn replay(group: &str) {
    let (dir, only) = match group.split_once('/') {
        Some((dir, name)) => (dir, Some(name)),
        None => (group, None),
    };
    let mut failures = Failures::default();
    let (mut checked, mut skipped) = (0, 0);
    for path in json_files(&golden_dir().join(dir)) {
        let stem = path.file_stem().unwrap().to_str().unwrap();
        if only.is_some_and(|n| n != stem) {
            continue;
        }
        let name = format!("{dir}/{stem}");
        if let Some(reason) = skip_reason(&name) {
            println!("skip {name}: {reason}");
            skipped += 1;
            continue;
        }
        let golden = load(&path);
        let actual = capture(&golden).await;
        checked += 1;
        failures.check(&name, compare(&golden, &actual));
    }
    println!("{group}: {checked} replayed, {skipped} skipped");
    failures.assert_none(checked);
}

#[tokio::test]
async fn api_01_about() {
    replay("api_01_about").await;
}

#[tokio::test]
async fn api_02_style() {
    replay("api_02_style").await;
}

#[tokio::test]
async fn api_03_tiles() {
    replay("api_03_tiles").await;
}

#[tokio::test]
async fn api_04_charts() {
    replay("api_04_charts").await;
}

#[tokio::test]
async fn api_05_content() {
    replay("api_05_content").await;
}

#[tokio::test]
async fn api_06_admin() {
    replay("api_06_admin").await;
}

#[tokio::test]
async fn api_07_auth() {
    replay("api_07_auth").await;
}

#[tokio::test]
async fn api_10_regions() {
    replay("api_10_regions").await;
}

#[tokio::test]
async fn api_11_forwarded() {
    replay("api_11_forwarded").await;
}

#[tokio::test]
async fn api_12_errors() {
    replay("api_12_errors").await;
}

/// Every golden is replayed by a test above or skipped with a reason.
#[test]
fn every_golden_is_accounted_for() {
    let mut unaccounted = Vec::new();
    for entry in std::fs::read_dir(golden_dir()).unwrap() {
        let dir = entry.unwrap().path();
        if !dir.is_dir() {
            continue;
        }
        let group = dir.file_name().unwrap().to_str().unwrap().to_owned();
        for path in json_files(&dir) {
            let name = format!("{group}/{}", path.file_stem().unwrap().to_str().unwrap());
            if !REPLAYED.contains(&group.as_str()) && skip_reason(&name).is_none() {
                unaccounted.push(name);
            }
        }
    }
    assert!(
        unaccounted.is_empty(),
        "goldens neither replayed nor skipped: {unaccounted:?}"
    );
}

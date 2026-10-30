//! Round trips of every JSON body in `conformance/golden/` through the wire type its route
//! responds with (Phase 1 exit criterion, RUST_PORT_PLAN.md §6). A new golden whose route is not
//! mapped here fails the test, so each new response shape is a visible decision.

mod common;

use std::path::Path;

use common::{Failures, Nulls, golden_dir, json_files, round_trip};
use njord_model::colors::style_path;
use njord_model::geojson::GeoJson;
use njord_model::{
    AboutJson, AdminResponse, Chart, ChartCatalog, ChartEditions, ChartFeatureInfo, ChartInsert,
    ColorLibrary, CustomTheme, Depth, FeatureInsert, FeatureRecord, IconInfo, IndexMap,
    LayerQueryResultPage, Pair, RegionManifestEntry, S57Attribute, S57ExpectedInput, S57Object,
    Style, Theme, ThemeMode, TileCacheCounts, TileJson, WsMsg,
};
use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::Value;

type Check = fn(&Value) -> Result<(), String>;

fn check<T: DeserializeOwned + Serialize>(body: &Value) -> Result<(), String> {
    round_trip::<T>(body, Nulls::Explicit).map(|_| ())
}

/// The wire type of a response body, by method and request path.
fn response_type(method: &str, path: &str) -> Option<(&'static str, Check)> {
    let route = path.split('?').next().unwrap();
    let query = path.split_once('?').map(|(_, q)| q).unwrap_or("");
    Some(match (method, route) {
        ("GET", "/v1/about/version") => ("AboutJson", check::<AboutJson>),
        ("GET", "/v1/about/s57objects") => ("S57Object map", check::<IndexMap<String, S57Object>>),
        ("GET", "/v1/about/s57attributes") => {
            ("S57Attribute map", check::<IndexMap<String, S57Attribute>>)
        }
        ("GET", "/v1/about/expectedInput") => (
            "S57ExpectedInput map",
            check::<IndexMap<String, Vec<S57ExpectedInput>>>,
        ),
        ("GET", "/v1/about/colors") => ("ColorLibrary", check::<ColorLibrary>),
        ("GET", "/v1/about/colors/custom") => (
            "custom ColorLibrary",
            check::<IndexMap<String, ColorLibrary>>,
        ),
        ("GET", r) if r.starts_with("/v1/style/") => ("Style", check::<Style>),
        ("GET", r) if r.starts_with("/v1/tile/") && query.contains("info=true") => {
            ("ChartFeatureInfo list", check::<Vec<ChartFeatureInfo>>)
        }
        ("GET", "/v1/tile_json") => ("TileJson", check::<TileJson>),
        ("GET" | "POST", "/v1/chart") => ("Chart", check::<Chart>),
        ("GET", "/v1/chart_catalog") => ("ChartCatalog", check::<ChartCatalog>),
        ("GET", "/v1/chart_editions") => ("ChartEditions", check::<ChartEditions>),
        ("GET", r) if r.starts_with("/v1/feature/layer/") => {
            ("LayerQueryResultPage", check::<LayerQueryResultPage>)
        }
        ("GET", r) if r.starts_with("/v1/feature/lnam/") => {
            ("FeatureRecord", check::<FeatureRecord>)
        }
        ("GET", "/v1/geojson") => ("GeoJson", check::<GeoJson>),
        ("POST", "/v1/geojson") => (
            "Pair<Int, FeatureInsert>",
            check::<Pair<i32, FeatureInsert>>,
        ),
        ("GET", r) if r.starts_with("/v1/content/sprites/") && r.ends_with(".json") => {
            ("IconInfo map", check::<IndexMap<String, IconInfo>>)
        }
        ("GET", "/v1/admin") | ("POST", "/v1/admin/verify") => {
            ("AdminResponse", check::<AdminResponse>)
        }
        ("GET", "/v1/cache") => ("TileCacheCounts", check::<TileCacheCounts>),
        ("GET", "/v1/regions") => (
            "RegionManifestEntry list",
            check::<Vec<RegionManifestEntry>>,
        ),
        // The final message of the P-API-8 ingest, collected from `/v1/ws/enc_process`.
        ("-", "<computed:api_08_ingest/completion_report>") => ("WsMsg", check::<WsMsg>),
        _ => return None,
    })
}

/// A typed stand-in for a masked value, by member name. Masked strings that are strings in the
/// wire type (uuids, versions, archive names) stay `"<masked>"`.
fn unmasked(member: &str) -> Option<Value> {
    match member {
        "id" | "lastId" | "archiveSize" | "ms" => Some(0.into()),
        "date" | "expirationDate" | "createdAt" => Some("2026-01-01T00:00:00Z".into()),
        _ => None,
    }
}

fn unmask(v: &mut Value, member: &str) {
    match v {
        Value::String(s) if s == "<masked>" => {
            if let Some(replacement) = unmasked(member) {
                *v = replacement;
            }
        }
        Value::Object(map) => map.iter_mut().for_each(|(k, v)| unmask(v, k)),
        Value::Array(items) => items.iter_mut().for_each(|v| unmask(v, member)),
        _ => {}
    }
}

fn goldens() -> Vec<(String, Value)> {
    let root = golden_dir();
    let mut out = Vec::new();
    for dir in std::fs::read_dir(&root).unwrap() {
        let dir = dir.unwrap().path();
        if !dir.is_dir() {
            continue;
        }
        for path in json_files(&dir) {
            let label = path
                .strip_prefix(&root)
                .unwrap_or(Path::new(""))
                .display()
                .to_string();
            out.push((label, common::load(&path)));
        }
    }
    out.sort_by(|a, b| a.0.cmp(&b.0));
    out
}

/// Bodies are JSON when the golden recorded a JSON content type, or (WebSocket messages) no
/// content type at all.
fn json_body(golden: &Value) -> Option<&Value> {
    let expected = &golden["expected"];
    let content_type = expected["content_type"].as_str().unwrap_or("");
    let body = &expected["body"];
    let is_json = content_type.contains("json") || (content_type.is_empty() && body.is_object());
    let ok = expected["status"]
        .as_u64()
        .is_none_or(|s| (200..300).contains(&s));
    (is_json && ok && (body.is_object() || body.is_array())).then_some(body)
}

#[test]
fn response_bodies() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for (label, golden) in goldens() {
        let Some(body) = json_body(&golden) else {
            continue;
        };
        let method = golden["request"]["method"].as_str().unwrap();
        let path = golden["request"]["path"].as_str().unwrap();
        let Some((type_name, check)) = response_type(method, path) else {
            failures.push(format!("{label}: no wire type mapped for {method} {path}"));
            continue;
        };
        let mut body = body.clone();
        unmask(&mut body, "");
        checked += 1;
        failures.check(format!("{label} ({type_name})"), check(&body));
    }
    failures.assert_none(checked);
}

/// Request bodies the suite sends must decode as the type the route receives, and the
/// deliberately invalid ones must not.
#[test]
fn request_bodies() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for (label, golden) in goldens() {
        let request = &golden["request"];
        let json = &request["json"];
        if !json.is_object() {
            continue;
        }
        let route = request["path"].as_str().unwrap().split('?').next().unwrap();
        let decoded = match (request["method"].as_str().unwrap(), route) {
            ("POST", "/v1/chart") => {
                serde_json::from_value::<ChartInsert>(json.clone()).map(|_| ())
            }
            ("POST", "/v1/geojson") => serde_json::from_value::<GeoJson>(json.clone()).map(|_| ()),
            (m, r) => {
                failures.push(format!("{label}: no request type mapped for {m} {r}"));
                continue;
            }
        };
        checked += 1;
        let invalid = label.ends_with("_invalid.json");
        match (decoded, invalid) {
            (Ok(()), false) | (Err(_), true) => {}
            (Ok(()), true) => failures.push(format!("{label}: invalid body decoded")),
            (Err(e), false) => failures.push(format!("{label}: {e}")),
        }
    }
    failures.assert_none(checked);
}

/// `style_path` builds the same URLs the suite requests for every known depth and theme.
#[test]
fn style_paths() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for (label, golden) in goldens() {
        let path = golden["request"]["path"].as_str().unwrap();
        let Some(rest) = path.strip_prefix("/v1/style/") else {
            continue;
        };
        let (segments, custom) = match rest.split_once("?c=") {
            Some((s, c)) => (s, Some(c)),
            None => (rest, None),
        };
        let Some((depth, mode)) = segments.split_once('/') else {
            continue;
        };
        let (Ok(depth), Some(mode)) = (
            depth.parse::<Depth>(),
            ThemeMode::ALL.iter().find(|m| m.path_name() == mode),
        ) else {
            continue; // the 404 cases
        };
        let theme = match custom {
            Some(name) => Theme::Custom(CustomTheme {
                mode: *mode,
                name: name.to_owned(),
            }),
            None => Theme::Mode(*mode),
        };
        checked += 1;
        if style_path(&theme, depth) != path {
            failures.push(format!("{label}: {} != {path}", style_path(&theme, depth)));
        }
    }
    failures.assert_none(checked);
}

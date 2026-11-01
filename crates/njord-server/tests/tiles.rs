//! The golden tiles, `testdata/geo/tiles/<z>_<x>_<y>.json`, against the tile encoder.
//!
//! Every tile is encoded by [`njord_server::tile_encoder`] over the seeded test database (the
//! three fixture cells and the 110m coastline), decoded with `njord_geo::mvt::view` and
//! canonicalised as the goldens are (layers by name; features by LNAM, props, geometry; feature
//! ids dropped; props with sorted keys), plus the choices that carry no meaning in MVT: where a
//! closed ring or line starts, and the order of the parts of a multi-geometry.
//!
//! Every feature must then be identical, or differ in one of the listed [`Divergence`]s, each a
//! known `geo`-versus-GEOS/JTS behaviour inside `njord-geo`; each is printed with the tile and
//! feature (`cargo test -p njord-server --test tiles -- --nocapture`), and anything else fails.
//! Props, feature presence and layer sets are never relaxed. Each tile is encoded [`ENCODES`] times
//! and must come out the same every time. The `_info.json` goldens are checked the same way in
//! WGS-84, with coordinates to [`INFO_TOLERANCE`] and ring orientation normalised.
//!
//! `NJORD_TILE_DUMP=1` writes the canonical Rust views under the test's temp directory.

mod common;

use std::collections::BTreeMap;

use common::tiles::{
    Divergence, MAX_SIMPLIFICATION_PX, canonical_tile, sorted_keys, tile_differences,
};
use njord_geo::mvt::view::decode_to_json;
use njord_server::tile_encoder::{self, TileXyz};
use njord_testdata::{Failures, first_difference, json_files, load, testdata};
use serde_json::{Value, json};

/// Degrees. GEOS and `geo` boolean operations agree to about 1e-9 of the extent.
const INFO_TOLERANCE: f64 = 1e-7;

fn tile_of(path: &std::path::Path) -> Option<(TileXyz, bool)> {
    let stem = path.file_stem()?.to_str()?;
    let (stem, info) = match stem.strip_suffix("_info") {
        Some(s) => (s, true),
        None => (stem, false),
    };
    let parts: Vec<i64> = stem
        .split('_')
        .map(|p| p.parse().ok())
        .collect::<Option<_>>()?;
    let [z, x, y] = parts[..] else { return None };
    Some((TileXyz { z: z as u32, x, y }, info))
}

/// How many times each tile is encoded; any feature that differs between encodes fails.
const ENCODES: usize = 3;

/// An `info` body in canonical form: props and geometry with sorted keys, entries sorted by
/// layer, `LNAM`, props and geometry.
fn canonical_info(info: &Value) -> Value {
    let mut items: Vec<Value> = info
        .as_array()
        .unwrap()
        .iter()
        .map(|e| json!({"layer": e["layer"], "props": sorted_keys(&e["props"]), "geom": sorted_keys(&e["geom"])}))
        .collect();
    items.sort_by_cached_key(|e| {
        (
            e["layer"].to_string(),
            e["props"]
                .get("LNAM")
                .map(Value::to_string)
                .unwrap_or_default(),
            e["props"].to_string(),
            e["geom"].to_string(),
        )
    });
    Value::Array(items)
}

/// `"<layer> <LNAM>"` of every feature whose geometry is not the same in all `views`.
fn unstable_features(views: &[Value]) -> std::collections::BTreeSet<String> {
    let index = |v: &Value| -> BTreeMap<String, Vec<String>> {
        let mut out: BTreeMap<String, Vec<String>> = BTreeMap::new();
        for layer in v["layers"].as_array().unwrap() {
            for f in layer["features"].as_array().unwrap() {
                let id = f["props"]
                    .get("LNAM")
                    .and_then(Value::as_str)
                    .map(str::to_owned)
                    .unwrap_or_else(|| f["props"].to_string());
                out.entry(format!("{} {id}", layer["name"].as_str().unwrap()))
                    .or_default()
                    .push(f["geometry"].to_string());
            }
        }
        out
    };
    let first = index(&views[0]);
    let mut unstable = std::collections::BTreeSet::new();
    for other in &views[1..] {
        let other = index(other);
        for (k, v) in first.iter().chain(other.iter()) {
            if first.get(k) != other.get(k) {
                unstable.insert(k.clone());
            }
            let _ = v;
        }
    }
    unstable
}

/// `first_difference` with floats equal within `tol`.
fn close(a: &Value, b: &Value, path: &str, tol: f64) -> Option<String> {
    match (a, b) {
        (Value::Number(x), Value::Number(y)) => {
            let (x, y) = (x.as_f64().unwrap(), y.as_f64().unwrap());
            ((x - y).abs() > tol).then(|| format!("{path}: {x} != {y}"))
        }
        (Value::Array(x), Value::Array(y)) => {
            if x.len() != y.len() {
                return Some(format!("{path}: length {} != {}", x.len(), y.len()));
            }
            x.iter()
                .zip(y)
                .enumerate()
                .find_map(|(i, (p, q))| close(p, q, &format!("{path}/{i}"), tol))
        }
        (Value::Object(x), Value::Object(y)) => {
            let (xk, yk): (Vec<_>, Vec<_>) = (x.keys().collect(), y.keys().collect());
            if xk != yk {
                return Some(format!("{path}: keys {xk:?} != {yk:?}"));
            }
            x.iter()
                .find_map(|(k, v)| close(v, &y[k], &format!("{path}/{k}"), tol))
        }
        _ => first_difference(a, b, path),
    }
}

/// GeoJSON counterpart of [`canonical_geometry`] for polygons: rings rotated to their smallest
/// vertex, holes and the polygons of a multi-polygon sorted.
fn normalise_geojson_rings(geom: &mut Value) {
    normalise_geojson_ring_starts(geom);
    let sort = |v: &mut Value| {
        if let Some(a) = v.as_array_mut() {
            a.sort_by_key(|x| x.to_string());
        }
    };
    match geom["type"].as_str() {
        Some("Polygon") => {
            if let Some(rings) = geom["coordinates"].as_array_mut()
                && rings.len() > 2
            {
                rings[1..].sort_by_key(|x| x.to_string());
            }
        }
        Some("MultiPolygon") => {
            for p in geom["coordinates"].as_array_mut().unwrap() {
                if let Some(rings) = p.as_array_mut()
                    && rings.len() > 2
                {
                    rings[1..].sort_by_key(|x| x.to_string());
                }
            }
            sort(&mut geom["coordinates"]);
        }
        _ => {}
    }
}

fn normalise_geojson_ring_starts(geom: &mut Value) {
    let rotate = |ring: &mut Value| {
        let ring = ring.as_array_mut().unwrap();
        ring.pop();
        let key = |v: &Value| (v[0].as_f64().unwrap(), v[1].as_f64().unwrap());
        let start = (0..ring.len())
            .min_by(|&i, &j| key(&ring[i]).partial_cmp(&key(&ring[j])).unwrap())
            .unwrap_or(0);
        ring.rotate_left(start);
        // Orientation: GEOS returns shells clockwise, `geo` counter-clockwise (RFC 7946).
        if ring.len() > 2 && key(&ring[ring.len() - 1]) < key(&ring[1]) {
            ring[1..].reverse();
        }
        if let Some(first) = ring.first().cloned() {
            ring.push(first);
        }
    };
    match geom["type"].as_str() {
        Some("Polygon") => geom["coordinates"]
            .as_array_mut()
            .unwrap()
            .iter_mut()
            .for_each(rotate),
        Some("MultiPolygon") => geom["coordinates"]
            .as_array_mut()
            .unwrap()
            .iter_mut()
            .flat_map(|p| p.as_array_mut().unwrap().iter_mut())
            .for_each(rotate),
        _ => {}
    }
}

#[tokio::test]
async fn p_te_5_golden_tiles() {
    let state = common::state("tiles").await;
    let dump = std::env::var("NJORD_TILE_DUMP").is_ok_and(|v| v == "1");
    let dump_dir =
        std::path::Path::new(env!("CARGO_TARGET_TMPDIR")).join("njord-server-tests/p_te_5");
    if dump {
        std::fs::create_dir_all(&dump_dir).unwrap();
    }
    let mut failures = Failures::default();
    let mut known: BTreeMap<Divergence, Vec<String>> = BTreeMap::new();
    let mut checked = 0;
    for path in json_files(&testdata().join("geo/tiles")) {
        let Some((t, info)) = tile_of(&path) else {
            continue;
        };
        let expected = load(&path)["cases"][0]["expected"].clone();
        let label = path.file_stem().unwrap().to_str().unwrap().to_owned();
        checked += 1;
        if info {
            let infos = tile_encoder::info(&state.db, state.symbology.clone(), t)
                .await
                .unwrap();
            let mut actual = canonical_info(&serde_json::to_value(&infos).unwrap());
            let mut expected = canonical_info(&expected);
            for item in actual
                .as_array_mut()
                .unwrap()
                .iter_mut()
                .chain(expected.as_array_mut().unwrap())
            {
                normalise_geojson_rings(&mut item["geom"]);
            }
            if dump {
                std::fs::write(
                    dump_dir.join(format!("{label}.json")),
                    serde_json::to_string_pretty(&actual).unwrap(),
                )
                .unwrap();
            }
            let (ea, aa) = (expected.as_array().unwrap(), actual.as_array().unwrap());
            let mut diffs = Vec::new();
            if ea.len() != aa.len() {
                diffs.push(format!("{} features expected, got {}", ea.len(), aa.len()));
            }
            for (i, (e, a)) in ea.iter().zip(aa).enumerate() {
                if let Some(d) = close(e, a, "", INFO_TOLERANCE) {
                    let lnam = e["props"]
                        .get("LNAM")
                        .and_then(Value::as_str)
                        .unwrap_or("-");
                    diffs.push(format!("{i} {} {lnam}: {d}", e["layer"]));
                }
            }
            failures.check(
                &label,
                if diffs.is_empty() {
                    Ok(())
                } else {
                    Err(diffs.join("\n    "))
                },
            );
        } else {
            let mut views = Vec::new();
            for _ in 0..ENCODES {
                let bytes = tile_encoder::tile(&state.db, state.symbology.clone(), t, false)
                    .await
                    .unwrap();
                views.push(canonical_tile(&decode_to_json(&bytes).unwrap()));
            }
            let unstable = unstable_features(&views);
            let actual = views.swap_remove(0);
            let expected = canonical_tile(&expected);
            if dump {
                std::fs::write(
                    dump_dir.join(format!("{label}.json")),
                    serde_json::to_string_pretty(&actual).unwrap(),
                )
                .unwrap();
            }
            let (mut errors, divergences) = tile_differences(&expected, &actual);
            errors.extend(
                unstable
                    .iter()
                    .map(|f| format!("{f}: not the same in {ENCODES} encodes")),
            );
            for (feature, d) in divergences {
                known
                    .entry(d)
                    .or_default()
                    .push(format!("{label} {feature}"));
            }
            failures.check(
                &label,
                if errors.is_empty() {
                    Ok(())
                } else {
                    Err(errors.join("\n    "))
                },
            );
        }
    }
    println!(
        "largest Simplification distance: {:.1} px",
        MAX_SIMPLIFICATION_PX.load(std::sync::atomic::Ordering::Relaxed) as f64 / 10.0
    );
    for (d, features) in &known {
        println!("known divergence {d:?}: {} features", features.len());
        for f in features {
            println!("    {f}");
        }
    }
    failures.assert_none(checked);
}

//! P-TE-5, the golden tiles (`testdata/geo/tiles/<z>_<x>_<y>.json`, Kotlin `GoldenTilesTest`):
//! every tile encoded by [`njord_server::tile_encoder`] over the seeded test database (the three
//! fixture cells and the 110m coastline), decoded with `njord_geo::mvt::view` and canonicalised as
//! `TileCanonical.kt` does (layers by name; features by LNAM, props, geometry; feature ids
//! dropped; props with sorted keys), plus the choices that carry no meaning in MVT: where a
//! closed ring or line starts, and the order of the parts of a multi-geometry.
//!
//! Every feature must then be identical, or differ in one of the listed [`Divergence`]s, each a
//! known `geo`-versus-GEOS/JTS behaviour inside `njord-geo`; each is printed with the tile and
//! feature (`cargo test -p njord-server --test tiles -- --nocapture`), and anything else fails.
//! Props, feature presence and layer sets are never relaxed. Each tile is encoded [`ENCODES`]
//! times and must come out the same every time. TE-7 `_info.json` the same way in WGS-84, with
//! coordinates to [`INFO_TOLERANCE`] and ring orientation normalised.
//!
//! `NJORD_TILE_DUMP=1` writes the canonical Rust views under the test's temp directory.

mod common;

use std::collections::BTreeMap;

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

fn sorted_keys(v: &Value) -> Value {
    match v {
        Value::Object(m) => {
            let mut keys: Vec<&String> = m.keys().collect();
            keys.sort();
            Value::Object(
                keys.into_iter()
                    .map(|k| (k.clone(), sorted_keys(&m[k])))
                    .collect(),
            )
        }
        Value::Array(a) => Value::Array(a.iter().map(sorted_keys).collect()),
        other => other.clone(),
    }
}

/// Rotates a closed path (first vertex repeated last) to start at its smallest vertex.
fn rotate_closed(path: &mut Vec<Value>) {
    let key = |v: &Value| (v[0].as_i64().unwrap(), v[1].as_i64().unwrap());
    if path.len() < 2 || path.first() != path.last() {
        return;
    }
    path.pop();
    let start = (0..path.len()).min_by_key(|&i| key(&path[i])).unwrap_or(0);
    path.rotate_left(start);
    if let Some(first) = path.first().cloned() {
        path.push(first);
    }
}

/// Twice the signed area of a ring in tile pixels; positive for an MVT exterior ring.
fn signed_area(ring: &[Value]) -> i64 {
    ring.windows(2)
        .map(|w| {
            w[0][0].as_i64().unwrap() * w[1][1].as_i64().unwrap()
                - w[1][0].as_i64().unwrap() * w[0][1].as_i64().unwrap()
        })
        .sum()
}

/// The geometry with the choices that carry no meaning in MVT made canonical: every closed
/// ring or closed line starts at its smallest vertex (GEOS and `geo` start clipped rings
/// elsewhere), the parts of a multi-line are sorted, and the polygons of a multi-polygon
/// (exterior ring plus its holes) are sorted, holes sorted within each. Winding and vertex
/// sequences are untouched.
fn canonical_geometry(kind: &Value, geometry: &Value) -> Value {
    let mut paths: Vec<Vec<Value>> = geometry
        .as_array()
        .unwrap()
        .iter()
        .map(|p| p.as_array().unwrap().clone())
        .collect();
    match kind.as_str() {
        Some("POLYGON") => {
            paths.iter_mut().for_each(rotate_closed);
            let mut polygons: Vec<Vec<Vec<Value>>> = Vec::new();
            for ring in paths {
                if signed_area(&ring) > 0 || polygons.is_empty() {
                    polygons.push(vec![ring]);
                } else {
                    polygons.last_mut().unwrap().push(ring);
                }
            }
            for p in &mut polygons {
                p[1..].sort_by_key(|r| Value::from(r.clone()).to_string());
            }
            polygons.sort_by_key(|p| Value::from(p[0].clone()).to_string());
            Value::from(
                polygons
                    .into_iter()
                    .flatten()
                    .map(Value::from)
                    .collect::<Vec<_>>(),
            )
        }
        Some("LINESTRING") => {
            paths.iter_mut().for_each(rotate_closed);
            paths.sort_by_key(|p| Value::from(p.clone()).to_string());
            Value::from(paths.into_iter().map(Value::from).collect::<Vec<_>>())
        }
        _ => geometry.clone(),
    }
}

/// `canonicalTile`, with [`canonical_geometry`].
fn canonical_tile(view: &Value) -> Value {
    let mut layers: Vec<Value> = view["layers"]
        .as_array()
        .unwrap()
        .iter()
        .map(|layer| {
            let mut features: Vec<Value> = layer["features"]
                .as_array()
                .unwrap()
                .iter()
                .map(|f| {
                    let geometry = canonical_geometry(&f["type"], &f["geometry"]);
                    json!({"type": f["type"], "props": sorted_keys(&f["props"]), "geometry": geometry})
                })
                .collect();
            features.sort_by_cached_key(|f| {
                (
                    f["props"].get("LNAM").map(Value::to_string).unwrap_or_default(),
                    f["props"].to_string(),
                    f["geometry"].to_string(),
                    f["type"].to_string(),
                )
            });
            json!({"name": layer["name"], "extent": layer["extent"], "features": features})
        })
        .collect();
    layers.sort_by_key(|l| l["name"].as_str().unwrap().to_owned());
    json!({ "layers": layers })
}

/// `canonicalInfo`.
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

/// A known way a Rust tile feature differs from the Kotlin one, from `geo` standing in for
/// GEOS/JTS inside `njord-geo` (see the module docs of `njord_server::tile_encoder`).
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
enum Divergence {
    /// The same parts, a few vertices apart, all within [`PIXEL_TOLERANCE`] of the other side.
    /// Not matchable without GEOS: the encoder simplifies with Douglas–Peucker, which keeps
    /// different vertices when it starts from a different ring vertex, and the ring starts come
    /// from the boolean ops and the repair (GEOS's overlay graph and `MakeValid` against `geo`'s
    /// `i_overlay` and triangulation repair, which share no rule for where a ring begins); GEOS
    /// also simplifies lines topology-preservingly. The rare vertex on the neighbouring pixel is
    /// `i_overlay`'s grid snapping against GEOS's floating-point intersections.
    Simplification,
}

/// How many times each tile is encoded; any feature that differs between encodes fails.
const ENCODES: usize = 3;

/// Pixels. The largest [`Divergence::Simplification`] seen on the fixtures is 0.1 px.
const PIXEL_TOLERANCE: f64 = 0.5;

/// The largest [`Divergence::Simplification`] distance seen, in tenths of a pixel.
static MAX_SIMPLIFICATION_PX: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

type Path = Vec<(i64, i64)>;

fn paths(geometry: &Value) -> Vec<Path> {
    geometry
        .as_array()
        .unwrap()
        .iter()
        .map(|p| {
            p.as_array()
                .unwrap()
                .iter()
                .map(|v| (v[0].as_i64().unwrap(), v[1].as_i64().unwrap()))
                .collect()
        })
        .collect()
}

/// Largest distance from a vertex of either path set to the nearest segment (or lone vertex) of
/// the other: how far apart the two drawings are, whatever vertices each keeps.
fn hausdorff(a: &[Path], b: &[Path]) -> f64 {
    fn to_segment(p: (i64, i64), s: ((i64, i64), (i64, i64))) -> f64 {
        let (px, py) = (p.0 as f64, p.1 as f64);
        let ((ax, ay), (bx, by)) = ((s.0.0 as f64, s.0.1 as f64), (s.1.0 as f64, s.1.1 as f64));
        let (dx, dy) = (bx - ax, by - ay);
        let len2 = dx * dx + dy * dy;
        let t = if len2 == 0.0 {
            0.0
        } else {
            (((px - ax) * dx + (py - ay) * dy) / len2).clamp(0.0, 1.0)
        };
        ((px - ax - t * dx).powi(2) + (py - ay - t * dy).powi(2)).sqrt()
    }
    fn segments(paths: &[Path]) -> Vec<((i64, i64), (i64, i64))> {
        paths
            .iter()
            .flat_map(|p| {
                if p.len() == 1 {
                    vec![(p[0], p[0])]
                } else {
                    p.windows(2).map(|w| (w[0], w[1])).collect()
                }
            })
            .collect()
    }
    let one_way = |x: &[Path], y: &[Path]| {
        let segs = segments(y);
        x.iter()
            .flatten()
            .map(|&p| {
                segs.iter()
                    .map(|&s| to_segment(p, s))
                    .fold(f64::INFINITY, f64::min)
            })
            .fold(0.0, f64::max)
    };
    if a.iter().all(Vec::is_empty) || b.iter().all(Vec::is_empty) {
        return f64::INFINITY;
    }
    one_way(a, b).max(one_way(b, a))
}

/// The divergences that explain `actual` against `expected` (same layer, same identity), or an
/// error when nothing known does.
fn classify(expected: &Value, actual: &Value) -> Result<Vec<Divergence>, String> {
    if expected == actual {
        return Ok(Vec::new());
    }
    if expected["props"] != actual["props"] || expected["type"] != actual["type"] {
        return Err(first_difference(expected, actual, "").unwrap_or_default());
    }
    let e = paths(&expected["geometry"]);
    let a = paths(&actual["geometry"]);
    let d = hausdorff(&e, &a);
    if e.len() != a.len() || d > PIXEL_TOLERANCE {
        return Err(format!(
            "{} parts expected, {} got, {d:.1} px apart",
            e.len(),
            a.len()
        ));
    }
    MAX_SIMPLIFICATION_PX.fetch_max((d * 10.0) as u64, std::sync::atomic::Ordering::Relaxed);
    Ok(vec![Divergence::Simplification])
}

/// Pairs the features of one layer by LNAM (or props when there is none), the closest
/// geometries first within a group, and classifies each pair.
fn tile_differences(expected: &Value, actual: &Value) -> (Vec<String>, Vec<(String, Divergence)>) {
    let by_name = |v: &Value| -> BTreeMap<String, Vec<Value>> {
        v["layers"]
            .as_array()
            .unwrap()
            .iter()
            .map(|l| {
                (
                    l["name"].as_str().unwrap().to_owned(),
                    l["features"].as_array().unwrap().clone(),
                )
            })
            .collect()
    };
    let identity = |f: &Value| {
        f["props"]
            .get("LNAM")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .unwrap_or_else(|| f["props"].to_string())
    };
    let (e, a) = (by_name(expected), by_name(actual));
    let mut errors = Vec::new();
    let mut known = Vec::new();
    for name in e
        .keys()
        .chain(a.keys())
        .collect::<std::collections::BTreeSet<_>>()
    {
        let empty = Vec::new();
        let (ef, af) = (e.get(name).unwrap_or(&empty), a.get(name).unwrap_or(&empty));
        let mut groups: BTreeMap<String, (Vec<&Value>, Vec<&Value>)> = BTreeMap::new();
        for f in ef {
            groups.entry(identity(f)).or_default().0.push(f);
        }
        for f in af {
            groups.entry(identity(f)).or_default().1.push(f);
        }
        for (id, (mut es, mut as_)) in groups {
            while let Some(x) = es.pop() {
                let best = (0..as_.len()).min_by(|&i, &j| {
                    let d =
                        |k: usize| hausdorff(&paths(&x["geometry"]), &paths(&as_[k]["geometry"]));
                    d(i).partial_cmp(&d(j)).unwrap()
                });
                let label = format!("layer {name} {}", id.chars().take(70).collect::<String>());
                match best {
                    Some(k) => {
                        let y = as_.remove(k);
                        match classify(x, y) {
                            Ok(ds) => known.extend(ds.into_iter().map(|d| (label.clone(), d))),
                            Err(msg) => errors.push(format!("{label}: {msg}")),
                        }
                    }
                    None => errors.push(format!("{label}: missing")),
                }
            }
            for y in as_ {
                errors.push(format!(
                    "layer {name} {}: unexpected {}",
                    id.chars().take(70).collect::<String>(),
                    y["type"]
                ));
            }
        }
    }
    (errors, known)
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

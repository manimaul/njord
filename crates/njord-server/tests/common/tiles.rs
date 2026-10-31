//! Decoded-tile canonicalisation and comparison shared by the P-TE-5 golden tile test
//! (tests/tiles.rs) and the region archive test (tests/export.rs): `TileCanonical.kt` plus the
//! choices that carry no meaning in MVT, and the one known [`Divergence`] of `geo` from GEOS.

#![allow(dead_code)]

use std::collections::BTreeMap;

use njord_testdata::first_difference;
use serde_json::{Value, json};

pub fn sorted_keys(v: &Value) -> Value {
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
pub fn canonical_geometry(kind: &Value, geometry: &Value) -> Value {
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
pub fn canonical_tile(view: &Value) -> Value {
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

/// A known way a Rust tile feature differs from the Kotlin one, from `geo` standing in for
/// GEOS/JTS inside `njord-geo` (see the module docs of `njord_server::tile_encoder`).
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum Divergence {
    /// The same parts, a few vertices apart, all within [`PIXEL_TOLERANCE`] of the other side.
    /// Not matchable without GEOS: the encoder simplifies with Douglas–Peucker, which keeps
    /// different vertices when it starts from a different ring vertex, and the ring starts come
    /// from the boolean ops and the repair (GEOS's overlay graph and `MakeValid` against `geo`'s
    /// `i_overlay` and triangulation repair, which share no rule for where a ring begins); GEOS
    /// also simplifies lines topology-preservingly. The rare vertex on the neighbouring pixel is
    /// `i_overlay`'s grid snapping against GEOS's floating-point intersections.
    Simplification,
}

/// Pixels. The largest [`Divergence::Simplification`] seen on the fixtures is 0.1 px.
pub const PIXEL_TOLERANCE: f64 = 0.5;

/// The largest [`Divergence::Simplification`] distance seen, in tenths of a pixel.
pub static MAX_SIMPLIFICATION_PX: std::sync::atomic::AtomicU64 =
    std::sync::atomic::AtomicU64::new(0);

pub type Path = Vec<(i64, i64)>;

pub fn paths(geometry: &Value) -> Vec<Path> {
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
pub fn hausdorff(a: &[Path], b: &[Path]) -> f64 {
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
pub fn classify(expected: &Value, actual: &Value) -> Result<Vec<Divergence>, String> {
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
pub fn tile_differences(
    expected: &Value,
    actual: &Value,
) -> (Vec<String>, Vec<(String, Divergence)>) {
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

/// Canonicalises both decoded views and compares them: `Ok` with the known divergences, `Err`
/// with every unexplained difference (a missing or unexpected feature, a prop, a geometry more
/// than [`PIXEL_TOLERANCE`] apart).
pub fn compare_tile(expected: &Value, actual: &Value) -> Result<Vec<(String, Divergence)>, String> {
    let (errors, known) = tile_differences(&canonical_tile(expected), &canonical_tile(actual));
    if errors.is_empty() {
        Ok(known)
    } else {
        Err(errors.join("\n    "))
    }
}

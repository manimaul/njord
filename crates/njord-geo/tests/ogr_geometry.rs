//! The golden fixture `geo/ogr_geometry.json` (best effort): geometry operations recorded over
//! GDAL/GEOS, re-run on [`njord_geo::geometry`] over the `geo` crate.
//!
//! Geometry results are compared as shapes, not WKT text: GDAL's WKT has 15 significant digits
//! and GEOS picks ring orientation, ring start vertex and part order freely, so both sides are
//! reduced to parts (points, lines, polygons with every ring oriented counter-clockwise and
//! rotated to its smallest vertex), sorted, with single- and multi-part types and all empty
//! geometries treated alike, and coordinates compared at 15 significant digits.
//!
//! The one tolerance is [`BOOLEAN_OP_TOLERANCE`] for `intersection` / `difference`: `geo`'s
//! `i_overlay` backend snaps vertices to an integer grid, so even vertices copied straight from
//! an input come back up to ~2e-12° off (the z16 tile against a chart coverage below).
//!
//! Ops not run, with the reason, are in [`SKIPPED_OPS`]; individual cases where `geo` and GEOS
//! legitimately differ are in [`DIVERGENT_CASES`]. Both are asserted, so a fix that makes a
//! divergent case pass, or a regression, fails the test.

use geo::{Area, Contains, Intersects, Simplify, Validation};
use geo_types::{Coord, Geometry, MultiPolygon};
use njord_geo::geometry;
use njord_testdata::{Failures, cases, first_difference, port_expected};
use serde_json::{Value, json};
use wkt::TryFromWkt;

/// Degrees: a fiftieth of a z24 tile pixel (360° / 2^24 / 4096 ≈ 5.2e-9°).
const BOOLEAN_OP_TOLERANCE: f64 = 1e-10;

/// Ops this test does not run, and why.
const SKIPPED_OPS: &[(&str, &str)] = &[
    (
        "numGeometries",
        "OGR handle accessor; geo-types exposes the structure directly and the tile path needs none of GDAL's counting conventions (0 for a LineString, rings for a Polygon)",
    ),
    (
        "numCoordinates",
        "OGR handle accessor (OGR_G_GetPointCount: 0 for anything but a point or line)",
    ),
    (
        "numInteriorRings",
        "OGR handle accessor; Polygon::interiors()",
    ),
    (
        "exteriorRing",
        "OGR handle accessor; Polygon::exterior() (GDAL's answer for a MultiPolygon is its first polygon)",
    ),
    ("interiorRing0", "OGR handle accessor; Polygon::interiors()"),
    (
        "coordinateSequence",
        "OGR handle accessor; the MVT encoder's dependence on it (a MultiPoint has no points) is pinned by the `geo/mvt` `multi` case instead",
    ),
    (
        "wkbHex",
        "WKB is njord-db's concern (geozero with-postgis-sqlx), not njord-geo's",
    ),
    (
        "wkbRoundTrip",
        "WKB is njord-db's concern (geozero with-postgis-sqlx), not njord-geo's",
    ),
    (
        "union",
        "not used by the tile path or the region export; geo only unions polygons, GEOS also mixed dimensions",
    ),
];

/// Cases (op, a, b) whose `geo` result legitimately differs from GEOS.
const DIVERGENT_CASES: &[(&str, &str, &str, &str)] = &[
    (
        "geoJson",
        "POLYGON EMPTY",
        "",
        "GDAL writes an empty polygon as {\"type\":\"Polygon\"} without coordinates; the geojson crate writes \"coordinates\": []",
    ),
    (
        "difference",
        "POLYGON ((0 0,10 0,10 10,0 10,0 0))",
        "LINESTRING (-5 5,15 5)",
        "same point set: GEOS nodes the polygon where the line crosses it (adds 0 5 and 10 5); a polygon minus a line is the polygon unchanged here",
    ),
];

fn parse(wkt: &str) -> Geometry {
    if wkt == "POINT EMPTY" {
        return Geometry::MultiPoint(geo_types::MultiPoint(vec![]));
    }
    Geometry::try_from_wkt_str(wkt).unwrap_or_else(|e| panic!("bad WKT {wkt}: {e:?}"))
}

fn type_name(g: &Geometry) -> &'static str {
    match g {
        Geometry::Point(_) => "Point",
        Geometry::Line(_) | Geometry::LineString(_) => "LineString",
        Geometry::Polygon(_) | Geometry::Rect(_) | Geometry::Triangle(_) => "Polygon",
        Geometry::MultiPoint(_) => "MultiPoint",
        Geometry::MultiLineString(_) => "MultiLineString",
        Geometry::MultiPolygon(_) => "MultiPolygon",
        Geometry::GeometryCollection(_) => "GeometryCollection",
    }
}

/// A geometry result: compared as a shape (see the module docs), `None` as JSON `null`.
enum Out {
    Json(Value),
    Shape(Option<Geometry>),
}

fn compute(op: &str, a: &Geometry, b: Option<&Geometry>, n: Option<f64>) -> Out {
    let mask = || -> MultiPolygon {
        match b.unwrap() {
            Geometry::Polygon(p) => MultiPolygon(vec![p.clone()]),
            Geometry::MultiPolygon(mp) => mp.clone(),
            other => panic!("mask is not polygonal: {other:?}"),
        }
    };
    let polygonal = |g: &Geometry| matches!(g, Geometry::Polygon(_) | Geometry::MultiPolygon(_));
    match op {
        "type" => Out::Json(json!(type_name(a))),
        "isValid" => Out::Json(json!(a.is_valid())),
        "isEmpty" => Out::Json(json!(geometry::is_empty(a))),
        "area" => Out::Json(json!(geometry::area(a))),
        "length" => Out::Json(json!(geometry::length(a))),
        "envelope" => {
            let (min, max) = geometry::envelope(a)
                .map(|r| (r.min(), r.max()))
                .unwrap_or_default();
            Out::Json(json!([min.x, min.y, max.x, max.y]))
        }
        "geoJson" => {
            let value = geojson::GeometryValue::from(a);
            Out::Json(serde_json::to_value(geojson::Geometry::new(value)).unwrap())
        }
        "clone" => Out::Shape(Some(a.clone())),
        "boundary" => Out::Shape(geometry::boundary(a)),
        "centroid" => Out::Shape(geometry::centroid(a).map(Geometry::Point)),
        "centroidOfLargestPart" => {
            Out::Shape(geometry::centroid_of_largest_part(a).map(Geometry::Point))
        }
        "makeValid" => Out::Shape(geometry::make_valid(a)),
        "envelopeGeometry" => Out::Shape(Some(Geometry::Polygon(geometry::envelope_polygon(
            a,
            n.unwrap_or(0.0),
        )))),
        "simplify" | "simplifyPreserveTopology" => {
            let n = n.unwrap();
            Out::Shape(Some(match a {
                Geometry::LineString(ls) => Geometry::LineString(ls.simplify(n)),
                Geometry::Polygon(p) => Geometry::Polygon(p.simplify(n)),
                other => panic!("no simplify case for {other:?}"),
            }))
        }
        "intersection" if polygonal(b.unwrap()) => {
            Out::Shape(Some(geometry::intersection(a, &mask())))
        }
        // GEOS intersection is symmetric; njord-geo only clips by a polygonal mask.
        "intersection" if polygonal(a) => {
            let a_mask = match a {
                Geometry::Polygon(p) => MultiPolygon(vec![p.clone()]),
                Geometry::MultiPolygon(mp) => mp.clone(),
                _ => unreachable!(),
            };
            Out::Shape(Some(geometry::intersection(b.unwrap(), &a_mask)))
        }
        "difference" => {
            let a = match a {
                Geometry::Polygon(p) => MultiPolygon(vec![p.clone()]),
                other => panic!("difference of {other:?}"),
            };
            let b = b.unwrap();
            if polygonal(b) {
                Out::Shape(geometry::difference(&a, &mask()).map(Geometry::MultiPolygon))
            } else {
                // a polygon minus something of lower dimension is the polygon
                Out::Shape(Some(Geometry::MultiPolygon(a)))
            }
        }
        "contains" | "preparedContains" => {
            let b = b.unwrap();
            Out::Json(json!(!geometry::is_empty(b) && a.contains(b)))
        }
        "intersects" | "preparedIntersects" => Out::Json(json!(a.intersects(b.unwrap()))),
        op => panic!("no computation for op {op}"),
    }
}

#[derive(Debug, PartialEq, PartialOrd)]
enum Part {
    Point(Xy),
    Line(Vec<Xy>),
    Polygon(Vec<Vec<Xy>>),
}

type Xy = (f64, f64);

fn xy(cs: &[Coord]) -> Vec<Xy> {
    cs.iter().map(|c| (c.x, c.y)).collect()
}

/// Counter-clockwise, starting at the smallest vertex, without the closing vertex.
fn canonical_ring(ring: &geo_types::LineString) -> Vec<Xy> {
    let mut cs = xy(&ring.0);
    if cs.len() > 1 && cs.first() == cs.last() {
        cs.pop();
    }
    if geo_types::Polygon::new(ring.clone(), vec![]).signed_area() < 0.0 {
        cs.reverse();
    }
    if let Some(start) = (0..cs.len()).min_by(|&i, &j| cs[i].partial_cmp(&cs[j]).unwrap()) {
        cs.rotate_left(start);
    }
    cs
}

fn parts(g: &Geometry, out: &mut Vec<Part>) {
    let polygon = |p: &geo_types::Polygon| {
        let mut holes: Vec<Vec<Xy>> = p.interiors().iter().map(canonical_ring).collect();
        holes.sort_by(|a, b| a.partial_cmp(b).unwrap());
        let mut rings = vec![canonical_ring(p.exterior())];
        rings.extend(holes);
        Part::Polygon(rings)
    };
    match g {
        Geometry::Point(p) if !p.x().is_nan() => out.push(Part::Point(p.x_y())),
        Geometry::Point(_) => {}
        Geometry::MultiPoint(mp) => out.extend(mp.iter().map(|p| Part::Point(p.x_y()))),
        Geometry::Line(l) => out.push(Part::Line(xy(&[l.start, l.end]))),
        Geometry::LineString(ls) if !ls.0.is_empty() => out.push(Part::Line(xy(&ls.0))),
        Geometry::LineString(_) => {}
        Geometry::MultiLineString(mls) => mls
            .iter()
            .for_each(|ls| parts(&Geometry::LineString(ls.clone()), out)),
        Geometry::Polygon(p) if !p.exterior().0.is_empty() => out.push(polygon(p)),
        Geometry::Polygon(_) => {}
        Geometry::MultiPolygon(mp) => mp
            .iter()
            .for_each(|p| parts(&Geometry::Polygon(p.clone()), out)),
        Geometry::Rect(r) => out.push(polygon(&r.to_polygon())),
        Geometry::Triangle(t) => out.push(polygon(&t.to_polygon())),
        Geometry::GeometryCollection(gc) => gc.iter().for_each(|g| parts(g, out)),
    }
}

fn shape(g: &Geometry) -> Vec<Part> {
    let mut out = Vec::new();
    parts(g, &mut out);
    out.sort_by(|a, b| a.partial_cmp(b).unwrap());
    out
}

fn same_at_wkt_precision(ours: f64, theirs: f64) -> bool {
    ours == theirs || format!("{ours:.14e}") == format!("{theirs:.14e}")
}

fn same_coords(a: &[Xy], b: &[Xy], tolerance: f64) -> bool {
    let same = |a: f64, b: f64| same_at_wkt_precision(a, b) || (a - b).abs() <= tolerance;
    a.len() == b.len()
        && a.iter()
            .zip(b)
            .all(|(a, b)| same(a.0, b.0) && same(a.1, b.1))
}

fn same_shape(ours: &[Part], theirs: &[Part], tolerance: f64) -> bool {
    ours.len() == theirs.len()
        && ours.iter().zip(theirs).all(|pair| match pair {
            (Part::Point(a), Part::Point(b)) => same_coords(&[*a], &[*b], tolerance),
            (Part::Line(a), Part::Line(b)) => same_coords(a, b, tolerance),
            (Part::Polygon(a), Part::Polygon(b)) => {
                a.len() == b.len() && a.iter().zip(b).all(|(a, b)| same_coords(a, b, tolerance))
            }
            _ => false,
        })
}

fn check(out: Out, expected: &Value, tolerance: f64) -> Result<(), String> {
    match out {
        Out::Json(actual) => first_difference(expected, &actual, "").map_or(Ok(()), Err),
        Out::Shape(None) if expected.is_null() => Ok(()),
        Out::Shape(None) => Err(format!("None, GEOS gave {expected}")),
        Out::Shape(Some(g)) => {
            let Some(text) = expected.as_str() else {
                return Err(format!("{g:?}, GEOS gave {expected}"));
            };
            let theirs = shape(&parse(text));
            let ours = shape(&g);
            if same_shape(&ours, &theirs, tolerance) {
                Ok(())
            } else {
                Err(format!("{ours:?} != {text}"))
            }
        }
    }
}

/// LB-1: every case of every op not in [`SKIPPED_OPS`] matches GEOS, except the documented
/// [`DIVERGENT_CASES`], which must still differ.
#[test]
fn ogr_geometry() {
    let mut failures = Failures::default();
    let cases = cases("geo/ogr_geometry.json");
    let (mut checked, mut skipped, mut divergent) = (0, 0, 0);
    for case in &cases {
        let input = &case["input"];
        let op = input["op"].as_str().unwrap();
        if SKIPPED_OPS.iter().any(|(o, _)| *o == op) {
            skipped += 1;
            continue;
        }
        checked += 1;
        let a_text = input["a"].as_str().unwrap();
        let b_text = input["b"].as_str().unwrap_or("");
        let a = parse(a_text);
        let b = input["b"].as_str().map(parse);
        let tolerance = match op {
            "intersection" | "difference" => BOOLEAN_OP_TOLERANCE,
            _ => 0.0,
        };
        let out = compute(op, &a, b.as_ref(), input["n"].as_f64());
        let result = check(out, port_expected(case), tolerance);
        let is_divergent = DIVERGENT_CASES
            .iter()
            .any(|(o, da, db, _)| *o == op && *da == a_text && *db == b_text);
        if is_divergent {
            divergent += 1;
            if result.is_ok() {
                failures.push(format!("{input}: listed as divergent but matches GEOS now"));
            }
        } else {
            failures.check(input, result);
        }
    }
    assert_eq!(skipped + checked, cases.len());
    assert_eq!(
        divergent,
        DIVERGENT_CASES.len(),
        "every divergent case is in the fixture"
    );
    failures.assert_none(checked);
}

/// The anchors the tile path relies on.
#[test]
fn tile_path_anchors() {
    let square = parse("POLYGON ((0 0,10 0,10 10,0 10,0 0))");
    let bow_tie = parse("POLYGON ((0 0,10 10,10 0,0 10,0 0))");
    let disjoint = parse("POLYGON ((20 20,30 20,30 30,20 30,20 20))");
    let mp = |g: &Geometry| match g {
        Geometry::Polygon(p) => MultiPolygon(vec![p.clone()]),
        _ => unreachable!(),
    };
    assert!(!bow_tie.is_valid());
    let repaired = geometry::make_valid(&bow_tie).unwrap();
    assert!(repaired.is_valid());
    assert_eq!(geometry::area(&repaired), 50.0);
    assert_eq!(geometry::difference(&mp(&square), &mp(&bow_tie)), None);
    let d = geometry::difference(&mp(&square), &mp(&disjoint)).unwrap();
    assert!(d.is_valid() && !d.0.is_empty());
    assert!(geometry::is_empty(&geometry::intersection(
        &square,
        &mp(&disjoint)
    )));
}

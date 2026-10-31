//! The tile-path behaviours where `njord-geo` follows GEOS rather than `geo`, each pinned on a
//! small input; the full evidence is P-TE-5 (`njord-server/tests/tiles.rs`). The LAKARE input is
//! the pixel-space geometry the Rust tile path hands the encoder for P-TE-5 LAKARE
//! `02260A82E9560782` on 14/2616/5742 (`fixtures/lakare_02260A82E9560782_14_2616_5742.wkt`).

use geo_types::{Geometry, LineString, MultiLineString, MultiPolygon, Point, Polygon};
use njord_geo::VectorTileEncoder;
use njord_geo::geometry::intersection;
use njord_geo::mvt::view::decode_to_json;
use njord_model::JsonObject;
use serde_json::{Value, json};
use wkt::TryFromWkt;

fn lakare() -> Geometry {
    let path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/tests/fixtures/lakare_02260A82E9560782_14_2616_5742.wkt"
    );
    Geometry::try_from_wkt_str(std::fs::read_to_string(path).unwrap().trim()).unwrap()
}

fn encode(geometry: &Geometry) -> Vec<u8> {
    let mut encoder = VectorTileEncoder::new();
    encoder.add_feature("LAKARE", &JsonObject::new(), geometry);
    encoder.encode()
}

fn square(lo: f64, hi: f64) -> MultiPolygon {
    MultiPolygon(vec![Polygon::new(
        LineString::from(vec![(lo, lo), (hi, lo), (hi, hi), (lo, hi), (lo, lo)]),
        vec![],
    )])
}

fn coords(geometry: &Geometry) -> Vec<Vec<(f64, f64)>> {
    let line = |l: &LineString| l.0.iter().map(|c| (c.x, c.y)).collect();
    match geometry {
        Geometry::LineString(l) => vec![line(l)],
        Geometry::MultiLineString(m) => m.iter().map(line).collect(),
        other => panic!("not a line: {other:?}"),
    }
}

/// The input is a bow-tie with a sub-pixel lobe, which `geo`'s repair traces from a randomly
/// seeded `HashSet`: the encoder must still write the same bytes every time.
#[test]
fn repaired_polygon_encodes_deterministically() {
    let geometry = lakare();
    let first = encode(&geometry);
    for _ in 0..100 {
        assert_eq!(encode(&geometry), first);
    }
}

/// The Kotlin tile has one ring; the lobe, which rounds to two pixels, is not written.
#[test]
fn degenerate_ring_is_dropped() {
    let view = decode_to_json(&encode(&lakare())).unwrap();
    let feature = &view["layers"][0]["features"][0];
    let rings = feature["geometry"].as_array().unwrap();
    assert_eq!(rings.len(), 1, "{feature}");
    let kotlin = json!([
        [2927, 2497],
        [2934, 2498],
        [2954, 2502],
        [2968, 2508],
        [2978, 2515],
        [3112, 2654],
        [3098, 2666],
        [3087, 2661],
        [3077, 2645],
        [3047, 2612],
        [3034, 2605],
        [3019, 2589],
        [2972, 2542],
        [2942, 2514],
        [2927, 2497]
    ]);
    let mut ring: Vec<Value> = rings[0].as_array().unwrap().clone();
    ring.pop();
    let start = ring.iter().position(|v| *v == kotlin[0]).unwrap();
    ring.rotate_left(start);
    ring.push(ring[0].clone());
    assert_eq!(Value::from(ring), kotlin);
}

fn line(coords: &[(f64, f64)]) -> Geometry {
    Geometry::LineString(LineString::from(coords.to_vec()))
}

/// A closed line cut by the mask runs between the mask boundary and its own start vertex
/// (P-TE-5 PLY on 14/2619/5741, coastline on 1/0/0), not from `geo`'s smallest vertex.
#[test]
fn closed_line_is_split_at_its_start_vertex_only() {
    let mask = square(0.0, 10.0);
    let start_inside = line(&[
        (5.0, 5.0),
        (15.0, 5.0),
        (15.0, 7.0),
        (5.0, 7.0),
        (4.0, 6.0),
        (5.0, 5.0),
    ]);
    assert_eq!(
        coords(&intersection(&start_inside, &mask)),
        vec![
            vec![(10.0, 7.0), (5.0, 7.0), (4.0, 6.0), (5.0, 5.0)],
            vec![(5.0, 5.0), (10.0, 5.0)],
        ]
    );
    let start_outside = line(&[
        (15.0, 5.0),
        (15.0, 7.0),
        (5.0, 7.0),
        (4.0, 6.0),
        (5.0, 5.0),
        (15.0, 5.0),
    ]);
    assert_eq!(
        coords(&intersection(&start_outside, &mask)),
        vec![vec![
            (10.0, 7.0),
            (5.0, 7.0),
            (4.0, 6.0),
            (5.0, 5.0),
            (10.0, 5.0)
        ]]
    );
}

/// A closed line wholly inside the mask keeps its start vertex.
#[test]
fn closed_line_inside_keeps_its_start() {
    let ring = line(&[(3.0, 1.0), (3.0, 3.0), (1.0, 1.0), (3.0, 1.0)]);
    assert_eq!(intersection(&ring, &square(0.0, 10.0)), ring);
}

/// Separate input lines are not merged where they meet.
#[test]
fn lines_are_not_merged_across_parts() {
    let lines = Geometry::MultiLineString(MultiLineString(vec![
        LineString::from(vec![(6.0, 6.0), (7.0, 7.0)]),
        LineString::from(vec![(7.0, 7.0), (8.0, 6.0)]),
    ]));
    assert_eq!(intersection(&lines, &square(0.0, 10.0)), lines);
}

/// A line that only touches the mask is the touching point (P-TE-5 SLCONS `022600D606360032`
/// on 14/2619/5744 is drawn as a POINT).
#[test]
fn line_touching_the_mask_is_a_point() {
    let touching = line(&[(12.0, 3.0), (10.0, 5.0), (12.0, 7.0)]);
    assert_eq!(
        intersection(&touching, &square(0.0, 10.0)),
        Geometry::Point(Point::new(10.0, 5.0))
    );
}

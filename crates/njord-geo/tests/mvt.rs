//! The golden fixtures `geo/mvt/<case>.bin` + `.json`: [`VectorTileEncoder`] fed the inputs the
//! fixtures were generated from, whose geometries are already in tile pixels.

use geo_types::{
    Geometry, GeometryCollection, LineString, MultiLineString, MultiPoint, MultiPolygon, Point,
    Polygon,
};
use njord_geo::VectorTileEncoder;
use njord_geo::mvt::view::decode_to_json;
use njord_model::JsonObject;
use njord_testdata::{Failures, first_difference, testdata};
use serde_json::{Value, json};

fn ring(coords: &[(f64, f64)]) -> LineString {
    LineString::from(coords.to_vec())
}

fn polygon(coords: &[(f64, f64)]) -> Polygon {
    Polygon::new(ring(coords), vec![])
}

fn holed(shell: &[(f64, f64)], hole: &[(f64, f64)]) -> Polygon {
    Polygon::new(ring(shell), vec![ring(hole)])
}

fn props(value: Value) -> JsonObject {
    match value {
        Value::Object(map) => map,
        _ => unreachable!(),
    }
}

fn point(x: f64, y: f64) -> Geometry {
    Geometry::Point(Point::new(x, y))
}

fn line(coords: &[(f64, f64)]) -> Geometry {
    Geometry::LineString(ring(coords))
}

/// One encoder per fixture case, built as `MvtFixtureTest` builds it.
fn build(case: &str) -> VectorTileEncoder {
    let mut e = VectorTileEncoder::new();
    match case {
        "empty" => {}
        "point_props" => e.add_feature(
            "BOYLAT",
            &props(json!({
                "str": "red can", "empty": "", "zero": "0", "int": 7, "negative": -42,
                "long": 9_007_199_254_740_993_i64, "double": 12.3, "negativeDouble": -0.6,
                "bool": true, "list": ["2", "4"], "LNAM": "0226F7A5C0ED2C4E",
            })),
            &point(100.0, 200.0),
        ),
        "lines" => {
            e.add_feature(
                "DEPCNT",
                &props(json!({"VALDCO": 10.0})),
                &line(&[(0.0, 0.0), (4096.0, 4096.0)]),
            );
            e.add_feature(
                "DEPCNT",
                &props(json!({"VALDCO": 2.0})),
                &line(&[
                    (10.0, 10.0),
                    (10.0, 10.0),
                    (20.0, 10.0),
                    (20.0, 10.0),
                    (20.0, 30.0),
                ]),
            );
            e.add_feature(
                "COALNE",
                &JsonObject::new(),
                &Geometry::MultiLineString(MultiLineString(vec![
                    ring(&[(100.0, 100.0), (200.0, 100.0)]),
                    ring(&[(300.0, 300.0), (300.0, 400.0), (350.0, 450.0)]),
                ])),
            );
        }
        "polygons" => {
            e.add_feature(
                "DEPARE",
                &props(json!({"DRVAL1": 0.0, "DRVAL2": 5.0})),
                &Geometry::Polygon(polygon(&[
                    (100.0, 100.0),
                    (100.0, 500.0),
                    (500.0, 500.0),
                    (500.0, 100.0),
                    (100.0, 100.0),
                ])),
            );
            e.add_feature(
                "DEPARE",
                &props(json!({"DRVAL1": 5.0, "DRVAL2": 10.0})),
                &Geometry::Polygon(polygon(&[
                    (600.0, 100.0),
                    (1000.0, 100.0),
                    (1000.0, 500.0),
                    (600.0, 500.0),
                    (600.0, 100.0),
                ])),
            );
            e.add_feature(
                "LNDARE",
                &props(json!({"OBJNAM": "Island"})),
                &Geometry::Polygon(holed(
                    &[
                        (1200.0, 1200.0),
                        (2000.0, 1200.0),
                        (2000.0, 2000.0),
                        (1200.0, 2000.0),
                        (1200.0, 1200.0),
                    ],
                    &[
                        (1400.0, 1400.0),
                        (1800.0, 1400.0),
                        (1800.0, 1800.0),
                        (1400.0, 1800.0),
                        (1400.0, 1400.0),
                    ],
                )),
            );
            e.add_feature(
                "LNDARE",
                &JsonObject::new(),
                &Geometry::MultiPolygon(MultiPolygon(vec![
                    polygon(&[
                        (2500.0, 2500.0),
                        (2500.0, 2600.0),
                        (2600.0, 2600.0),
                        (2600.0, 2500.0),
                        (2500.0, 2500.0),
                    ]),
                    holed(
                        &[
                            (3000.0, 3000.0),
                            (3500.0, 3000.0),
                            (3500.0, 3500.0),
                            (3000.0, 3500.0),
                            (3000.0, 3000.0),
                        ],
                        &[
                            (3100.0, 3100.0),
                            (3100.0, 3200.0),
                            (3200.0, 3200.0),
                            (3200.0, 3100.0),
                            (3100.0, 3100.0),
                        ],
                    ),
                ])),
            );
        }
        "multi" => {
            e.add_feature(
                "SOUNDG",
                &props(json!({"METERS": 1.2})),
                &Geometry::MultiPoint(MultiPoint::from(vec![
                    (10.0, 10.0),
                    (20.0, 20.0),
                    (30.0, 10.0),
                ])),
            );
            e.add_feature(
                "MIXED",
                &props(json!({"K": "v"})),
                &Geometry::GeometryCollection(GeometryCollection(vec![
                    point(500.0, 500.0),
                    line(&[(500.0, 500.0), (600.0, 700.0)]),
                ])),
            );
        }
        "clipping" => {
            for (n, x, y) in [
                (1, 4100.0, 100.0),
                (2, 4105.0, 100.0),
                (3, -8.0, -8.0),
                (4, -10.0, 0.0),
            ] {
                e.add_feature("BOYLAT", &props(json!({"N": n})), &point(x, y));
            }
            e.add_feature(
                "DEPARE",
                &props(json!({"N": 5})),
                &Geometry::Polygon(polygon(&[
                    (3000.0, 3000.0),
                    (5000.0, 3000.0),
                    (5000.0, 5000.0),
                    (3000.0, 5000.0),
                    (3000.0, 3000.0),
                ])),
            );
            e.add_feature(
                "DEPCNT",
                &props(json!({"N": 6})),
                &line(&[(2000.0, 2000.0), (6000.0, 2000.0)]),
            );
            e.add_feature(
                "DEPARE",
                &props(json!({"N": 7})),
                &Geometry::Polygon(polygon(&[
                    (5000.0, 5000.0),
                    (6000.0, 5000.0),
                    (6000.0, 6000.0),
                    (5000.0, 6000.0),
                    (5000.0, 5000.0),
                ])),
            );
        }
        "tiny_and_simplified" => {
            e.add_feature(
                "TINY",
                &props(json!({"N": 1})),
                &Geometry::Polygon(polygon(&[
                    (10.0, 10.0),
                    (10.5, 10.0),
                    (10.5, 10.5),
                    (10.0, 10.5),
                    (10.0, 10.0),
                ])),
            );
            e.add_feature(
                "TINY",
                &props(json!({"N": 2})),
                &line(&[(10.0, 10.0), (10.5, 10.0)]),
            );
            e.add_feature(
                "TINY",
                &props(json!({"N": 3})),
                &line(&[(10.0, 10.0), (11.5, 10.0)]),
            );
            e.add_feature(
                "TINY",
                &props(json!({"N": 4})),
                &line(&[(100.0, 100.0), (150.0, 100.05), (200.0, 100.0)]),
            );
            e.add_feature(
                "TINY",
                &props(json!({"N": 5})),
                &Geometry::Polygon(polygon(&[
                    (300.0, 300.0),
                    (300.3, 400.0),
                    (400.0, 400.3),
                    (400.0, 300.0),
                    (300.0, 300.0),
                ])),
            );
        }
        "shared_keys_values" => {
            e.add_feature(
                "BOYLAT",
                &props(json!({"CATLAM": 1, "COLOUR": "3"})),
                &point(100.0, 100.0),
            );
            e.add_feature(
                "BOYLAT",
                &props(json!({"CATLAM": 2, "COLOUR": "4"})),
                &point(200.0, 100.0),
            );
            e.add_feature(
                "BOYLAT",
                &props(json!({"CATLAM": 1, "COLOUR": "3", "OBJNAM": "A"})),
                &point(300.0, 100.0),
            );
            e.add_feature(
                "BOYLAT",
                &props(json!({"CATLAM": "1", "COLOUR": "4"})),
                &point(400.0, 100.0),
            );
        }
        "two_layers" => {
            e.add_feature(
                "LNDARE",
                &JsonObject::new(),
                &Geometry::Polygon(polygon(&[
                    (0.0, 0.0),
                    (0.0, 100.0),
                    (100.0, 100.0),
                    (100.0, 0.0),
                    (0.0, 0.0),
                ])),
            );
            e.add_feature("SOUNDG", &props(json!({"METERS": 3.4})), &point(50.0, 50.0));
            e.add_feature(
                "LNDARE",
                &JsonObject::new(),
                &Geometry::Polygon(polygon(&[
                    (200.0, 200.0),
                    (200.0, 300.0),
                    (300.0, 300.0),
                    (300.0, 200.0),
                    (200.0, 200.0),
                ])),
            );
        }
        other => panic!("no inputs for fixture case {other}"),
    }
    e
}

const CASES: &[&str] = &[
    "clipping",
    "empty",
    "lines",
    "multi",
    "point_props",
    "polygons",
    "shared_keys_values",
    "tiny_and_simplified",
    "two_layers",
];

/// Cases whose bytes depend on where GEOS starts a clipped ring, which `geo` chooses
/// differently; their decoded view still matches once rings are rotated.
const GEOS_RING_START: &[&str] = &["clipping"];

struct Fixture {
    bytes: Vec<u8>,
    /// The view of the golden `.bin`.
    decoded: Value,
    /// `port.decoded`: the view the encoder must produce where the golden `.bin` records an
    /// accepted defect.
    port_decoded: Option<Value>,
}

impl Fixture {
    /// What the encoder's tile must decode to: `port.decoded` when present, else `decoded`.
    fn port_expected(&self) -> &Value {
        self.port_decoded.as_ref().unwrap_or(&self.decoded)
    }
}

fn fixture(case: &str) -> Fixture {
    let dir = testdata().join("geo/mvt");
    let sidecar = njord_testdata::load(&dir.join(format!("{case}.json")));
    let bin = sidecar["bin"].as_str().unwrap();
    let bytes = std::fs::read(dir.join(bin)).unwrap();
    assert_eq!(
        Some(bytes.len() as u64),
        sidecar["bytes"].as_u64(),
        "{case}: byte count"
    );
    Fixture {
        bytes,
        decoded: sidecar["decoded"].clone(),
        port_decoded: sidecar.get("port").map(|p| p["decoded"].clone()),
    }
}

/// Rotates every closed ring of every POLYGON feature to start at its smallest vertex. Where a
/// ring starts carries no meaning in MVT, and GEOS and `geo` pick different starts for a clipped
/// polygon; the winding and the vertex sequence are still compared.
fn normalise_ring_starts(view: &mut Value) {
    for layer in view["layers"].as_array_mut().unwrap() {
        for feature in layer["features"].as_array_mut().unwrap() {
            if feature["type"] != "POLYGON" {
                continue;
            }
            for path in feature["geometry"].as_array_mut().unwrap() {
                let ring = path.as_array_mut().unwrap();
                ring.pop();
                let key = |v: &Value| (v[0].as_i64().unwrap(), v[1].as_i64().unwrap());
                let start = (0..ring.len()).min_by_key(|&i| key(&ring[i])).unwrap_or(0);
                ring.rotate_left(start);
                if let Some(first) = ring.first().cloned() {
                    ring.push(first);
                }
            }
        }
    }
}

/// The encoded tile decodes to the sidecar's `decoded` view, or its `port.decoded` where one is
/// recorded (the MultiPoint in `multi`). This is the gate.
#[test]
fn mvt_decoded_view() {
    let mut failures = Failures::default();
    for case in CASES {
        let mut expected = fixture(case).port_expected().clone();
        let Some(mut actual) = failures.check(
            case,
            decode_to_json(&build(case).encode()).map_err(|e| e.to_string()),
        ) else {
            continue;
        };
        normalise_ring_starts(&mut expected);
        normalise_ring_starts(&mut actual);
        if let Some(d) = first_difference(&expected, &actual, "") {
            failures.push(format!("{case}: {d}\n  ours: {actual}"));
        }
    }
    failures.assert_none(CASES.len());
}

/// A MultiPoint whose points round to the same pixel writes each pixel once, and the MoveTo count
/// matches the points written (a count that claimed the dropped point would make the tile
/// undecodable). No golden fixture covers this: none holds an encoded MultiPoint.
#[test]
fn multi_point_drops_repeated_pixels() {
    let mut e = VectorTileEncoder::new();
    e.add_feature(
        "SOUNDG",
        &props(json!({"METERS": 3.4})),
        &Geometry::MultiPoint(MultiPoint::from(vec![
            (10.0, 10.0),
            (10.2, 9.9),
            (20.0, 20.0),
        ])),
    );
    let view = decode_to_json(&e.encode()).unwrap();
    assert_eq!(
        view["layers"][0]["features"][0]["geometry"],
        json!([[[10, 10]], [[20, 20]]])
    );
}

/// The decoder reads the golden `.bin` into exactly the recorded `decoded` view, ring starts
/// included. This is the decoder the golden tiles in `njord-server/tests/tiles.rs` are compared
/// with.
#[test]
fn mvt_decoder_reads_golden_tiles() {
    let mut failures = Failures::default();
    for case in CASES {
        let f = fixture(case);
        let result = decode_to_json(&f.bytes)
            .map_err(|e| e.to_string())
            .and_then(|actual| first_difference(&f.decoded, &actual, "").map_or(Ok(()), Err));
        failures.check(case, result);
    }
    failures.assert_none(CASES.len());
}

/// Informational per testdata/README.md but checked anyway: the protobuf writer uses the golden
/// tiles' field order, so wherever `geo` and GEOS produce the same vertices the bytes are
/// identical to the golden `.bin`. Cases with a `port.decoded` are left out: their golden bytes
/// are the defect.
#[test]
fn mvt_bytes_match_golden() {
    let mut failures = Failures::default();
    let mut checked = 0;
    for case in CASES.iter().filter(|c| !GEOS_RING_START.contains(c)) {
        let f = fixture(case);
        if f.port_decoded.is_some() {
            continue;
        }
        checked += 1;
        if build(case).encode() != f.bytes {
            failures.push(format!("{case}: bytes differ from {case}.bin"));
        }
    }
    failures.assert_none(checked);
}

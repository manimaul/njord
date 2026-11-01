//! The Web Mercator arithmetic of [`njord_geo::TileSystem`] against the golden fixture
//! `geo/tile_system.json`.

use njord_geo::{TILE_SYSTEM, TWEAK_ZOOM_TO_SCALE_BY};
use njord_testdata::{Failures, cases, first_difference, port_expected};
use serde_json::{Value, json};

/// The fixture was recorded with `mapSize = 4096 << z` in a 32-bit `Int`, which overflows from z19
/// (`2^12 << 19 == 2^31`), so every op that goes through it pins garbage at z ≥ 19. The crate
/// computes it in 64 bits; those cases carry no `port.expected`, so they are checked for sanity
/// instead of equality.
const GOLDEN_OVERFLOWS_FROM_Z: u64 = 19;

fn f(input: &Value, key: &str) -> f64 {
    input[key].as_f64().unwrap()
}

fn i(input: &Value, key: &str) -> i64 {
    input[key].as_i64().unwrap()
}

fn z(input: &Value) -> u32 {
    input["z"].as_u64().unwrap() as u32
}

/// `POLYGON ((x y,x y,…))` as GDAL writes it, including `-inf`.
fn parse_polygon_wkt(wkt: &str) -> Vec<(f64, f64)> {
    let body = wkt
        .strip_prefix("POLYGON ((")
        .and_then(|s| s.strip_suffix("))"))
        .unwrap_or_else(|| panic!("not a single-ring polygon: {wkt}"));
    body.split(',')
        .map(|pair| {
            let mut xy = pair.split_whitespace().map(|v| v.parse::<f64>().unwrap());
            (xy.next().unwrap(), xy.next().unwrap())
        })
        .collect()
}

/// GDAL writes WKT coordinates with 15 significant digits (`%.15g`), so the clip polygons are
/// compared at that precision: both values rounded to 15 significant digits must agree. The
/// unrounded corners are checked exactly by [`clip_polygon_corners_are_exact`].
fn same_at_wkt_precision(ours: f64, theirs: f64) -> bool {
    ours == theirs || format!("{ours:.14e}") == format!("{theirs:.14e}")
}

fn compute(input: &Value) -> Value {
    let ts = TILE_SYSTEM;
    let xy = |c: geo_types::Coord| json!([c.x, c.y]);
    match input["op"].as_str().unwrap() {
        "mapSize" => json!(ts.map_size(z(input))),
        "latLngToTileXy" => xy(ts.lat_lng_to_tile_xy(f(input, "lng"), f(input, "lat"), z(input))),
        "latLngToTileBoundedXy" => xy(ts.lat_lng_to_tile_bounded_xy(
            f(input, "lng"),
            f(input, "lat"),
            i(input, "x"),
            i(input, "y"),
            z(input),
        )),
        "scaleToZoom" => json!(ts.scale_to_zoom(f(input, "scale"), f(input, "lat"))),
        "zoomToScale" => json!(ts.zoom_to_scale(f(input, "zoom"), f(input, "lat"))),
        "scaleToZoomInt" => {
            let tweak = input
                .get("tweak")
                .and_then(Value::as_f64)
                .unwrap_or(TWEAK_ZOOM_TO_SCALE_BY);
            json!(ts.scale_to_zoom_int(f(input, "scale"), f(input, "lat"), tweak))
        }
        "createTileClipPolygon" => {
            let expand = input
                .get("expandPixels")
                .and_then(Value::as_i64)
                .unwrap_or(0) as i32;
            let polygon =
                ts.create_tile_clip_polygon(i(input, "x"), i(input, "y"), z(input), expand);
            Value::Array(polygon.exterior().0.iter().map(|c| xy(*c)).collect())
        }
        op => panic!("unknown op {op}"),
    }
}

fn compare(input: &Value, expected: &Value, actual: &Value) -> Result<(), String> {
    if input["op"] == "createTileClipPolygon" {
        let theirs = parse_polygon_wkt(expected.as_str().unwrap());
        let ours: Vec<(f64, f64)> = actual
            .as_array()
            .unwrap()
            .iter()
            .map(|c| (c[0].as_f64().unwrap(), c[1].as_f64().unwrap()))
            .collect();
        let same = ours.len() == theirs.len()
            && ours
                .iter()
                .zip(&theirs)
                .all(|(a, b)| same_at_wkt_precision(a.0, b.0) && same_at_wkt_precision(a.1, b.1));
        return if same {
            Ok(())
        } else {
            Err(format!("{ours:?} != {expected}"))
        };
    }
    match first_difference(expected, actual, "") {
        None => Ok(()),
        Some(d) => Err(d),
    }
}

/// The overflowed cases: what the 64-bit map size must produce instead of the fixture's garbage.
fn check_sane(input: &Value, actual: &Value) -> Result<(), String> {
    let z = z(input);
    let tiles = (1u64 << z) as f64;
    let ok = match input["op"].as_str().unwrap() {
        "mapSize" => actual.as_i64() == Some(4096i64 << z),
        "latLngToTileXy" => actual
            .as_array()
            .unwrap()
            .iter()
            .all(|v| (0.0..tiles).contains(&v.as_f64().unwrap())),
        "latLngToTileBoundedXy" => actual
            .as_array()
            .unwrap()
            .iter()
            .all(|v| v.as_f64().unwrap().abs() < tiles * 4096.0),
        "createTileClipPolygon" => actual.as_array().unwrap().iter().all(|c| {
            let (lng, lat) = (c[0].as_f64().unwrap(), c[1].as_f64().unwrap());
            (-180.0..=180.0).contains(&lng) && (-85.06..=85.06).contains(&lat)
        }),
        _ => false,
    };
    if ok {
        Ok(())
    } else {
        Err(format!("64-bit result is not sane: {actual}"))
    }
}

/// Every case, at the fixture's precision.
#[test]
fn tile_system() {
    let mut failures = Failures::default();
    let cases = cases("geo/tile_system.json");
    let mut overflowed = 0;
    for case in &cases {
        let input = &case["input"];
        let actual = compute(input);
        let golden_overflowed = input["op"] != "scaleToZoom"
            && input["op"] != "zoomToScale"
            && input["op"] != "scaleToZoomInt"
            && input["z"].as_u64().unwrap() >= GOLDEN_OVERFLOWS_FROM_Z;
        let result = if golden_overflowed {
            overflowed += 1;
            check_sane(input, &actual)
        } else {
            compare(input, port_expected(case), &actual)
        };
        failures.check(input, result);
    }
    assert_eq!(
        overflowed, 127,
        "overflowed cases: everything at z19 and z24"
    );
    failures.assert_none(cases.len());
}

/// The `latLngToTileBoundedXy` inputs were taken from the recorded clip polygon's envelope at full
/// precision (west/north, east/north, east/south, west/south, then the centre), so they
/// pin the unrounded clip polygon corners exactly, which the 15-digit WKT cannot.
#[test]
fn clip_polygon_corners_are_exact() {
    let mut failures = Failures::default();
    let cases = cases("geo/tile_system.json");
    let bounded: Vec<&Value> = cases
        .iter()
        .map(|c| &c["input"])
        .filter(|i| i["op"] == "latLngToTileBoundedXy")
        .collect();
    let mut checked = 0;
    for group in bounded.chunks(9) {
        let first = group[0];
        let (x, y, z) = (i(first, "x"), i(first, "y"), z(first));
        if u64::from(z) >= GOLDEN_OVERFLOWS_FROM_Z {
            continue;
        }
        checked += 1;
        let polygon = TILE_SYSTEM.create_tile_clip_polygon(x, y, z, 0);
        let ring = &polygon.exterior().0;
        let (west, north, east, south) = (ring[0].x, ring[0].y, ring[2].x, ring[2].y);
        let corners = [(west, north), (east, north), (east, south), (west, south)];
        for (input, (lng, lat)) in group.iter().zip(corners) {
            if (f(input, "lng"), f(input, "lat")) != (lng, lat) {
                failures.push(format!(
                    "tile {x}/{y}/{z}: corner {input} != ({lng}, {lat})"
                ));
            }
        }
    }
    failures.assert_none(checked);
}

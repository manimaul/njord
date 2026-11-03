//! `njord_geo::geometry::is_valid` gives `geo`'s `Validation::is_valid` answer on every input: random
//! rings (self-crossing stars, spikes, repeated and collinear points, rings snapped to a coarse grid
//! so segments overlap and touch), polygons with holes inside, across and on the shell, and
//! multipolygons whose parts overlap, touch along an edge or at a point. Plus the LAKARE fixture of
//! `geos_parity.rs`, a real ring of the tile path.

use geo::Validation;
use geo_types::{Coord, Geometry, GeometryCollection, LineString, MultiPolygon, Polygon};
use njord_geo::geometry::is_valid;
use wkt::TryFromWkt;

/// A small deterministic generator (SplitMix64), so a failing case reproduces.
struct Rng(u64);

impl Rng {
    fn next(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = self.0;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }

    fn below(&mut self, n: u64) -> u64 {
        self.next() % n
    }

    fn unit(&mut self) -> f64 {
        (self.next() >> 11) as f64 / (1u64 << 53) as f64
    }
}

/// A closed ring around (`cx`, `cy`): a star whose radius may jump (self-crossings), with points
/// sometimes repeated, sometimes doubling back (spikes), and coordinates snapped to `grid` when
/// `grid > 0` (collinear and touching segments).
fn ring(rng: &mut Rng, cx: f64, cy: f64, radius: f64, grid: f64) -> LineString {
    let n = 3 + rng.below(40) as usize;
    let mut coords = Vec::with_capacity(n + 2);
    let mut angle = 0.0;
    for _ in 0..n {
        angle += std::f64::consts::TAU / n as f64 * (0.2 + 1.6 * rng.unit());
        let r = radius
            * if rng.below(4) == 0 {
                0.1 + rng.unit()
            } else {
                0.8 + 0.2 * rng.unit()
            };
        let mut c = Coord {
            x: cx + r * angle.cos(),
            y: cy + r * angle.sin(),
        };
        if grid > 0.0 {
            c = Coord {
                x: (c.x / grid).round() * grid,
                y: (c.y / grid).round() * grid,
            };
        }
        coords.push(c);
        match rng.below(12) {
            0 => coords.push(c),
            1 if coords.len() > 1 => coords.push(coords[coords.len() - 2]),
            _ => {}
        }
    }
    coords.push(coords[0]);
    LineString(coords)
}

fn polygon(rng: &mut Rng, cx: f64, cy: f64, grid: f64) -> Polygon {
    let exterior = ring(rng, cx, cy, 10.0, grid);
    let holes = (0..rng.below(4))
        .map(|_| {
            let (dx, dy) = (rng.unit() * 16.0 - 8.0, rng.unit() * 16.0 - 8.0);
            let r = 0.5 + rng.unit() * 4.0;
            ring(rng, cx + dx, cy + dy, r, grid)
        })
        .collect();
    Polygon::new(exterior, holes)
}

fn check(geometry: &Geometry, label: &str) {
    assert_eq!(
        is_valid(geometry),
        geometry.is_valid(),
        "{label}: {geometry:?}"
    );
}

#[test]
fn matches_geo_on_random_polygons() {
    let mut rng = Rng(0x006E_6A6F_7264);
    let (mut valid, mut invalid) = (0, 0);
    for case in 0..20_000 {
        let grid = [0.0, 0.0, 1.0, 2.5][rng.below(4) as usize];
        let geometry = match rng.below(4) {
            0 => Geometry::LineString(ring(&mut rng, 0.0, 0.0, 10.0, grid)),
            1 => Geometry::MultiPolygon(MultiPolygon(
                (0..1 + rng.below(3))
                    .map(|_| {
                        let (cx, cy) = (rng.unit() * 30.0, rng.unit() * 30.0);
                        polygon(&mut rng, cx, cy, grid)
                    })
                    .collect(),
            )),
            _ => Geometry::Polygon(polygon(&mut rng, 0.0, 0.0, grid)),
        };
        check(&geometry, &format!("case {case}"));
        if geometry.is_valid() {
            valid += 1;
        } else {
            invalid += 1;
        }
    }
    // Both outcomes are well represented, so the comparison is not one-sided.
    assert!(
        valid > 2_000 && invalid > 2_000,
        "{valid} valid, {invalid} invalid"
    );
}

#[test]
fn matches_geo_on_edge_cases() {
    let cases = [
        // Hole touching the shell at a point (valid), along an edge (invalid), outside it.
        "POLYGON((0 0,4 0,4 4,0 4,0 0),(0 2,2 1,3 2,2 3,0 2))",
        "POLYGON((0 0,4 0,4 4,0 4,0 0),(0 1,1 1,1 2,0 2,0 1))",
        "POLYGON((0 0,4 0,4 4,0 4,0 0),(5 5,6 5,6 6,5 5))",
        // Holes touching at a point, overlapping.
        "POLYGON((0 0,4 0,4 4,0 4,0 0),(1 2,2 1,3 2,2 3,1 2),(3 2,3.5 1,3.75 2,3.5 3,3 2))",
        "POLYGON((0 0,4 0,4 4,0 4,0 0),(1 1,3 1,3 3,1 3,1 1),(2 2,3.5 2,3.5 3.5,2 3.5,2 2))",
        // Bow tie, spike, flat ring, too few points, repeated points only.
        "POLYGON((0 0,2 2,2 0,0 2,0 0))",
        "POLYGON((0 0,4 0,4 4,2 4,2 6,2 4,0 4,0 0))",
        "POLYGON((0 0,2 0,1 0,0 0))",
        "POLYGON((0 0,1 1,0 0))",
        "POLYGON((0 0,0 0,0 0,0 0))",
        "POLYGON EMPTY",
        // Multipolygon parts sharing an edge, a point, overlapping.
        "MULTIPOLYGON(((0 0,1 0,1 1,0 1,0 0)),((1 0,2 0,2 1,1 1,1 0)))",
        "MULTIPOLYGON(((0 0,1 0,1 1,0 1,0 0)),((1 1,2 1,2 2,1 2,1 1)))",
        "MULTIPOLYGON(((0 0,2 0,2 2,0 2,0 0)),((1 1,3 1,3 3,1 3,1 1)))",
        "GEOMETRYCOLLECTION(POINT(0 0),POLYGON((0 0,2 2,2 0,0 2,0 0)))",
        "GEOMETRYCOLLECTION(LINESTRING(0 0,1 1),POLYGON((0 0,1 0,1 1,0 0)))",
    ];
    for wkt in cases {
        check(&Geometry::try_from_wkt_str(wkt).unwrap(), wkt);
    }
    let nan = Polygon::new(
        LineString::from(vec![(0.0, 0.0), (f64::NAN, 1.0), (1.0, 1.0), (0.0, 0.0)]),
        vec![],
    );
    check(&Geometry::Polygon(nan.clone()), "NaN");
    check(
        &Geometry::GeometryCollection(GeometryCollection(vec![Geometry::Polygon(nan)])),
        "NaN in a collection",
    );
}

#[test]
fn matches_geo_on_the_lakare_fixture() {
    let path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/tests/fixtures/lakare_02260A82E9560782_14_2616_5742.wkt"
    );
    let geometry =
        Geometry::try_from_wkt_str(std::fs::read_to_string(path).unwrap().trim()).unwrap();
    check(&geometry, "LAKARE");
}

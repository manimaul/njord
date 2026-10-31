//! The `OgrGeometry` operations the tile path uses (`libgdal/.../OgrGeometry.kt`,
//! `MvtDataset.kt` `transformToTilePixels`), re-expressed over `geo-types` with the pure-Rust
//! `geo` crate instead of GDAL/GEOS (RUST_PORT_PLAN.md §4.1). Checked against LB-1
//! (`geo/ogr_geometry.json`) on a best-effort basis: the test lists every op where `geo` and GEOS
//! legitimately differ.
//!
//! Results follow GEOS's choice of output type where the MVT encoder's behaviour depends on it:
//! an intersection with one part comes back as the single-part type (`Point`, `LineString`,
//! `Polygon`), several parts as the multi type, and an empty result as an empty multi type of the
//! same dimension.
//!
//! Boolean operations run on `geo`'s `i_overlay` backend, which snaps coordinates to an integer
//! grid scaled to the inputs' bounding box. Output vertices therefore agree with GEOS to roughly
//! `extent × 1e-9`, far below a tile pixel, but not bit for bit.

use geo::{Area, BooleanOps, BoundingRect, Centroid, Intersects, MakeValid, MapCoords, Validation};
use geo_types::{
    Coord, Geometry, GeometryCollection, LineString, MultiLineString, MultiPoint, MultiPolygon,
    Point, Polygon, Rect,
};

use crate::tile_system::TileSystem;

/// True when the geometry has no coordinates (`OGR_G_IsEmpty`).
pub fn is_empty(geometry: &Geometry) -> bool {
    match geometry {
        Geometry::Point(p) => p.x().is_nan() && p.y().is_nan(),
        Geometry::Line(_) | Geometry::Rect(_) | Geometry::Triangle(_) => false,
        Geometry::LineString(ls) => ls.0.is_empty(),
        Geometry::Polygon(p) => p.exterior().0.is_empty(),
        Geometry::MultiPoint(mp) => mp.0.is_empty(),
        Geometry::MultiLineString(mls) => mls.iter().all(|ls| ls.0.is_empty()),
        Geometry::MultiPolygon(mp) => mp.iter().all(|p| p.exterior().0.is_empty()),
        Geometry::GeometryCollection(gc) => gc.iter().all(is_empty),
    }
}

/// Planar area (`OGR_G_GetArea`): holes subtracted, parts and collection members summed, zero
/// for points and lines. Always non-negative.
pub fn area(geometry: &Geometry) -> f64 {
    geometry.unsigned_area()
}

fn path_length(coords: &[Coord]) -> f64 {
    coords
        .windows(2)
        .map(|w| {
            let (dx, dy) = (w[1].x - w[0].x, w[1].y - w[0].y);
            (dx * dx + dy * dy).sqrt()
        })
        .sum()
}

/// Planar length (`OGR_G_Length`): line lengths, polygon perimeters including holes, collection
/// members summed, zero for points.
pub fn length(geometry: &Geometry) -> f64 {
    let polygon = |p: &Polygon| -> f64 {
        path_length(&p.exterior().0) + p.interiors().iter().map(|r| path_length(&r.0)).sum::<f64>()
    };
    match geometry {
        Geometry::Point(_) | Geometry::MultiPoint(_) => 0.0,
        Geometry::Line(l) => path_length(&[l.start, l.end]),
        Geometry::LineString(ls) => path_length(&ls.0),
        Geometry::MultiLineString(mls) => mls.iter().map(|ls| path_length(&ls.0)).sum(),
        Geometry::Polygon(p) => polygon(p),
        Geometry::MultiPolygon(mp) => mp.iter().map(polygon).sum(),
        Geometry::Rect(r) => polygon(&r.to_polygon()),
        Geometry::Triangle(t) => polygon(&t.to_polygon()),
        Geometry::GeometryCollection(gc) => gc.iter().map(length).sum(),
    }
}

/// Bounding box, or `None` for an empty geometry (where `OGR_G_GetEnvelope` reports all zeros).
pub fn envelope(geometry: &Geometry) -> Option<Rect> {
    geometry.bounding_rect()
}

/// The envelope as a polygon grown by `expand` on every side (`envelopeGeometry(expand)`). The
/// ring runs min/min, max/min, max/max, min/max, as Kotlin builds it; an empty geometry gives the
/// all-zero envelope GDAL reports.
pub fn envelope_polygon(geometry: &Geometry, expand: f64) -> Polygon {
    let (min, max) = envelope(geometry)
        .map(|r| (r.min(), r.max()))
        .unwrap_or_default();
    let (x0, y0, x1, y1) = (
        min.x - expand,
        min.y - expand,
        max.x + expand,
        max.y + expand,
    );
    Polygon::new(
        LineString::from(vec![(x0, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0)]),
        vec![],
    )
}

/// `OgrGeometry.makeValid()`: a valid geometry is returned unchanged; an invalid polygon or
/// multipolygon is repaired with `geo`'s odd-even triangulation repair, whose result is always a
/// `MultiPolygon`. GEOS's `MakeValid` instead keeps collapsed parts as lines (a `GeometryCollection`
/// result), and picks different ring start vertices. Any other invalid geometry (non-finite
/// coordinates, a line with a single distinct vertex) has no repair and gives `None`, as a failed
/// `OGR_G_MakeValid` does.
pub fn make_valid(geometry: &Geometry) -> Option<Geometry> {
    if geometry.is_valid() {
        return Some(geometry.clone());
    }
    match geometry {
        Geometry::Polygon(p) => p.make_valid().ok().map(Geometry::MultiPolygon),
        Geometry::MultiPolygon(mp) => mp.make_valid().ok().map(Geometry::MultiPolygon),
        _ => None,
    }
}

fn single_or_multi_points(points: Vec<Point>) -> Geometry {
    match <[Point; 1]>::try_from(points) {
        Ok([p]) => Geometry::Point(p),
        Err(points) => Geometry::MultiPoint(MultiPoint(points)),
    }
}

fn single_or_multi_lines(mls: MultiLineString) -> Geometry {
    match <[LineString; 1]>::try_from(mls.0) {
        Ok([ls]) => Geometry::LineString(ls),
        Err(lines) => Geometry::MultiLineString(MultiLineString(lines)),
    }
}

fn single_or_multi_polygons(mp: MultiPolygon) -> Geometry {
    match <[Polygon; 1]>::try_from(mp.0) {
        Ok([p]) => Geometry::Polygon(p),
        Err(polygons) => Geometry::MultiPolygon(MultiPolygon(polygons)),
    }
}

/// The part of `geometry` inside `mask` (closed: points and lines on the mask's boundary are
/// kept), with GEOS's output types (see the module docs). Collection members are intersected one
/// by one and empty results dropped.
pub fn intersection(geometry: &Geometry, mask: &MultiPolygon) -> Geometry {
    match geometry {
        Geometry::Point(p) => single_or_multi_points(
            Some(*p)
                .filter(|p| mask.intersects(p))
                .into_iter()
                .collect(),
        ),
        Geometry::MultiPoint(mp) => {
            single_or_multi_points(mp.iter().copied().filter(|p| mask.intersects(p)).collect())
        }
        Geometry::Line(l) => {
            let lines = MultiLineString(vec![LineString(vec![l.start, l.end])]);
            single_or_multi_lines(mask.clip(&lines, false))
        }
        Geometry::LineString(ls) => {
            single_or_multi_lines(mask.clip(&MultiLineString(vec![ls.clone()]), false))
        }
        Geometry::MultiLineString(mls) => single_or_multi_lines(mask.clip(mls, false)),
        Geometry::Polygon(p) => single_or_multi_polygons(p.intersection(mask)),
        Geometry::MultiPolygon(mp) => single_or_multi_polygons(mp.intersection(mask)),
        Geometry::Rect(r) => single_or_multi_polygons(r.to_polygon().intersection(mask)),
        Geometry::Triangle(t) => single_or_multi_polygons(t.to_polygon().intersection(mask)),
        Geometry::GeometryCollection(gc) => Geometry::GeometryCollection(GeometryCollection(
            gc.iter()
                .map(|g| intersection(g, mask))
                .filter(|g| !is_empty(g))
                .collect(),
        )),
    }
}

/// `a − b`, or `None` when `b` is invalid: Kotlin's `difference` refuses an invalid mask rather
/// than guess, and the chart-priority masking then keeps its previous include area.
pub fn difference(a: &MultiPolygon, b: &MultiPolygon) -> Option<MultiPolygon> {
    b.is_valid().then(|| a.difference(b))
}

/// The topological boundary (`OGR_G_Boundary`): polygon rings as a `LineString` (one ring) or a
/// `MultiLineString`, line endpoints as a `MultiPoint` under the mod-2 rule, an empty collection
/// for points. GEOS does not define the boundary of a `GeometryCollection`, and neither does this
/// (`None`).
pub fn boundary(geometry: &Geometry) -> Option<Geometry> {
    let rings = |polygons: &mut dyn Iterator<Item = &Polygon>| -> Vec<LineString> {
        polygons
            .flat_map(|p| std::iter::once(p.exterior()).chain(p.interiors()))
            .filter(|r| !r.0.is_empty())
            .cloned()
            .collect()
    };
    let endpoints = |lines: &mut dyn Iterator<Item = &LineString>| -> Geometry {
        let mut counts: Vec<(Coord, usize)> = Vec::new();
        for line in lines.filter(|l| !l.is_closed()) {
            for end in [line.0[0], line.0[line.0.len() - 1]] {
                match counts.iter_mut().find(|(c, _)| *c == end) {
                    Some((_, n)) => *n += 1,
                    None => counts.push((end, 1)),
                }
            }
        }
        Geometry::MultiPoint(
            counts
                .into_iter()
                .filter(|(_, n)| n % 2 == 1)
                .map(|(c, _)| Point(c))
                .collect(),
        )
    };
    Some(match geometry {
        Geometry::Point(_) | Geometry::MultiPoint(_) => {
            Geometry::GeometryCollection(GeometryCollection(vec![]))
        }
        Geometry::Line(l) => endpoints(&mut std::iter::once(&LineString(vec![l.start, l.end]))),
        Geometry::LineString(ls) => endpoints(&mut std::iter::once(ls)),
        Geometry::MultiLineString(mls) => endpoints(&mut mls.iter()),
        Geometry::Polygon(p) => polygon_rings(rings(&mut std::iter::once(p))),
        Geometry::MultiPolygon(mp) => {
            Geometry::MultiLineString(MultiLineString(rings(&mut mp.iter())))
        }
        Geometry::Rect(r) => polygon_rings(rings(&mut std::iter::once(&r.to_polygon()))),
        Geometry::Triangle(t) => polygon_rings(rings(&mut std::iter::once(&t.to_polygon()))),
        Geometry::GeometryCollection(_) => return None,
    })
}

fn polygon_rings(rings: Vec<LineString>) -> Geometry {
    if rings.len() == 1 {
        Geometry::LineString(rings.into_iter().next().unwrap())
    } else {
        Geometry::MultiLineString(MultiLineString(rings))
    }
}

/// Centroid of the highest-dimension parts, as GEOS computes it; `None` for an empty geometry.
pub fn centroid(geometry: &Geometry) -> Option<Point> {
    geometry.centroid()
}

/// `centroidOfLargestPart`: for a `MultiPolygon`, the centroid of its largest part by area (the
/// first one on a tie), so a region label lands on the mainland rather than between islands; for
/// anything else the plain [`centroid`].
pub fn centroid_of_largest_part(geometry: &Geometry) -> Option<Point> {
    if let Geometry::MultiPolygon(mp) = geometry {
        let mut largest: Option<&Polygon> = None;
        for p in mp.iter() {
            if largest.is_none_or(|l| p.unsigned_area() > l.unsigned_area()) {
                largest = Some(p);
            }
        }
        if let Some(p) = largest {
            return p.centroid();
        }
    }
    centroid(geometry)
}

/// `transformToTilePixels`: every WGS-84 coordinate replaced by its whole-pixel position relative
/// to the top-left corner of tile `x`/`y`/`z`
/// ([`TileSystem::lat_lng_to_tile_bounded_xy`]).
pub fn to_tile_pixels(
    geometry: &Geometry,
    x: i64,
    y: i64,
    z: u32,
    tile_system: &TileSystem,
) -> Geometry {
    geometry.map_coords(|c| tile_system.lat_lng_to_tile_bounded_xy(c.x, c.y, x, y, z))
}

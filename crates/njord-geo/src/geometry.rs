//! The geometry operations the tile path uses, over `geo-types` with the pure-Rust `geo` crate.
//!
//! They have the semantics of the GDAL/GEOS operations the golden tiles were produced with, and are
//! checked against the golden fixture `testdata/geo/ogr_geometry.json` on a best-effort basis: the
//! test lists every op where `geo` and GEOS legitimately differ.
//!
//! Results follow GEOS's choice of output type where the MVT encoder's behaviour depends on it:
//! an intersection with one part comes back as the single-part type (`Point`, `LineString`,
//! `Polygon`), several parts as the multi type, and an empty result as an empty multi type of the
//! same dimension. Lines are cut where GEOS cuts them, and a line that only touches the mask
//! gives the touching points ([`intersection`]).
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

/// How far, relative to the extent of a boolean op's inputs, an output vertex may lie from the
/// input vertex it was copied from (`i_overlay` grid snapping) and still count as that vertex.
const SNAP_TOLERANCE: f64 = 1e-9;

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

/// The envelope as a polygon grown by `expand` on every side.
///
/// The ring runs min/min, max/min, max/max, min/max; an empty geometry gives the all-zero envelope
/// GDAL reports.
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

/// The geometry made valid, or `None` when it has no repair.
///
/// A valid geometry is returned unchanged; an invalid polygon or multipolygon is repaired with
/// `geo`'s odd-even triangulation repair, whose result is always a `MultiPolygon`. GEOS's
/// `MakeValid` instead keeps collapsed parts as lines (a `GeometryCollection` result), and picks
/// different ring start vertices. Any other invalid geometry (non-finite
/// coordinates, a line with a single distinct vertex) has no repair and gives `None`, as a failed
/// `OGR_G_MakeValid` does.
///
/// The repair is made deterministic: every ring is rotated to start at its smallest vertex and
/// rings and polygons are sorted by that vertex. `geo` traces its output rings by iterating a
/// `HashSet` with a randomly seeded hasher, so its ring start vertices and the order of rings and
/// polygons change from call to call (and with them what Douglas–Peucker keeps downstream, e.g.
/// LAKARE `02260A82E9560782` in the golden tile 14/2616/5742).
pub fn make_valid(geometry: &Geometry) -> Option<Geometry> {
    if geometry.is_valid() {
        return Some(geometry.clone());
    }
    let repaired = match geometry {
        Geometry::Polygon(p) => p.make_valid().ok()?,
        Geometry::MultiPolygon(mp) => mp.make_valid().ok()?,
        _ => return None,
    };
    Some(Geometry::MultiPolygon(canonical_polygons(repaired)))
}

/// Every ring rotated to start at its smallest vertex (by x, then y), holes and polygons ordered
/// by that vertex.
///
/// This is [`make_valid`]'s deterministic output, and the ring starts of [`intersection`]
/// (`i_overlay` starts rings elsewhere; this start agrees more often with GEOS's after
/// Douglas–Peucker in the golden tiles).
fn canonical_polygons(polygons: MultiPolygon) -> MultiPolygon {
    let smallest = |a: &Coord, b: &Coord| a.x.total_cmp(&b.x).then(a.y.total_cmp(&b.y));
    let rotate = |ring: &mut LineString| {
        let cs = &mut ring.0;
        if cs.len() < 2 || cs.first() != cs.last() {
            return;
        }
        cs.pop();
        let start = (0..cs.len())
            .min_by(|&a, &b| smallest(&cs[a], &cs[b]))
            .unwrap_or(0);
        cs.rotate_left(start);
        cs.push(cs[0]);
    };
    let by_start = |a: &LineString, b: &LineString| match (a.0.first(), b.0.first()) {
        (Some(a), Some(b)) => smallest(a, b),
        (a, b) => a.is_some().cmp(&b.is_some()),
    };
    let mut polygons: Vec<Polygon> = polygons
        .0
        .into_iter()
        .map(|p| {
            let (mut exterior, mut interiors) = p.into_inner();
            rotate(&mut exterior);
            interiors.iter_mut().for_each(rotate);
            interiors.sort_by(by_start);
            Polygon::new(exterior, interiors)
        })
        .collect();
    polygons.sort_by(|a, b| by_start(a.exterior(), b.exterior()));
    MultiPolygon(polygons)
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

/// The part of `geometry` inside `mask`, with GEOS's output types (see the module docs).
///
/// The mask is closed: points and lines on its boundary are kept. Lines are split as GEOS splits
/// them, and polygon rings start at their smallest vertex, holes and polygons ordered by that
/// vertex. Collection members are intersected one by one and empty results dropped.
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
        Geometry::Line(l) => clip_lines(
            &MultiLineString(vec![LineString(vec![l.start, l.end])]),
            mask,
        ),
        Geometry::LineString(ls) => clip_lines(&MultiLineString(vec![ls.clone()]), mask),
        Geometry::MultiLineString(mls) => clip_lines(mls, mask),
        Geometry::Polygon(p) => single_or_multi_polygons(canonical_polygons(p.intersection(mask))),
        Geometry::MultiPolygon(mp) => {
            single_or_multi_polygons(canonical_polygons(mp.intersection(mask)))
        }
        Geometry::Rect(r) => {
            single_or_multi_polygons(canonical_polygons(r.to_polygon().intersection(mask)))
        }
        Geometry::Triangle(t) => {
            single_or_multi_polygons(canonical_polygons(t.to_polygon().intersection(mask)))
        }
        Geometry::GeometryCollection(gc) => Geometry::GeometryCollection(GeometryCollection(
            gc.iter()
                .map(|g| intersection(g, mask))
                .filter(|g| !is_empty(g))
                .collect(),
        )),
    }
}

/// Lines intersected with `mask` as GEOS's overlay returns them, one input line at a time.
///
/// The pieces come from `geo`'s line clip. GEOS's result lines are not merged: they run between
/// nodes, which are the points where a line meets the mask's boundary, where it crosses itself,
/// and its own start and end vertex. `geo` instead merges result lines at every vertex where just
/// two of them meet, and walks a closed line from its smallest vertex (in the golden tiles,
/// coastline, contour and coverage-boundary rings come back split there). So for each input line:
///
/// - two pieces that meet end to start strictly inside the mask, with no other piece ending
///   there, are joined, unless they meet at the line's own start vertex;
/// - a piece that passes through the start vertex of a closed line is split there;
/// - a closed piece (the whole ring inside the mask) is rotated back to the line's start.
///
/// A line that only touches the mask has no clipped part, but GEOS returns the touching points
/// (SLCONS `022600D606360032` in the golden tile 14/2619/5744, which after simplification ends on
/// the encoder's clip square and so is drawn as a POINT).
fn clip_lines(lines: &MultiLineString, mask: &MultiPolygon) -> Geometry {
    let mut pieces: Vec<LineString> = Vec::new();
    for line in lines.iter().filter(|l| !l.0.is_empty()) {
        pieces.extend(clip_line(line, mask));
    }
    if pieces.is_empty() {
        return single_or_multi_points(touching_points(lines, mask));
    }
    single_or_multi_lines(MultiLineString(pieces))
}

/// [`clip_lines`] for one line.
fn clip_line(line: &LineString, mask: &MultiPolygon) -> Vec<LineString> {
    use geo::Contains;
    let mut pieces: Vec<LineString> = mask
        .clip(&MultiLineString(vec![line.clone()]), false)
        .0
        .into_iter()
        .filter(|l| !l.0.is_empty())
        .collect();
    let start = line.0[0];
    let extent = |r: Option<Rect>| r.map_or(0.0, |r| r.width().max(r.height()));
    let tolerance = SNAP_TOLERANCE * extent(line.bounding_rect()).max(extent(mask.bounding_rect()));
    let is_start = |c: &Coord| (c.x - start.x).hypot(c.y - start.y) <= tolerance;
    let ends_at = |pieces: &[LineString], c: Coord| {
        pieces
            .iter()
            .map(|p| usize::from(p.0[0] == c) + usize::from(p.0[p.0.len() - 1] == c))
            .sum::<usize>()
    };
    'merge: loop {
        for i in 0..pieces.len() {
            let end = pieces[i].0[pieces[i].0.len() - 1];
            if pieces[i].is_closed()
                || is_start(&end)
                || !mask.contains(&Point(end))
                || ends_at(&pieces, end) != 2
            {
                continue;
            }
            if let Some(j) = (0..pieces.len()).find(|&j| j != i && pieces[j].0[0] == end) {
                let tail = pieces.remove(j);
                let i = if j < i { i - 1 } else { i };
                pieces[i].0.extend(tail.0.into_iter().skip(1));
                continue 'merge;
            }
        }
        break;
    }
    if !line.is_closed() {
        return pieces;
    }
    let mut out = Vec::with_capacity(pieces.len() + 1);
    for mut piece in pieces {
        let cs = &mut piece.0;
        if piece_is_ring(cs) {
            cs.pop();
            let k = (0..cs.len()).find(|&k| is_start(&cs[k])).unwrap_or(0);
            cs.rotate_left(k);
            cs.push(cs[0]);
            out.push(piece);
        } else if let Some(k) = (1..cs.len().saturating_sub(1)).find(|&k| is_start(&cs[k])) {
            let tail = cs.split_off(k);
            cs.push(tail[0]);
            out.push(piece);
            out.push(LineString(tail));
        } else {
            out.push(piece);
        }
    }
    out
}

fn piece_is_ring(cs: &[Coord]) -> bool {
    cs.len() > 2 && cs.first() == cs.last()
}

/// The points where `lines` meet the boundary of `mask`, in input order without repeats.
fn touching_points(lines: &MultiLineString, mask: &MultiPolygon) -> Vec<Point> {
    use geo::line_intersection::{LineIntersection, line_intersection};
    let edges: Vec<geo_types::Line> = mask
        .iter()
        .flat_map(|p| std::iter::once(p.exterior()).chain(p.interiors()))
        .flat_map(|r| r.lines())
        .collect();
    let mut points: Vec<Point> = Vec::new();
    for segment in lines.iter().flat_map(|l| l.lines()) {
        for edge in &edges {
            if let Some(LineIntersection::SinglePoint { intersection, .. }) =
                line_intersection(segment, *edge)
                && !points.iter().any(|p| p.0 == intersection)
            {
                points.push(Point(intersection));
            }
        }
    }
    points
}

/// `a − b`, or `None` when `b` is invalid.
///
/// An invalid mask is refused rather than guessed at, and the chart-priority masking then keeps
/// its previous include area.
pub fn difference(a: &MultiPolygon, b: &MultiPolygon) -> Option<MultiPolygon> {
    b.is_valid().then(|| a.difference(b))
}

/// The topological boundary (`OGR_G_Boundary`), or `None` for a `GeometryCollection`.
///
/// Polygon rings come back as a `LineString` (one ring) or a `MultiLineString`, line endpoints as
/// a `MultiPoint` under the mod-2 rule, and points as an empty collection. GEOS does not define
/// the boundary of a `GeometryCollection`, and neither does this.
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

/// The centroid of a `MultiPolygon`'s largest part by area, or the plain [`centroid`] of anything
/// else.
///
/// On a tie the first largest part wins. A region label thus lands on the mainland rather than
/// between islands.
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

/// The geometry with every WGS-84 coordinate replaced by its whole-pixel position relative to the
/// top-left corner of tile `x`/`y`/`z` ([`TileSystem::lat_lng_to_tile_bounded_xy`]).
pub fn to_tile_pixels(
    geometry: &Geometry,
    x: i64,
    y: i64,
    z: u32,
    tile_system: &TileSystem,
) -> Geometry {
    geometry.map_coords(|c| tile_system.lat_lng_to_tile_bounded_xy(c.x, c.y, x, y, z))
}

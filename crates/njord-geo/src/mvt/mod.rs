//! MVT encoding, ported from Kotlin `libgdal/.../tile/VectorTileEncoder.kt` (itself a port of
//! `java-vector-tile`). Pinned by P-TE-2 (`geo/mvt/<case>.bin` + `.json`).
//!
//! # Why a hand-written encoder
//!
//! The plan suggested `geozero`'s MVT writer or the `mvt` crate. Neither reproduces what P-TE-2
//! pins: both generate their own command streams, so they do not drop consecutive duplicate
//! vertices or degenerate multi-line parts the way the Kotlin encoder does, do not force the
//! Kotlin ring orientation, and know nothing of the 1 px / 1 px² minimum sizes, the 0.1 px
//! simplification, the 8 px clip buffer, the id autoincrement that starts at 2, or the JSON value
//! mapping. Wrapping either would still leave all of that to write here, plus a second geometry
//! model. So the command generation is a line-for-line port of the Kotlin, geometry processing
//! uses `geo` (simplify, clip, validity), and the protobuf is written by hand in [`proto`]
//! (≈150 lines, no code generation), in the same field order as `kotlinx.serialization`, which
//! makes the output byte-identical to Kotlin's wherever the geometry library agrees with GEOS.
//! [`view`] decodes tiles back into the fixtures' JSON view, which the Kotlin side has too.
//!
//! # Pipeline per feature
//!
//! 1. Invalid geometry goes through [`make_valid`]; one that cannot be repaired is dropped without
//!    consuming an id.
//! 2. Polygons under 1 px² and lines under 1 px are dropped (their id is consumed).
//! 3. A `GeometryCollection` is split into one feature per member, all sharing one id.
//! 4. Lines and polygons are simplified with Douglas–Peucker at 0.1 px. Kotlin uses GEOS
//!    `simplifyPreserveTopology` for lines and `simplify` for polygons; `geo` has no
//!    topology-preserving variant, so both use `geo`'s Ramer–Douglas–Peucker. They differ only
//!    when simplification would make a line cross itself.
//! 5. Points are kept only strictly inside the tile plus the clip buffer plus 1 px (Kotlin tests
//!    containment in the clip square's envelope grown by one pixel). Anything else whose envelope
//!    lies within that grown square is kept whole; the rest is intersected with the clip square
//!    (`-8..4104`), and an empty result is dropped.
//! 6. Coordinates are rounded half to even (Kotlin's `round`) and delta/zig-zag encoded per the
//!    MVT 2.1 spec; exterior rings are written clockwise on screen, holes counter-clockwise.

pub mod proto;
pub mod view;

use geo::Simplify;
use geo_types::{Coord, Geometry, LineString, MultiPolygon, Polygon};
use indexmap::IndexMap;
use njord_model::JsonObject;
use serde_json::Value;

use crate::geometry::{self, is_empty, make_valid};
use proto::{GeomType, Tile};

/// Tile extent in pixels.
pub const EXTENT: u32 = 4096;
/// Pixels kept beyond each tile edge so lines and fills meet seamlessly across tiles.
pub const CLIP_BUFFER: i32 = 8;
const SIMPLIFICATION_DISTANCE_TOLERANCE: f64 = 0.1;
const MINIMUM_LENGTH: f64 = 1.0;
const MINIMUM_AREA: f64 = MINIMUM_LENGTH * MINIMUM_LENGTH;

const MOVE_TO: u32 = 1;
const LINE_TO: u32 = 2;
const CLOSE_PATH: u32 = 7;

/// The square `-buffer..size + buffer` (`createTileEnvelope`), ring starting at the bottom-left
/// in tile pixels.
pub fn create_tile_envelope(buffer: i32, size: u32) -> Polygon {
    let (lo, hi) = (f64::from(-buffer), f64::from(size as i32 + buffer));
    Polygon::new(
        LineString::from(vec![(lo, hi), (hi, hi), (hi, lo), (lo, lo), (lo, hi)]),
        vec![],
    )
}

struct Feature {
    geometry: Geometry,
    tags: Vec<u32>,
    id: u64,
}

#[derive(Default)]
struct Layer {
    features: Vec<Feature>,
    keys: IndexMap<String, u32>,
    /// Keyed by the value's JSON text, which tells `"1"` from `1` and `1` from `1.0` as Kotlin's
    /// `JsonElement` equality does.
    values: IndexMap<String, (u32, Value)>,
}

impl Layer {
    fn key(&mut self, key: &str) -> u32 {
        let next = self.keys.len() as u32;
        *self.keys.entry(key.to_owned()).or_insert(next)
    }

    fn value(&mut self, value: &Value) -> u32 {
        let next = self.values.len() as u32;
        self.values
            .entry(value.to_string())
            .or_insert_with(|| (next, value.clone()))
            .0
    }
}

/// Accumulates features per layer and encodes them as one tile. Geometry must already be in tile
/// pixels (see [`geometry::to_tile_pixels`]).
pub struct VectorTileEncoder {
    layers: IndexMap<String, Layer>,
    autoincrement: u64,
    clip_square: MultiPolygon,
}

impl Default for VectorTileEncoder {
    fn default() -> Self {
        VectorTileEncoder::new()
    }
}

impl VectorTileEncoder {
    pub fn new() -> Self {
        VectorTileEncoder {
            layers: IndexMap::new(),
            autoincrement: 1,
            clip_square: MultiPolygon(vec![create_tile_envelope(CLIP_BUFFER, EXTENT)]),
        }
    }

    /// Adds the clip square's outline and centre to `layer_name` (the `?debug` tile overlay).
    pub fn add_debug_envelope(&mut self, layer_name: &str, attributes: &JsonObject) {
        let border = Geometry::Polygon(create_tile_envelope(CLIP_BUFFER, EXTENT));
        if let Some(boundary) = geometry::boundary(&border) {
            self.add_feature(layer_name, attributes, &boundary);
        }
        if let Some(centre) = geometry::centroid(&border) {
            self.add_feature(layer_name, attributes, &Geometry::Point(centre));
        }
    }

    /// Adds one feature. Feature ids are an autoincrement starting at 2 (Kotlin's `++autoincrement`
    /// from 1), consumed even when the geometry is later dropped as too small or outside the
    /// tile; MapLibre falls back to this id where `promoteId: LNAM` finds no `LNAM`.
    pub fn add_feature(&mut self, layer_name: &str, attributes: &JsonObject, geometry: &Geometry) {
        let Some(valid) = make_valid(geometry) else {
            return;
        };
        self.autoincrement += 1;
        let id = self.autoincrement;
        self.add(layer_name, attributes, normalize(valid), id);
    }

    fn add(&mut self, layer_name: &str, attributes: &JsonObject, geometry: Geometry, id: u64) {
        match &geometry {
            Geometry::Polygon(_) | Geometry::MultiPolygon(_)
                if geometry::area(&geometry) < MINIMUM_AREA =>
            {
                return;
            }
            Geometry::LineString(_) | Geometry::MultiLineString(_)
                if geometry::length(&geometry) < MINIMUM_LENGTH =>
            {
                return;
            }
            Geometry::GeometryCollection(gc) => {
                for member in gc.iter() {
                    self.add(layer_name, attributes, normalize(member.clone()), id);
                }
                return;
            }
            _ => {}
        }

        let geometry = match geometry {
            Geometry::LineString(ls) => {
                Geometry::LineString(ls.simplify(SIMPLIFICATION_DISTANCE_TOLERANCE))
            }
            Geometry::MultiLineString(mls) => {
                Geometry::MultiLineString(mls.simplify(SIMPLIFICATION_DISTANCE_TOLERANCE))
            }
            Geometry::Polygon(p) => {
                Geometry::Polygon(p.simplify(SIMPLIFICATION_DISTANCE_TOLERANCE))
            }
            Geometry::MultiPolygon(mp) => {
                Geometry::MultiPolygon(mp.simplify(SIMPLIFICATION_DISTANCE_TOLERANCE))
            }
            other => other,
        };

        let geometry = match geometry {
            Geometry::Point(p) => {
                if !inside_clip_envelope(p.0) {
                    return;
                }
                Geometry::Point(p)
            }
            other => self.clip(other),
        };

        if is_empty(&geometry) {
            return;
        }

        if let Geometry::GeometryCollection(gc) = geometry {
            for member in gc {
                self.add(layer_name, attributes, normalize(member), id);
            }
            return;
        }

        let layer = self.layers.entry(layer_name.to_owned()).or_default();
        let mut tags = Vec::with_capacity(attributes.len() * 2);
        for (key, value) in attributes {
            tags.push(layer.key(key));
            tags.push(layer.value(value));
        }
        layer.features.push(Feature { geometry, tags, id });
    }

    fn clip(&self, geometry: Geometry) -> Geometry {
        let (lo, hi) = clip_envelope_bounds();
        let within = geometry::envelope(&geometry).is_none_or(|r| {
            r.min().x >= lo && r.min().y >= lo && r.max().x <= hi && r.max().y <= hi
        });
        if within {
            geometry
        } else {
            geometry::intersection(&geometry, &self.clip_square)
        }
    }

    /// True if any layer not in `excluding_layers` has a feature: a tile holding only the `PLY`
    /// chart-coverage outline has no real content.
    pub fn has_features(&self, excluding_layers: &[&str]) -> bool {
        self.layers.iter().any(|(name, layer)| {
            !excluding_layers.contains(&name.as_str()) && !layer.features.is_empty()
        })
    }

    /// The tile as protobuf bytes; a feature whose geometry encodes to no commands is left out,
    /// but its layer is still written.
    pub fn encode(&self) -> Vec<u8> {
        self.tile().encode()
    }

    /// The tile message [`encode`](Self::encode) writes.
    pub fn tile(&self) -> Tile {
        let layers = self
            .layers
            .iter()
            .map(|(name, layer)| {
                let features = layer
                    .features
                    .iter()
                    .filter_map(|f| {
                        let geometry = Commands::default().geometry(&f.geometry);
                        (!geometry.is_empty()).then(|| proto::Feature {
                            id: f.id,
                            tags: f.tags.clone(),
                            geom_type: tile_type(&f.geometry),
                            geometry,
                        })
                    })
                    .collect();
                proto::Layer {
                    version: 2,
                    name: name.clone(),
                    features,
                    keys: layer.keys.keys().cloned().collect(),
                    values: layer.values.values().map(|(_, v)| tile_value(v)).collect(),
                    extent: EXTENT,
                }
            })
            .collect();
        Tile { layers }
    }
}

/// GDAL has no `Line`, `Rect` or `Triangle`: they encode as the line or polygon they are.
fn normalize(geometry: Geometry) -> Geometry {
    match geometry {
        Geometry::Line(l) => Geometry::LineString(LineString(vec![l.start, l.end])),
        Geometry::Rect(r) => Geometry::Polygon(r.to_polygon()),
        Geometry::Triangle(t) => Geometry::Polygon(t.to_polygon()),
        other => other,
    }
}

/// The clip square's envelope grown by one pixel: `-9..4105`. Kotlin grows it because GDAL has no
/// `covers`, only `contains`, and geometry on the clip square's outline must not be lost.
fn clip_envelope_bounds() -> (f64, f64) {
    (
        f64::from(-CLIP_BUFFER - 1),
        f64::from(EXTENT as i32 + CLIP_BUFFER + 1),
    )
}

/// `contains` of a point is strict: a point on the grown envelope's outline is outside.
fn inside_clip_envelope(c: Coord) -> bool {
    let (lo, hi) = clip_envelope_bounds();
    c.x > lo && c.x < hi && c.y > lo && c.y < hi
}

fn tile_type(geometry: &Geometry) -> GeomType {
    match geometry {
        Geometry::Point(_) | Geometry::MultiPoint(_) => GeomType::Point,
        Geometry::Line(_) | Geometry::LineString(_) | Geometry::MultiLineString(_) => {
            GeomType::LineString
        }
        Geometry::Polygon(_)
        | Geometry::MultiPolygon(_)
        | Geometry::Rect(_)
        | Geometry::Triangle(_) => GeomType::Polygon,
        Geometry::GeometryCollection(_) => GeomType::Unknown,
    }
}

/// `jsonPrimitiveTileValue`: strings stay strings, booleans are `bool_value`, integers that fit
/// an `i32` are `int_value`, other `i64`s `sint_value`, every other number `double_value`. A
/// `null` is the string `"null"` (Kotlin reads its content), and arrays and objects are their
/// compact JSON text.
fn tile_value(value: &Value) -> proto::Value {
    let mut v = proto::Value::default();
    match value {
        Value::String(s) => v.string_value = Some(s.clone()),
        Value::Bool(b) => v.bool_value = Some(*b),
        Value::Number(n) => match n.as_i64() {
            Some(i) if i32::try_from(i).is_ok() => v.int_value = Some(i),
            Some(i) => v.sint_value = Some(i),
            None => v.double_value = n.as_f64(),
        },
        Value::Null => v.string_value = Some("null".to_owned()),
        Value::Array(_) | Value::Object(_) => v.string_value = Some(value.to_string()),
    }
    v
}

fn command_and_length(command: u32, repeat: i32) -> u32 {
    ((repeat << 3) as u32) | command
}

fn zig_zag_encode(n: i32) -> u32 {
    ((n << 1) ^ (n >> 31)) as u32
}

/// Kotlin's `round(d).toInt()`: half to even, saturating.
fn round_px(d: f64) -> i32 {
    d.round_ties_even() as i32
}

/// The command stream for one feature. The cursor carries across the parts of a multi geometry,
/// as MVT deltas do.
#[derive(Default)]
struct Commands {
    x: i32,
    y: i32,
}

impl Commands {
    fn geometry(&mut self, geometry: &Geometry) -> Vec<u32> {
        match geometry {
            Geometry::Point(p) => self.coords(&[p.0], false, false),
            Geometry::LineString(ls) => self.coords(&ls.0, false, false),
            Geometry::Polygon(p) => self.polygon(p),
            // Kotlin reads a MultiPoint's points with `OGR_G_GetPoints`, which sees no points on a
            // collection, so a MultiPoint encodes to nothing and the feature is left out (P-TE-2
            // `multi`). Clipping turns a MultiPoint with one point inside into a Point, which does
            // encode.
            Geometry::MultiPoint(_) => vec![],
            Geometry::MultiLineString(mls) => mls
                .iter()
                .flat_map(|ls| {
                    let (old_x, old_y) = (self.x, self.y);
                    let commands = self.coords(&ls.0, false, false);
                    // A part whose points all round to one pixel is a lone MoveTo, which the spec
                    // forbids: drop it and restore the cursor.
                    if commands.len() > 3 {
                        commands
                    } else {
                        (self.x, self.y) = (old_x, old_y);
                        vec![]
                    }
                })
                .collect(),
            Geometry::MultiPolygon(mp) => mp.iter().flat_map(|p| self.polygon(p)).collect(),
            Geometry::Line(_)
            | Geometry::Rect(_)
            | Geometry::Triangle(_)
            | Geometry::GeometryCollection(_) => {
                unreachable!("normalised before it reaches the encoder")
            }
        }
    }

    /// The exterior ring clockwise on screen (positive shoelace with y down) and holes
    /// counter-clockwise, whatever the input winding.
    fn polygon(&mut self, polygon: &Polygon) -> Vec<u32> {
        let ring = |cs: &[Coord], reverse: bool| -> Vec<Coord> {
            let mut cs = cs.to_vec();
            if reverse {
                cs.reverse();
            }
            cs
        };
        let exterior = &polygon.exterior().0;
        let mut commands = self.coords(&ring(exterior, signed_area(exterior) < 0.0), true, false);
        for interior in polygon.interiors() {
            let cs = ring(&interior.0, signed_area(&interior.0) > 0.0);
            commands.extend(self.coords(&cs, true, false));
        }
        commands
    }

    fn coords(&mut self, cs: &[Coord], close_path_at_end: bool, multi_point: bool) -> Vec<u32> {
        if cs.is_empty() {
            return vec![];
        }
        let mut r: Vec<u32> = Vec::new();
        let mut line_to_index = 0;
        let mut line_to_length = 0i32;
        let n = cs.len() as i32;

        for (i, c) in cs.iter().enumerate() {
            if i == 0 {
                r.push(command_and_length(MOVE_TO, if multi_point { n } else { 1 }));
            }
            let (x, y) = (round_px(c.x), round_px(c.y));

            // a point equal to the previous one after rounding
            if i > 0 && x == self.x && y == self.y {
                line_to_length -= 1;
                continue;
            }
            // the ring's closing point: ClosePath draws that edge
            if close_path_at_end && cs.len() > 1 && i == cs.len() - 1 && same_position(cs[0], *c) {
                line_to_length -= 1;
                continue;
            }

            r.push(zig_zag_encode(x.wrapping_sub(self.x)));
            r.push(zig_zag_encode(y.wrapping_sub(self.y)));
            (self.x, self.y) = (x, y);

            if i == 0 && cs.len() > 1 && !multi_point {
                line_to_index = r.len();
                line_to_length = n - 1;
                r.push(command_and_length(LINE_TO, line_to_length));
            }
        }

        if line_to_index > 0 {
            if line_to_length == 0 {
                r.remove(line_to_index);
            } else {
                r[line_to_index] = command_and_length(LINE_TO, line_to_length);
            }
        }
        if close_path_at_end {
            r.push(command_and_length(CLOSE_PATH, 1));
        }
        r
    }
}

/// Kotlin's data-class `Position` equality, which compares doubles with `compareTo`: `-0.0` and
/// `0.0` differ, `NaN` equals `NaN`.
fn same_position(a: Coord, b: Coord) -> bool {
    a.x.total_cmp(&b.x).is_eq() && a.y.total_cmp(&b.y).is_eq()
}

/// Shoelace area over the ring as given (closing vertex included), y down.
fn signed_area(cs: &[Coord]) -> f64 {
    let mut area = 0.0;
    for i in 0..cs.len() {
        let j = (i + 1) % cs.len();
        area += cs[i].x * cs[j].y - cs[j].x * cs[i].y;
    }
    area / 2.0
}

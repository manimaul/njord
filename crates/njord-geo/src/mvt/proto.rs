//! The Mapbox Vector Tile 2.1 protobuf messages (`vector_tile.proto`, Kotlin `tile/Tile.kt`),
//! written and read by hand.
//!
//! The writer emits fields in the order `kotlinx.serialization.protobuf` does — declaration order
//! of `Tile.kt`, which puts `Layer.version` (field 15) first — and omits fields equal to their
//! Kotlin default (`version == 1`, empty lists, `id == 0`, `type == UNKNOWN`). For the same input
//! the bytes are therefore identical to the Kotlin encoder's, which the P-TE-2 test checks. The
//! reader accepts any field order and both packed and unpacked repeated fields.

/// `Tile.GeomType`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum GeomType {
    Unknown = 0,
    Point = 1,
    LineString = 2,
    Polygon = 3,
}

impl GeomType {
    /// The enum constant's name, as the decoded view prints it.
    pub fn name(self) -> &'static str {
        match self {
            GeomType::Unknown => "UNKNOWN",
            GeomType::Point => "POINT",
            GeomType::LineString => "LINESTRING",
            GeomType::Polygon => "POLYGON",
        }
    }

    fn from_wire(v: u64) -> GeomType {
        match v {
            1 => GeomType::Point,
            2 => GeomType::LineString,
            3 => GeomType::Polygon,
            _ => GeomType::Unknown,
        }
    }
}

/// `Tile.Value`: exactly one field is set by the encoder, but the wire format allows any.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Value {
    pub string_value: Option<String>,
    pub float_value: Option<f32>,
    pub double_value: Option<f64>,
    pub int_value: Option<i64>,
    pub uint_value: Option<u64>,
    pub sint_value: Option<i64>,
    pub bool_value: Option<bool>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Feature {
    pub id: u64,
    pub tags: Vec<u32>,
    pub geom_type: GeomType,
    pub geometry: Vec<u32>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Layer {
    pub version: u32,
    pub name: String,
    pub features: Vec<Feature>,
    pub keys: Vec<String>,
    pub values: Vec<Value>,
    pub extent: u32,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct Tile {
    pub layers: Vec<Layer>,
}

const VARINT: u8 = 0;
const FIXED64: u8 = 1;
const LEN: u8 = 2;
const FIXED32: u8 = 5;

#[derive(Default)]
struct Writer(Vec<u8>);

impl Writer {
    fn varint(&mut self, mut v: u64) {
        while v >= 0x80 {
            self.0.push((v as u8) | 0x80);
            v >>= 7;
        }
        self.0.push(v as u8);
    }

    fn key(&mut self, field: u32, wire: u8) {
        self.varint(u64::from(field << 3 | u32::from(wire)));
    }

    fn uint(&mut self, field: u32, v: u64) {
        self.key(field, VARINT);
        self.varint(v);
    }

    fn bytes(&mut self, field: u32, bytes: &[u8]) {
        self.key(field, LEN);
        self.varint(bytes.len() as u64);
        self.0.extend_from_slice(bytes);
    }

    fn packed(&mut self, field: u32, values: &[u32]) {
        if values.is_empty() {
            return;
        }
        let mut inner = Writer::default();
        values.iter().for_each(|v| inner.varint(u64::from(*v)));
        self.bytes(field, &inner.0);
    }
}

fn encode_value(value: &Value) -> Vec<u8> {
    let mut w = Writer::default();
    if let Some(s) = &value.string_value {
        w.bytes(1, s.as_bytes());
    }
    if let Some(f) = value.float_value {
        w.key(2, FIXED32);
        w.0.extend_from_slice(&f.to_le_bytes());
    }
    if let Some(d) = value.double_value {
        w.key(3, FIXED64);
        w.0.extend_from_slice(&d.to_le_bytes());
    }
    if let Some(i) = value.int_value {
        w.uint(4, i as u64);
    }
    if let Some(u) = value.uint_value {
        w.uint(5, u);
    }
    if let Some(s) = value.sint_value {
        w.uint(6, ((s << 1) ^ (s >> 63)) as u64);
    }
    if let Some(b) = value.bool_value {
        w.uint(7, u64::from(b));
    }
    w.0
}

fn encode_feature(feature: &Feature) -> Vec<u8> {
    let mut w = Writer::default();
    if feature.id != 0 {
        w.uint(1, feature.id);
    }
    w.packed(2, &feature.tags);
    if feature.geom_type != GeomType::Unknown {
        w.uint(3, feature.geom_type as u64);
    }
    w.packed(4, &feature.geometry);
    w.0
}

fn encode_layer(layer: &Layer) -> Vec<u8> {
    let mut w = Writer::default();
    if layer.version != 1 {
        w.uint(15, u64::from(layer.version));
    }
    w.bytes(1, layer.name.as_bytes());
    for f in &layer.features {
        w.bytes(2, &encode_feature(f));
    }
    for k in &layer.keys {
        w.bytes(3, k.as_bytes());
    }
    for v in &layer.values {
        w.bytes(4, &encode_value(v));
    }
    w.uint(5, u64::from(layer.extent));
    w.0
}

impl Tile {
    pub fn encode(&self) -> Vec<u8> {
        let mut w = Writer::default();
        for layer in &self.layers {
            w.bytes(3, &encode_layer(layer));
        }
        w.0
    }

    pub fn decode(bytes: &[u8]) -> Result<Tile, DecodeError> {
        let mut tile = Tile::default();
        for field in Reader(bytes) {
            if let (3, Field::Len(b)) = field? {
                tile.layers.push(decode_layer(b)?);
            }
        }
        Ok(tile)
    }
}

/// The bytes are not a well-formed vector tile.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DecodeError(pub &'static str);

impl std::fmt::Display for DecodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "invalid vector tile: {}", self.0)
    }
}

impl std::error::Error for DecodeError {}

enum Field<'a> {
    Varint(u64),
    Fixed64([u8; 8]),
    Fixed32([u8; 4]),
    Len(&'a [u8]),
}

struct Reader<'a>(&'a [u8]);

impl<'a> Reader<'a> {
    fn varint(&mut self) -> Result<u64, DecodeError> {
        let mut v = 0u64;
        for shift in (0..64).step_by(7) {
            let (&b, rest) = self
                .0
                .split_first()
                .ok_or(DecodeError("truncated varint"))?;
            self.0 = rest;
            v |= u64::from(b & 0x7f) << shift;
            if b & 0x80 == 0 {
                return Ok(v);
            }
        }
        Err(DecodeError("varint too long"))
    }

    fn take(&mut self, n: usize) -> Result<&'a [u8], DecodeError> {
        if self.0.len() < n {
            return Err(DecodeError("truncated field"));
        }
        let (head, rest) = self.0.split_at(n);
        self.0 = rest;
        Ok(head)
    }

    fn field(&mut self) -> Result<(u32, Field<'a>), DecodeError> {
        let key = self.varint()?;
        let number = (key >> 3) as u32;
        let field = match (key & 7) as u8 {
            VARINT => Field::Varint(self.varint()?),
            FIXED64 => Field::Fixed64(self.take(8)?.try_into().unwrap()),
            FIXED32 => Field::Fixed32(self.take(4)?.try_into().unwrap()),
            LEN => {
                let n = self.varint()? as usize;
                Field::Len(self.take(n)?)
            }
            _ => return Err(DecodeError("unsupported wire type")),
        };
        Ok((number, field))
    }
}

impl<'a> Iterator for Reader<'a> {
    type Item = Result<(u32, Field<'a>), DecodeError>;

    fn next(&mut self) -> Option<Self::Item> {
        if self.0.is_empty() {
            return None;
        }
        let item = self.field();
        if item.is_err() {
            self.0 = &[];
        }
        Some(item)
    }
}

fn utf8(b: &[u8]) -> Result<String, DecodeError> {
    String::from_utf8(b.to_vec()).map_err(|_| DecodeError("string is not UTF-8"))
}

/// A repeated `uint32`, packed or not.
fn push_u32s(out: &mut Vec<u32>, field: Field) -> Result<(), DecodeError> {
    match field {
        Field::Varint(v) => out.push(v as u32),
        Field::Len(b) => {
            let mut r = Reader(b);
            while !r.0.is_empty() {
                out.push(r.varint()? as u32);
            }
        }
        _ => return Err(DecodeError("bad repeated uint32")),
    }
    Ok(())
}

fn decode_layer(bytes: &[u8]) -> Result<Layer, DecodeError> {
    let mut layer = Layer {
        version: 1,
        name: String::new(),
        features: vec![],
        keys: vec![],
        values: vec![],
        extent: 4096,
    };
    for field in Reader(bytes) {
        match field? {
            (15, Field::Varint(v)) => layer.version = v as u32,
            (1, Field::Len(b)) => layer.name = utf8(b)?,
            (2, Field::Len(b)) => layer.features.push(decode_feature(b)?),
            (3, Field::Len(b)) => layer.keys.push(utf8(b)?),
            (4, Field::Len(b)) => layer.values.push(decode_value(b)?),
            (5, Field::Varint(v)) => layer.extent = v as u32,
            _ => {}
        }
    }
    Ok(layer)
}

fn decode_feature(bytes: &[u8]) -> Result<Feature, DecodeError> {
    let mut feature = Feature {
        id: 0,
        tags: vec![],
        geom_type: GeomType::Unknown,
        geometry: vec![],
    };
    for field in Reader(bytes) {
        match field? {
            (1, Field::Varint(v)) => feature.id = v,
            (2, f) => push_u32s(&mut feature.tags, f)?,
            (3, Field::Varint(v)) => feature.geom_type = GeomType::from_wire(v),
            (4, f) => push_u32s(&mut feature.geometry, f)?,
            _ => {}
        }
    }
    Ok(feature)
}

fn decode_value(bytes: &[u8]) -> Result<Value, DecodeError> {
    let mut value = Value::default();
    for field in Reader(bytes) {
        match field? {
            (1, Field::Len(b)) => value.string_value = Some(utf8(b)?),
            (2, Field::Fixed32(b)) => value.float_value = Some(f32::from_le_bytes(b)),
            (3, Field::Fixed64(b)) => value.double_value = Some(f64::from_le_bytes(b)),
            (4, Field::Varint(v)) => value.int_value = Some(v as i64),
            (5, Field::Varint(v)) => value.uint_value = Some(v),
            (6, Field::Varint(v)) => value.sint_value = Some(((v >> 1) as i64) ^ -((v & 1) as i64)),
            (7, Field::Varint(v)) => value.bool_value = Some(v != 0),
            _ => {}
        }
    }
    Ok(value)
}

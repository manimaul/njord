//! The readable JSON view of an encoded tile that the P-TE-2 sidecars (`geo/mvt/<case>.json`
//! `decoded`) and the P-TE-5 golden tiles store, ported from Kotlin `tile/TileJsonView.kt`:
//!
//! ```text
//! { "layers": [ { "name", "version", "extent",
//!                 "features": [ { "id", "type", "props": {…}, "geometry": [ [ [x, y], … ], … ] } ] } ] }
//! ```
//!
//! `geometry` is one path per point run, line or ring in tile pixels, decoded from the
//! MoveTo/LineTo/ClosePath stream; a ClosePath repeats the ring's first vertex. Props keep tag
//! order and layers keep encoded order.

use serde_json::{Map, Value, json};

use super::proto::{self, DecodeError, Tile};
use super::{CLOSE_PATH, LINE_TO, MOVE_TO};

/// Decodes `bytes` and returns the view.
pub fn decode_to_json(bytes: &[u8]) -> Result<Value, DecodeError> {
    Tile::decode(bytes).and_then(|t| tile_view(&t))
}

pub fn tile_view(tile: &Tile) -> Result<Value, DecodeError> {
    let layers = tile
        .layers
        .iter()
        .map(layer_view)
        .collect::<Result<Vec<_>, _>>()?;
    Ok(json!({ "layers": layers }))
}

fn layer_view(layer: &proto::Layer) -> Result<Value, DecodeError> {
    let mut features = Vec::with_capacity(layer.features.len());
    for feature in &layer.features {
        let mut props = Map::new();
        for pair in feature.tags.chunks(2) {
            let [k, v] = pair else {
                return Err(DecodeError("odd number of tags"));
            };
            let key = layer.keys.get(*k as usize);
            let value = layer.values.get(*v as usize);
            let (Some(key), Some(value)) = (key, value) else {
                return Err(DecodeError("tag index out of range"));
            };
            props.insert(key.clone(), value_view(value));
        }
        features.push(json!({
            "id": feature.id as i64,
            "type": feature.geom_type.name(),
            "props": props,
            "geometry": decode_geometry(&feature.geometry)?,
        }));
    }
    Ok(json!({
        "name": layer.name,
        "version": layer.version,
        "extent": layer.extent,
        "features": features,
    }))
}

/// The first field set, in Kotlin's `when` order; a non-finite double or float has no JSON form
/// and reads as `null`.
fn value_view(value: &proto::Value) -> Value {
    if let Some(s) = &value.string_value {
        Value::from(s.clone())
    } else if let Some(f) = value.float_value {
        // Through the shortest f32 text, so 0.1f reads 0.1 as Kotlin prints it, not 0.100000001…
        f.to_string()
            .parse::<f64>()
            .ok()
            .and_then(serde_json::Number::from_f64)
            .map_or(Value::Null, Value::Number)
    } else if let Some(d) = value.double_value {
        serde_json::Number::from_f64(d).map_or(Value::Null, Value::Number)
    } else if let Some(i) = value.int_value {
        Value::from(i)
    } else if let Some(u) = value.uint_value {
        Value::from(u as i64)
    } else if let Some(s) = value.sint_value {
        Value::from(s)
    } else if let Some(b) = value.bool_value {
        Value::from(b)
    } else {
        Value::Null
    }
}

/// The command stream as paths of absolute tile pixels.
pub fn decode_geometry(commands: &[u32]) -> Result<Value, DecodeError> {
    let zig_zag_decode = |n: u32| ((n >> 1) as i32) ^ -((n & 1) as i32);
    let (mut x, mut y) = (0i32, 0i32);
    let mut paths: Vec<Value> = Vec::new();
    let mut path: Vec<Value> = Vec::new();
    let mut words = commands.iter().copied();
    while let Some(word) = words.next() {
        let (id, count) = (word & 0x7, word >> 3);
        match id {
            MOVE_TO | LINE_TO => {
                for _ in 0..count {
                    if id == MOVE_TO && !path.is_empty() {
                        paths.push(Value::Array(std::mem::take(&mut path)));
                    }
                    let (Some(dx), Some(dy)) = (words.next(), words.next()) else {
                        return Err(DecodeError("truncated geometry"));
                    };
                    x = x.wrapping_add(zig_zag_decode(dx));
                    y = y.wrapping_add(zig_zag_decode(dy));
                    path.push(json!([x, y]));
                }
            }
            CLOSE_PATH => {
                if let Some(first) = path.first().cloned() {
                    path.push(first);
                }
                if !path.is_empty() {
                    paths.push(Value::Array(std::mem::take(&mut path)));
                }
            }
            _ => return Err(DecodeError("unknown geometry command")),
        }
    }
    if !path.is_empty() {
        paths.push(Value::Array(path));
    }
    Ok(Value::Array(paths))
}

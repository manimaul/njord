//! `OgrFeature.properties`: GDAL fields → JSON props, plus the two derived props ingest relies
//! on (P-IN-2): `METERS` on soundings and `MINZ`/`MAXZ` next to `SCAMIN`/`SCAMAX`.

use njord_model::JsonObject;
use serde_json::Value;

use crate::ogr::{FeatureRef, Geometry, Layer};
use njord_geo::tile_system::{TILE_SYSTEM, TWEAK_ZOOM_TO_SCALE_BY};

/// One feature as ingest inserts it: GDAL-mapped props and the geometry as read.
#[derive(Clone, Debug)]
pub struct Feature {
    /// Every set, non-null field in GDAL field order (strings and dates as strings, booleans,
    /// numbers, arrays), with:
    /// - `METERS` first on `SOUNDG` features: the sounding's Z;
    /// - `MINZ` just before `SCAMIN` and `MAXZ` just before `SCAMAX`: the scale's zoom at the
    ///   centre latitude of the feature's envelope (0 without a geometry).
    pub props: JsonObject,
    pub geometry: Option<Geometry>,
}

pub(crate) fn read_layer(layer: &Layer<'_>, name: &str) -> Vec<Feature> {
    let is_sounding = name == "SOUNDG";
    layer.map_features(|f| read(&f, is_sounding))
}

fn read(f: &FeatureRef<'_>, is_sounding: bool) -> Feature {
    let geometry = f.geometry();
    let mut props = JsonObject::new();
    if is_sounding && let Some(g) = &geometry {
        props.insert("METERS".into(), Value::from(g.first_z()));
    }
    let latitude = || {
        geometry.as_ref().map_or(0.0, |g| {
            let e = g.envelope();
            (e.south + e.north) / 2.0
        })
    };
    for field in f.fields() {
        let zoom_key = match field.name.as_str() {
            "SCAMIN" => Some("MINZ"),
            "SCAMAX" => Some("MAXZ"),
            _ => None,
        };
        if let Some(key) = zoom_key
            && let Some(scale) = int_value(&field.value)
        {
            let zoom =
                TILE_SYSTEM.scale_to_zoom_int(f64::from(scale), latitude(), TWEAK_ZOOM_TO_SCALE_BY);
            props.insert(key.into(), Value::from(zoom));
        }
        props.insert(field.name, field.value);
    }
    Feature { props, geometry }
}

/// Kotlin `JsonPrimitive.intOrNull`: the primitive's content parsed as an `Int`, so `12`, `"12"`
/// and `"+12"` give 12 while `12.0`, `true` and out-of-range values give `None`.
pub(crate) fn int_value(v: &Value) -> Option<i32> {
    match v {
        Value::Number(n) if !n.is_f64() => n.as_i64().and_then(|i| i32::try_from(i).ok()),
        Value::String(s) => s.parse().ok(),
        _ => None,
    }
}

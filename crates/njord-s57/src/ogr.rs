//! The GDAL layer: every GDAL call in the crate goes through here, on the georust `gdal` crate.
//!
//! Nothing outside this module touches a `gdal` type.

use std::path::Path;
use std::sync::Once;

use gdal::cpl::CslStringList;
use gdal::vector::{FieldValue, LayerAccess};
use gdal::{Dataset as GdalDataset, DatasetOptions, GdalOpenFlags};
use gdal_sys::{OGRFieldSubType, OGRFieldType};
use serde_json::Value;

use crate::{Envelope, Error, S57_OPTIONS};

static INIT: Once = Once::new();

/// Sets `OGR_S57_OPTIONS` once per process, before the first dataset is
/// opened (the `gdal` crate registers the drivers itself).
pub(crate) fn init() {
    INIT.call_once(|| {
        gdal::config::set_config_option("OGR_S57_OPTIONS", S57_OPTIONS)
            .expect("set OGR_S57_OPTIONS");
    });
}

pub(crate) fn version() -> String {
    gdal::version::version_info("RELEASE_NAME")
}

/// An open GDAL vector dataset. GDAL datasets are not thread-safe: `gdal::Dataset` may move to
/// another thread (`Send`) but never be shared between threads (`!Sync`).
pub(crate) struct Dataset(GdalDataset);

impl Dataset {
    pub(crate) fn open(path: &Path, driver: &str) -> Result<Dataset, Error> {
        init();
        let options = DatasetOptions {
            open_flags: GdalOpenFlags::GDAL_OF_VECTOR,
            allowed_drivers: Some(&[driver]),
            ..DatasetOptions::default()
        };
        GdalDataset::open_ex(path, options)
            .map(Dataset)
            .map_err(|e| {
                Error(format!(
                    "failed to open {} with {driver}: {e}",
                    path.display()
                ))
            })
    }

    pub(crate) fn layer_names(&self) -> Vec<String> {
        self.0.layers().map(|l| l.name()).collect()
    }

    pub(crate) fn layer(&self, name: &str) -> Option<Layer<'_>> {
        self.0.layer_by_name(name).ok().map(Layer)
    }
}

/// A layer borrowed from its [`Dataset`].
pub(crate) struct Layer<'a>(gdal::vector::Layer<'a>);

/// How [`FeatureRef::fields`] reads one field of a layer.
#[derive(Clone, Copy)]
enum FieldKind {
    /// Integers, reals, strings and their lists, through `FieldValue`.
    Plain,
    /// `OFTInteger` with the `OFSTBoolean` subtype: a JSON boolean.
    Boolean,
    /// Every other type (dates, times, binary): GDAL's string form, `OGR_F_GetFieldAsString`.
    AsString,
}

impl Layer<'_> {
    /// `OGR_L_GetFeatureCount(force = TRUE)`.
    pub(crate) fn feature_count(&self) -> u64 {
        self.0.feature_count()
    }

    /// Reads every feature from the start, handing `f` each one's fields and geometry.
    pub(crate) fn map_features<T>(&mut self, mut f: impl FnMut(FeatureRef<'_>) -> T) -> Vec<T> {
        let kinds = self.field_kinds();
        self.0.reset_feature_reading();
        self.0
            .features()
            .map(|feature| {
                f(FeatureRef {
                    feature: &feature,
                    kinds: &kinds,
                })
            })
            .collect()
    }

    fn field_kinds(&self) -> Vec<FieldKind> {
        let defn = self.0.defn();
        defn.fields()
            .enumerate()
            .map(|(i, field)| match field.field_type() {
                OGRFieldType::OFTInteger if is_boolean(defn, i) => FieldKind::Boolean,
                OGRFieldType::OFTInteger
                | OGRFieldType::OFTIntegerList
                | OGRFieldType::OFTInteger64
                | OGRFieldType::OFTInteger64List
                | OGRFieldType::OFTReal
                | OGRFieldType::OFTRealList
                | OGRFieldType::OFTString
                | OGRFieldType::OFTStringList => FieldKind::Plain,
                _ => FieldKind::AsString,
            })
            .collect()
    }
}

/// `OGR_Fld_GetSubType(defn) == OFSTBoolean`. The `gdal` crate exposes no field subtype, so
/// this is the one raw `gdal-sys` call in the crate.
fn is_boolean(defn: &gdal::vector::Defn, i: usize) -> bool {
    // SAFETY: `defn` is the live definition of a layer borrowed for this call and `i` is one of
    // its field indices; the returned field definition is owned by `defn` and only read here.
    unsafe {
        let field = gdal_sys::OGR_FD_GetFieldDefn(defn.c_defn(), i as i32);
        !field.is_null() && gdal_sys::OGR_Fld_GetSubType(field) == OGRFieldSubType::OFSTBoolean
    }
}

pub(crate) struct FeatureRef<'a> {
    feature: &'a gdal::vector::Feature<'a>,
    kinds: &'a [FieldKind],
}

/// One field of a feature, mapped to JSON.
pub(crate) struct Field {
    pub(crate) name: String,
    pub(crate) value: Value,
}

impl FeatureRef<'_> {
    /// A copy of the feature's geometry, if it has one.
    pub(crate) fn geometry(&self) -> Option<Geometry> {
        self.feature.geometry().cloned().map(Geometry)
    }

    /// Every field that is set and not null, in field order: strings and dates as strings,
    /// `OFSTBoolean` integers as booleans, numbers and lists as JSON numbers and arrays.
    pub(crate) fn fields(&self) -> Vec<Field> {
        self.feature
            .fields()
            .zip(self.kinds)
            .enumerate()
            .filter_map(|(i, ((name, value), kind))| {
                let value = value?;
                let value = match kind {
                    FieldKind::Plain => plain(value),
                    FieldKind::Boolean => match value {
                        FieldValue::IntegerValue(v) => Value::Bool(v != 0),
                        other => plain(other),
                    },
                    FieldKind::AsString => Value::String(
                        self.feature
                            .field_as_string(i)
                            .ok()
                            .flatten()
                            .unwrap_or_default(),
                    ),
                };
                Some(Field { name, value })
            })
            .collect()
    }
}

fn plain(value: FieldValue) -> Value {
    match value {
        FieldValue::IntegerValue(v) => Value::from(v),
        FieldValue::IntegerListValue(v) => Value::from(v),
        FieldValue::Integer64Value(v) => Value::from(v),
        FieldValue::Integer64ListValue(v) => Value::from(v),
        FieldValue::StringValue(v) => Value::String(v),
        FieldValue::StringListValue(v) => Value::from(v),
        FieldValue::RealValue(v) => real(v),
        FieldValue::RealListValue(v) => Value::Array(v.into_iter().map(real).collect()),
        // Only reached for `FieldKind::AsString` fields, which never come through here.
        FieldValue::DateValue(d) => Value::String(d.to_string()),
        FieldValue::DateTimeValue(d) => Value::String(d.to_string()),
    }
}

/// A non-finite double has no JSON form; it becomes `null`.
fn real(v: f64) -> Value {
    serde_json::Number::from_f64(v).map_or(Value::Null, Value::Number)
}

/// An owned OGR geometry, in WGS84 for both S-57 cells and Natural Earth shapefiles.
///
/// It is opaque so that GDAL stays inside this crate: ingest takes [`Geometry::wkb`] to PostGIS.
#[derive(Clone)]
pub struct Geometry(gdal::vector::Geometry);

// SAFETY: every `Geometry` here owns its OGR geometry (a clone, a parse result or a repair
// result) and belongs to no dataset or feature, so moving it to another thread is sound; GEOS
// calls on distinct geometries are reentrant. `gdal::vector::Geometry` is `!Send` only because it
// may also borrow a feature's geometry, which this type never does.
unsafe impl Send for Geometry {}

impl Geometry {
    /// Parses WKT with `OGR_G_CreateFromWkt`, without a spatial reference.
    ///
    /// # Errors
    ///
    /// Returns an [`Error`] if GDAL cannot parse `wkt`.
    pub fn from_wkt(wkt: &str) -> Result<Geometry, Error> {
        init();
        gdal::vector::Geometry::from_wkt(wkt)
            .map(Geometry)
            .map_err(|e| Error(format!("invalid WKT {wkt}: {e}")))
    }

    /// Whether the geometry is valid (`OGR_G_IsValid`, GEOS validity).
    pub fn is_valid(&self) -> bool {
        self.0.is_valid()
    }

    /// The geometry itself when valid, otherwise GDAL's `OGR_G_MakeValid` repair.
    ///
    /// Returns `None` when GDAL cannot repair it; ingest then skips the feature.
    pub fn make_valid(self) -> Option<Geometry> {
        if self.is_valid() {
            Some(self)
        } else {
            self.0.make_valid(&CslStringList::new()).ok().map(Geometry)
        }
    }

    /// The bounding box (`OGR_G_GetEnvelope`).
    pub fn envelope(&self) -> Envelope {
        let e = self.0.envelope();
        Envelope {
            west: e.MinX,
            south: e.MinY,
            east: e.MaxX,
            north: e.MaxY,
        }
    }

    /// `OGR_G_GetZ(geom, 0)`: the first point's Z (0 for 2D).
    pub(crate) fn first_z(&self) -> f64 {
        self.0.get_point(0).2
    }

    /// The geometry as WKB (`OGR_G_ExportToWkb(wkbNDR)`): little-endian, with OGC (pre-ISO) type
    /// codes for 2.5D.
    ///
    /// # Panics
    ///
    /// Panics if GDAL fails to export the geometry.
    pub fn wkb(&self) -> Vec<u8> {
        self.0.wkb().expect("OGR_G_ExportToWkb")
    }

    /// The geometry as WKT (`OGR_G_ExportToWkt`).
    ///
    /// # Panics
    ///
    /// Panics if GDAL fails to export the geometry.
    pub fn wkt(&self) -> String {
        self.0.wkt().expect("OGR_G_ExportToWkt")
    }

    /// The geometry as GeoJSON (`OGR_G_ExportToJson`, parsed).
    ///
    /// # Panics
    ///
    /// Panics if GDAL fails to export the geometry or its GeoJSON does not parse.
    pub fn geojson(&self) -> geojson::Geometry {
        let s = self.0.json().expect("OGR_G_ExportToJson");
        serde_json::from_str(&s).unwrap_or_else(|e| panic!("GDAL GeoJSON {s}: {e}"))
    }
}

impl std::fmt::Debug for Geometry {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.wkt())
    }
}

//! The thin unsafe layer over `gdal-sys`: every GDAL call in the crate goes through here
//! (Kotlin `Gdal.kt`, `GdalDataset.kt`, the reading half of `OgrLayer.kt` and the parts of
//! `OgrGeometry.kt` ingest uses). Nothing outside this module touches a raw GDAL handle.

use std::ffi::{CStr, CString, c_char};
use std::marker::PhantomData;
use std::path::Path;
use std::ptr;
use std::sync::Once;

use gdal_sys::{
    GDALDatasetH, OGREnvelope, OGRFeatureH, OGRFieldSubType, OGRFieldType, OGRGeometryH, OGRLayerH,
    OGRwkbByteOrder,
};
use serde_json::Value;

use crate::{Envelope, Error, S57_OPTIONS};

/// `GDAL_OF_VECTOR` (a `#define`, so not in the generated bindings).
const GDAL_OF_VECTOR: u32 = 0x04;

static INIT: Once = Once::new();

/// `Gdal.initialize()`: registers every driver and sets `OGR_S57_OPTIONS`, once per process.
pub(crate) fn init() {
    INIT.call_once(|| unsafe {
        gdal_sys::GDALAllRegister();
        let key = CString::new("OGR_S57_OPTIONS").unwrap();
        let value = CString::new(S57_OPTIONS).unwrap();
        gdal_sys::CPLSetConfigOption(key.as_ptr(), value.as_ptr());
    });
}

pub(crate) fn version() -> String {
    init();
    let key = CString::new("RELEASE_NAME").unwrap();
    unsafe { string(gdal_sys::GDALVersionInfo(key.as_ptr())) }.unwrap_or_default()
}

unsafe fn string(p: *const c_char) -> Option<String> {
    (!p.is_null()).then(|| unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned())
}

fn last_error() -> String {
    unsafe { string(gdal_sys::CPLGetLastErrorMsg()) }.unwrap_or_default()
}

/// An open GDAL vector dataset. GDAL datasets are not thread-safe: the handle may move to
/// another thread (`Send`) but never be shared between threads (`!Sync`).
pub(crate) struct Dataset {
    ptr: GDALDatasetH,
    _not_sync: PhantomData<std::cell::Cell<()>>,
}

// SAFETY: a GDAL dataset may be used from any one thread at a time; `!Sync` forbids concurrent use.
unsafe impl Send for Dataset {}

impl Dataset {
    pub(crate) fn open(path: &Path, driver: &str) -> Result<Dataset, Error> {
        init();
        let c_path = CString::new(path.to_string_lossy().as_bytes())
            .map_err(|_| Error(format!("{}: path contains NUL", path.display())))?;
        let c_driver = CString::new(driver).unwrap();
        let drivers = [c_driver.as_ptr(), ptr::null()];
        let ptr = unsafe {
            gdal_sys::GDALOpenEx(
                c_path.as_ptr(),
                GDAL_OF_VECTOR,
                drivers.as_ptr(),
                ptr::null(),
                ptr::null(),
            )
        };
        if ptr.is_null() {
            return Err(Error(format!(
                "failed to open {} with {driver}: {}",
                path.display(),
                last_error()
            )));
        }
        Ok(Dataset {
            ptr,
            _not_sync: PhantomData,
        })
    }

    pub(crate) fn layer_names(&self) -> Vec<String> {
        let count = unsafe { gdal_sys::GDALDatasetGetLayerCount(self.ptr) };
        (0..count)
            .filter_map(|i| {
                let layer = unsafe { gdal_sys::GDALDatasetGetLayer(self.ptr, i) };
                (!layer.is_null())
                    .then(|| unsafe { string(gdal_sys::OGR_L_GetName(layer)) })
                    .flatten()
            })
            .collect()
    }

    pub(crate) fn layer(&self, name: &str) -> Option<Layer<'_>> {
        let c_name = CString::new(name).ok()?;
        let ptr = unsafe { gdal_sys::GDALDatasetGetLayerByName(self.ptr, c_name.as_ptr()) };
        (!ptr.is_null()).then_some(Layer {
            ptr,
            _dataset: PhantomData,
        })
    }
}

impl Drop for Dataset {
    fn drop(&mut self) {
        unsafe { gdal_sys::GDALClose(self.ptr) };
    }
}

/// A layer borrowed from its [`Dataset`]; GDAL frees it with the dataset.
pub(crate) struct Layer<'a> {
    ptr: OGRLayerH,
    _dataset: PhantomData<&'a Dataset>,
}

impl Layer<'_> {
    /// `OGR_L_GetFeatureCount(force = TRUE)`.
    pub(crate) fn feature_count(&self) -> u64 {
        unsafe { gdal_sys::OGR_L_GetFeatureCount(self.ptr, 1) }.max(0) as u64
    }

    /// Reads every feature from the start (`OgrLayer.mapFeature`), handing `f` each one's
    /// fields and geometry; the feature is destroyed when `f` returns.
    pub(crate) fn map_features<T>(&self, mut f: impl FnMut(FeatureRef<'_>) -> T) -> Vec<T> {
        unsafe { gdal_sys::OGR_L_ResetReading(self.ptr) };
        let mut out = Vec::new();
        loop {
            let ptr = unsafe { gdal_sys::OGR_L_GetNextFeature(self.ptr) };
            if ptr.is_null() {
                break;
            }
            let feature = OwnedFeature(ptr);
            out.push(f(FeatureRef {
                ptr: feature.0,
                _feature: PhantomData,
            }));
        }
        out
    }
}

struct OwnedFeature(OGRFeatureH);

impl Drop for OwnedFeature {
    fn drop(&mut self) {
        unsafe { gdal_sys::OGR_F_Destroy(self.0) };
    }
}

pub(crate) struct FeatureRef<'a> {
    ptr: OGRFeatureH,
    _feature: PhantomData<&'a OwnedFeature>,
}

/// One field of a feature as `OgrFeature.properties` maps it to JSON.
pub(crate) struct Field {
    pub(crate) name: String,
    pub(crate) value: Value,
}

impl FeatureRef<'_> {
    /// A copy of the feature's geometry, if it has one.
    pub(crate) fn geometry(&self) -> Option<Geometry> {
        let geom = unsafe { gdal_sys::OGR_F_GetGeometryRef(self.ptr) };
        if geom.is_null() {
            return None;
        }
        Geometry::from_owned(unsafe { gdal_sys::OGR_G_Clone(geom) })
    }

    /// Every field that is set and not null, in field order, typed as `OgrFeature.properties`
    /// types them: strings and dates as strings, `OFSTBoolean` integers as booleans, numbers and
    /// lists as JSON numbers and arrays.
    pub(crate) fn fields(&self) -> Vec<Field> {
        let count = unsafe { gdal_sys::OGR_F_GetFieldCount(self.ptr) };
        (0..count).filter_map(|i| self.field(i)).collect()
    }

    fn field(&self, i: i32) -> Option<Field> {
        let p = self.ptr;
        unsafe {
            if gdal_sys::OGR_F_IsFieldSetAndNotNull(p, i) != 1 {
                return None;
            }
            let defn = gdal_sys::OGR_F_GetFieldDefnRef(p, i);
            if defn.is_null() {
                return None;
            }
            let name = string(gdal_sys::OGR_Fld_GetNameRef(defn))?;
            let as_string = || {
                Value::String(string(gdal_sys::OGR_F_GetFieldAsString(p, i)).unwrap_or_default())
            };
            let value = match gdal_sys::OGR_Fld_GetType(defn) {
                OGRFieldType::OFTString => as_string(),
                OGRFieldType::OFTStringList => {
                    let list = gdal_sys::OGR_F_GetFieldAsStringList(p, i);
                    let mut items = Vec::new();
                    if !list.is_null() {
                        let mut j = 0;
                        while let Some(s) = string(*list.add(j)) {
                            items.push(Value::String(s));
                            j += 1;
                        }
                    }
                    Value::Array(items)
                }
                OGRFieldType::OFTInteger => {
                    let v = gdal_sys::OGR_F_GetFieldAsInteger(p, i);
                    if gdal_sys::OGR_Fld_GetSubType(defn) == OGRFieldSubType::OFSTBoolean {
                        Value::Bool(v != 0)
                    } else {
                        Value::from(v)
                    }
                }
                OGRFieldType::OFTIntegerList => {
                    let mut n = 0;
                    let arr = gdal_sys::OGR_F_GetFieldAsIntegerList(p, i, &mut n);
                    list(arr, n, |v| Value::from(*v))
                }
                OGRFieldType::OFTReal => real(gdal_sys::OGR_F_GetFieldAsDouble(p, i)),
                OGRFieldType::OFTRealList => {
                    let mut n = 0;
                    let arr = gdal_sys::OGR_F_GetFieldAsDoubleList(p, i, &mut n);
                    list(arr, n, |v| real(*v))
                }
                OGRFieldType::OFTInteger64 => {
                    Value::from(gdal_sys::OGR_F_GetFieldAsInteger64(p, i))
                }
                OGRFieldType::OFTInteger64List => {
                    let mut n = 0;
                    let arr = gdal_sys::OGR_F_GetFieldAsInteger64List(p, i, &mut n);
                    list(arr, n, |v| Value::from(*v))
                }
                _ => as_string(),
            };
            Some(Field { name, value })
        }
    }
}

/// A non-finite double has no JSON form; it becomes `null`.
fn real(v: f64) -> Value {
    serde_json::Number::from_f64(v).map_or(Value::Null, Value::Number)
}

unsafe fn list<T>(arr: *const T, n: i32, f: impl Fn(&T) -> Value) -> Value {
    if arr.is_null() || n <= 0 {
        return Value::Array(Vec::new());
    }
    Value::Array(
        unsafe { std::slice::from_raw_parts(arr, n as usize) }
            .iter()
            .map(f)
            .collect(),
    )
}

/// An owned OGR geometry, in WGS84 for both S-57 cells and Natural Earth shapefiles. Opaque so
/// that GDAL stays inside this crate: ingest takes [`Geometry::wkb`] to PostGIS.
pub struct Geometry(OGRGeometryH);

// SAFETY: an owned OGR geometry belongs to no dataset; GEOS calls on it are reentrant.
unsafe impl Send for Geometry {}

impl Geometry {
    fn from_owned(ptr: OGRGeometryH) -> Option<Geometry> {
        (!ptr.is_null()).then_some(Geometry(ptr))
    }

    /// `OGR_G_CreateFromWkt`, without a spatial reference.
    pub fn from_wkt(wkt: &str) -> Result<Geometry, Error> {
        init();
        let c_wkt = CString::new(wkt).map_err(|_| Error("WKT contains NUL".into()))?;
        let mut cursor = c_wkt.as_ptr() as *mut c_char;
        let mut geom: OGRGeometryH = ptr::null_mut();
        let err = unsafe { gdal_sys::OGR_G_CreateFromWkt(&mut cursor, ptr::null_mut(), &mut geom) };
        match Geometry::from_owned(geom) {
            Some(g) if err == 0 => Ok(g),
            _ => Err(Error(format!("invalid WKT {wkt}: {}", last_error()))),
        }
    }

    /// `OGR_G_IsValid` (GEOS validity).
    pub fn is_valid(&self) -> bool {
        unsafe { gdal_sys::OGR_G_IsValid(self.0) == 1 }
    }

    /// `OgrGeometry.makeValid()`: the geometry itself when valid, otherwise GDAL's
    /// `OGR_G_MakeValid` repair, or `None` when GDAL cannot repair it (ingest then skips the
    /// feature).
    pub fn make_valid(self) -> Option<Geometry> {
        if self.is_valid() {
            Some(self)
        } else {
            Geometry::from_owned(unsafe { gdal_sys::OGR_G_MakeValid(self.0) })
        }
    }

    pub fn envelope(&self) -> Envelope {
        let mut e = OGREnvelope {
            MinX: 0.0,
            MaxX: 0.0,
            MinY: 0.0,
            MaxY: 0.0,
        };
        unsafe { gdal_sys::OGR_G_GetEnvelope(self.0, &mut e) };
        Envelope {
            west: e.MinX,
            south: e.MinY,
            east: e.MaxX,
            north: e.MaxY,
        }
    }

    /// `OGR_G_GetZ(geom, 0)`: the first point's Z (0 for 2D).
    pub(crate) fn first_z(&self) -> f64 {
        unsafe { gdal_sys::OGR_G_GetZ(self.0, 0) }
    }

    /// `OGR_G_ExportToWkb(wkbNDR)`: little-endian, OGC (pre-ISO) type codes for 2.5D.
    pub fn wkb(&self) -> Vec<u8> {
        let size = unsafe { gdal_sys::OGR_G_WkbSize(self.0) }.max(0) as usize;
        let mut buf = vec![0u8; size];
        let err = unsafe {
            gdal_sys::OGR_G_ExportToWkb(self.0, OGRwkbByteOrder::wkbNDR, buf.as_mut_ptr())
        };
        assert_eq!(err, 0, "OGR_G_ExportToWkb failed: {}", last_error());
        buf
    }

    /// `OGR_G_ExportToWkt`.
    pub fn wkt(&self) -> String {
        let mut out: *mut c_char = ptr::null_mut();
        let err = unsafe { gdal_sys::OGR_G_ExportToWkt(self.0, &mut out) };
        assert_eq!(err, 0, "OGR_G_ExportToWkt failed: {}", last_error());
        let s = unsafe { string(out) }.unwrap_or_default();
        unsafe { gdal_sys::VSIFree(out.cast()) };
        s
    }

    /// `OGR_G_ExportToJson`, parsed (`OgrGeometry.geoJson()`).
    pub fn geojson(&self) -> geojson::Geometry {
        let p = unsafe { gdal_sys::OGR_G_ExportToJson(self.0) };
        assert!(!p.is_null(), "OGR_G_ExportToJson failed: {}", last_error());
        let s = unsafe { string(p) }.unwrap_or_default();
        unsafe { gdal_sys::VSIFree(p.cast()) };
        serde_json::from_str(&s).unwrap_or_else(|e| panic!("GDAL GeoJSON {s}: {e}"))
    }
}

impl Clone for Geometry {
    fn clone(&self) -> Geometry {
        Geometry::from_owned(unsafe { gdal_sys::OGR_G_Clone(self.0) }).expect("OGR_G_Clone")
    }
}

impl Drop for Geometry {
    fn drop(&mut self) {
        unsafe { gdal_sys::OGR_G_DestroyGeometry(self.0) };
    }
}

impl std::fmt::Debug for Geometry {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.wkt())
    }
}

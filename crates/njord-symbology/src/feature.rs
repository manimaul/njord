//! The feature `preTileEncode` mutates (`ChartFeature` in `Chart.kt`) and the symbology prop
//! setters from `Layerable.kt`.

use njord_model::{Color, JsonObject, Sprite};
use serde_json::Value;

/// The geometry primitive of a feature, as the fixtures name it (`geometry_type`).
///
/// No Kotlin `preTileEncode` reads the geometry, so this is carried for callers and tests only.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum GeometryType {
    Point,
    MultiPoint,
    LineString,
    MultiLineString,
    Polygon,
    MultiPolygon,
    GeometryCollection,
}

impl GeometryType {
    /// Parses the fixtures' upper-case OGR names (`POINT`, `LINESTRING`, …); `NONE` and anything
    /// unknown is `None`.
    pub fn from_fixture_name(name: &str) -> Option<GeometryType> {
        Some(match name {
            "POINT" => GeometryType::Point,
            "MULTIPOINT" => GeometryType::MultiPoint,
            "LINESTRING" => GeometryType::LineString,
            "MULTILINESTRING" => GeometryType::MultiLineString,
            "POLYGON" => GeometryType::Polygon,
            "MULTIPOLYGON" => GeometryType::MultiPolygon,
            "GEOMETRYCOLLECTION" => GeometryType::GeometryCollection,
            _ => return None,
        })
    }
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct ChartFeature {
    /// The S-57 object class acronym (`BOYLAT`), which selects the layerable by key.
    pub layer: String,
    pub geometry_type: Option<GeometryType>,
    /// S-57 attributes as GDAL maps them; `preTileEncode` adds the symbology props in place, and
    /// insertion order is kept (Kotlin's `LinkedHashMap`).
    pub props: JsonObject,
    /// `associatedLayerNames`: the object classes of the features that share this feature's
    /// position, looked up by the database when the tile is built (P-DB-4). Only TOPMAR reads
    /// it, to tell a floating platform (a buoy) from a rigid one (a beacon); the fixtures call it
    /// `assoc`.
    pub associated_layer_names: Vec<String>,
}

impl ChartFeature {
    pub fn new(layer: impl Into<String>, props: JsonObject) -> Self {
        ChartFeature {
            layer: layer.into(),
            props,
            ..Default::default()
        }
    }

    fn set(&mut self, key: impl Into<String>, value: impl Into<Value>) {
        self.props.insert(key.into(), value.into());
    }

    /// `excludeAreaPointSymbol`: `EA`, which keeps `areaLayerWithPointSymbol` off the area.
    pub fn exclude_area_point_symbol(&mut self) {
        self.set("EA", true);
    }

    /// `pointSymbol`: `SY`.
    pub fn point_symbol(&mut self, symbol: Sprite) {
        self.set("SY", symbol.as_str());
    }

    /// `pointSymbol(symbol, num)`: `SY<num>`.
    pub fn point_symbol_n(&mut self, symbol: Sprite, num: i32) {
        self.set(format!("SY{num}"), symbol.as_str());
    }

    /// `areaPattern`: `AP`.
    pub fn area_pattern(&mut self, pattern: Sprite) {
        self.set("AP", pattern.as_str());
    }

    /// `areaColor`: `AC`.
    pub fn area_color(&mut self, color: Color) {
        self.set("AC", color.as_str());
    }

    /// `linePattern`: `LP`.
    pub fn line_pattern(&mut self, pattern: Sprite) {
        self.set("LP", pattern.as_str());
    }

    /// `lineColor`: `LC`.
    pub fn line_color(&mut self, color: Color) {
        self.set("LC", color.as_str());
    }
}

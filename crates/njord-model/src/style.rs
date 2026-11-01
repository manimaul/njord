//! The MapLibre style served by `GET /v1/style/{depth}/{theme}`.
//!
//! Unlike the rest of the API, the style omits every `Option` that is `None`; defaulted fields
//! are still always written. The golden fixtures are `testdata/symbology/style/` and
//! `conformance/golden/api_02_style/`.

use geojson::GeoJson;
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::IndexMap;
use crate::string_enum;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Style {
    #[serde(rename = "glyphs")]
    pub glyphs_url: String,
    pub layers: Vec<Layer>,
    pub name: String,
    pub sources: IndexMap<String, Source>,
    #[serde(rename = "sprite")]
    pub sprite_url: String,
    pub version: i32,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Layer {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub paint: Option<Paint>,
    #[serde(rename = "type")]
    pub kind: LayerType,
    /// [`Source::SENC`] for every chart layer; `None` only for the background layer.
    ///
    /// A missing member stays `None` rather than defaulting to [`Source::SENC`], so a style
    /// round-trips unchanged.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
    #[serde(
        rename = "source-layer",
        default,
        skip_serializing_if = "Option::is_none"
    )]
    pub source_layer: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub filter: Option<Value>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub layout: Option<Layout>,
    #[serde(rename = "minzoom", default, skip_serializing_if = "Option::is_none")]
    pub min_zoom: Option<i32>,
    #[serde(rename = "maxzoom", default, skip_serializing_if = "Option::is_none")]
    pub max_zoom_exclusive: Option<i32>,
}

/// Declares a struct whose fields are all optional, omitted when `None`, with explicit wire
/// names (MapLibre's kebab-case property names).
macro_rules! style_properties {
    ($(#[$meta:meta])* pub struct $name:ident { $( $(#[$fmeta:meta])* $field:ident : $ty:ty = $wire:literal ),+ $(,)? }) => {
        $(#[$meta])*
        #[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
        pub struct $name {
            $(
                $(#[$fmeta])*
                #[serde(rename = $wire, default, skip_serializing_if = "Option::is_none")]
                pub $field: Option<$ty>,
            )+
        }
    };
}

style_properties! {
    pub struct Paint {
        text_color: Value = "text-color",
        text_halo_color: String = "text-halo-color",
        text_halo_width: f32 = "text-halo-width",
        background_color: String = "background-color",
        background_opacity: i32 = "background-opacity",
        fill_color: Value = "fill-color",
        fill_opacity: f32 = "fill-opacity",
        /// A sprite name or an expression.
        fill_pattern: Value = "fill-pattern",
        line_color: Value = "line-color",
        line_width: f32 = "line-width",
        line_dash_array: Vec<f32> = "line-dasharray",
        /// A sprite name or an expression.
        line_pattern: Value = "line-pattern",
        circle_opacity: f32 = "circle-opacity",
        circle_color: Value = "circle-color",
        circle_stroke_color: Value = "circle-stroke-color",
        circle_radius: f32 = "circle-radius",
        circle_stroke_width: f32 = "circle-stroke-width",
    }
}

style_properties! {
    pub struct Layout {
        /// A sprite name or an expression.
        icon_image: Value = "icon-image",
        icon_anchor: Anchor = "icon-anchor",
        icon_text_fit: IconTextFit = "icon-text-fit",
        /// top, right, bottom, left: extra area around the text box the fitted icon is scaled to.
        icon_text_fit_padding: Vec<f32> = "icon-text-fit-padding",
        icon_allow_overlap: bool = "icon-allow-overlap",
        icon_ignore_placement: bool = "icon-ignore-placement",
        icon_keep_upright: bool = "icon-keep-upright",
        icon_rotate: Value = "icon-rotate",
        icon_size: f32 = "icon-size",
        symbol_spacing: f32 = "symbol-spacing",
        icon_rotation_alignment: IconRotationAlignment = "icon-rotation-alignment",
        text_font: Vec<Font> = "text-font",
        text_anchor: Anchor = "text-anchor",
        text_justify: TextJustify = "text-justify",
        text_field: Value = "text-field",
        text_offset: Value = "text-offset",
        text_optional: bool = "text-optional",
        line_join: LineJoin = "line-join",
        line_cap: LineCap = "line-cap",
        /// Positive values are right and down, negative values left and up.
        icon_offset: Value = "icon-offset",
        text_allow_overlap: bool = "text-allow-overlap",
        text_ignore_placement: bool = "text-ignore-placement",
        text_max_width_ems: f32 = "text-max-width",
        text_size: f32 = "text-size",
        text_padding: f32 = "text-padding",
        symbol_placement: Placement = "symbol-placement",
        symbol_sort_key: Value = "symbol-sort-key",
    }
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct Source {
    #[serde(rename = "type", default)]
    pub kind: SourceType,
    /// The TileJSON URL, e.g. `https://localhost:9000/v1/tile_json`.
    #[serde(rename = "url", default, skip_serializing_if = "Option::is_none")]
    pub tile_json_url: Option<String>,
    #[serde(rename = "promoteId", default, skip_serializing_if = "Option::is_none")]
    pub promote_id: Option<String>,
    /// Inline data of a `geojson` source (no served style uses one yet).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub data: Option<GeoJson>,
}

impl Source {
    /// The id of the chart vector tile source.
    pub const SENC: &'static str = "src_senc";
}

string_enum! {
    #[derive(Default)]
    pub enum SourceType {
        #[default]
        Vector = "vector",
        GeoJson = "geojson",
    }
}

string_enum! {
    pub enum IconRotationAlignment {
        Map = "map",
        Viewport = "viewport",
        Auto = "auto",
    }
}

string_enum! {
    /// Scales an icon to the text drawn on the same symbol: a stretchable sprite becomes a text
    /// background or underline.
    pub enum IconTextFit {
        None = "none",
        Width = "width",
        Height = "height",
        Both = "both",
    }
}

string_enum! {
    pub enum LineCap {
        Butt = "butt",
        Round = "round",
        Square = "square",
    }
}

string_enum! {
    pub enum LineJoin {
        Bevel = "bevel",
        Round = "round",
        Miter = "miter",
    }
}

string_enum! {
    pub enum Placement {
        Point = "point",
        Line = "line",
        LineCenter = "line-center",
    }
}

string_enum! {
    pub enum TextJustify {
        Auto = "auto",
        Left = "left",
        Center = "center",
        Right = "right",
    }
}

string_enum! {
    pub enum Anchor {
        Center = "center",
        Left = "left",
        Right = "right",
        BottomLeft = "bottom-left",
        BottomRight = "bottom-right",
        Bottom = "bottom",
        Top = "top",
        TopLeft = "top-left",
        TopRight = "top-right",
    }
}

string_enum! {
    pub enum Font {
        RobotoBold = "Roboto Bold",
        RobotoItalic = "Roboto Italic",
        RobotoMedium = "Roboto Medium",
        RobotoRegular = "Roboto Regular",
    }
}

string_enum! {
    /// <https://maplibre.org/maplibre-style-spec/layers/>
    pub enum LayerType {
        Background = "background",
        Fill = "fill",
        Line = "line",
        Symbol = "symbol",
        Circle = "circle",
        Heatmap = "heatmap",
        FillExtrusion = "fill-extrusion",
        Raster = "raster",
        Hillshade = "hillshade",
        Sky = "sky",
    }
}

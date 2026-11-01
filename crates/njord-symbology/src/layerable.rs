//! The `Layerable` contract and its layer helpers.
//!
//! A [`Layerable`] is one rule per S-57 object class that (a) adds symbology props to a feature
//! before it is encoded into a tile ([`Layerable::pre_tile_encode`]) and (b) contributes the
//! MapLibre layers that draw those props ([`Layerable::layers`]). The trait carries the
//! behaviour, while the key and source layer it is registered under live in the
//! [`LayerFactory`](crate::LayerFactory) entry, so one type can be registered twice (`Lokbsn` as
//! `LOKBSN` and `lokbsn`) and a variant that only changes its source layer (the Natural Earth
//! `land` layer drawn by `Lndare`) is a registration, not a type.
//!
//! The [`LayerBuilder`] helpers take their required arguments positionally and the optional ones
//! as a `…Opts` struct with a [`Default`], so a centred point symbol reads
//! `b.point_layer_from_symbol(PointSymbolOpts { anchor: Anchor::Center, ..Default::default() })`.

use njord_model::style::{
    Anchor, Font, IconRotationAlignment, LayerType, LineCap, LineJoin, Placement, TextJustify,
};
use njord_model::{Color, Depth, Layer, Layout, Paint, Source, Sprite, Theme};
use serde_json::{Value, json};

use crate::number::float_json;
use crate::paint_brushes::{IconRot, Label, LineStyle, Offset, Symbol};
use crate::{ChartFeature, ColorLibrary, SymbologyConfig, filters};

/// What every rule may read: the depth thresholds and the colour library.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Context {
    pub config: SymbologyConfig,
    pub colors: ColorLibrary,
}

impl Context {
    pub fn new(config: SymbologyConfig, colors: ColorLibrary) -> Self {
        Context { config, colors }
    }
}

/// The symbology rule for one S-57 object class: its feature props and its style layers.
pub trait Layerable: Send + Sync {
    /// The vector tile layer the style layers read, when it is not the registration key.
    fn source_layer(&self) -> Option<&str> {
        None
    }

    /// The style layers, in draw order.
    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer>;

    /// Adds the symbology props (`SY`, `AC`, `LC`, `AP`, `LP`, …) the layers read.
    fn pre_tile_encode(&self, _feature: &mut ChartFeature, _cx: &Context) {}
}

/// Builds the layers of one layerable for one style request.
///
/// It carries the request's depth unit and theme plus the layerable's key, source layer and id
/// counter. Helper-made layer ids are numbered by that counter (`DEPARE_fill_1`,
/// `DEPARE_fill_2`, …). A builder lives for one style generation, so ids always number from 1;
/// the style goldens in `testdata/` pin exactly those ids.
pub struct LayerBuilder<'a> {
    pub cx: &'a Context,
    pub key: &'a str,
    pub source_layer: &'a str,
    pub depth: Depth,
    pub theme: &'a Theme,
    next_id: u32,
}

impl<'a> LayerBuilder<'a> {
    pub fn new(
        cx: &'a Context,
        key: &'a str,
        source_layer: &'a str,
        depth: Depth,
        theme: &'a Theme,
    ) -> Self {
        LayerBuilder {
            cx,
            key,
            source_layer,
            depth,
            theme,
            next_id: 0,
        }
    }

    pub fn config(&self) -> &SymbologyConfig {
        &self.cx.config
    }

    /// Advances the layer id counter and answers the new value (the first call answers 1).
    pub fn next_id(&mut self) -> u32 {
        self.next_id += 1;
        self.next_id
    }

    /// The colour for this request's theme ([`ColorLibrary::color_from`]).
    pub fn color(&self, color: Color) -> String {
        self.cx.colors.color_from(color, self.theme).to_owned()
    }

    /// [`color`](Self::color) as a JSON string.
    pub fn color_json(&self, color: Color) -> Value {
        Value::String(self.color(color))
    }

    /// A fill colour picked by the feature's `AC` prop ([`filters::area_fill_color`]).
    pub fn area_fill_color(&self, options: &[Color]) -> Value {
        filters::area_fill_color(&self.cx.colors, Some(options), self.theme)
    }

    /// A line colour picked by the feature's `LC` prop ([`filters::line_color`]).
    pub fn line_color(&self, options: &[Color]) -> Value {
        filters::line_color(&self.cx.colors, Some(options), self.theme)
    }

    /// A layer of `kind` on the chart source and this layerable's source layer, everything else unset.
    pub fn layer(&self, id: impl Into<String>, kind: LayerType) -> Layer {
        Layer {
            id: id.into(),
            paint: None,
            kind,
            source: Some(Source::SENC.to_owned()),
            source_layer: Some(self.source_layer.to_owned()),
            filter: None,
            layout: None,
            min_zoom: None,
            max_zoom_exclusive: None,
        }
    }

    /// `"{key}_{infix}{next_id}"`, e.g. `DEPARE_fill_1` (infix `fill_`) or `ACHARE_area_point3`
    /// (infix `area_point`: some infixes have no trailing separator, as the style goldens record).
    fn helper_id(&mut self, infix: &str) -> String {
        let n = self.next_id();
        format!("{}_{infix}{n}", self.key)
    }

    /// The SNDG2 text on a DEPDW halo that most labels use.
    pub fn label_paint(&self, text: Color, halo: Color) -> Paint {
        Paint {
            text_color: Some(self.color_json(text)),
            text_halo_color: Some(self.color(halo)),
            text_halo_width: Some(2.5),
            ..Default::default()
        }
    }

    /// A point symbol on point features (`<key>_point_<n>`).
    pub fn point_layer_from_symbol(&mut self, o: PointSymbolOpts) -> Layer {
        let id = self.helper_id("point_");
        Layer {
            filter: Some(
                o.filter
                    .unwrap_or_else(|| json!([filters::ANY, filters::eq_type_point()])),
            ),
            layout: Some(Layout {
                symbol_placement: Some(Placement::Point),
                icon_image: Some(o.symbol.property()),
                icon_anchor: Some(o.anchor),
                icon_offset: o.icon_offset.map(|x| x.property()),
                icon_allow_overlap: Some(o.icon_allow_overlap),
                icon_rotate: o.icon_rotate.map(|x| x.property()),
                icon_rotation_alignment: o.icon_rotation_alignment,
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A text label on point features (`<key>_label_<n>`).
    pub fn point_layer_with_label(&mut self, label: &Label, o: PointLabelOpts) -> Layer {
        let id = self.helper_id("label_");
        Layer {
            filter: Some(filters::eq_type_point()),
            layout: Some(Layout {
                text_font: Some(vec![Font::RobotoBold]),
                text_anchor: o.text_anchor,
                text_justify: Some(o.text_justify),
                text_field: Some(label.property()),
                text_size: Some(14.0),
                text_offset: o.text_offset.map(|x| x.property()),
                symbol_placement: Some(Placement::Point),
                ..Default::default()
            }),
            paint: Some(self.label_paint(o.label_color, o.highlight_color)),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A line in one fixed colour (`<key>_line_<n>`).
    pub fn line_layer_with_color(&mut self, color: Color, o: LineColorOpts) -> Layer {
        let id = self.helper_id("line_");
        Layer {
            filter: Some(
                o.filter
                    .unwrap_or_else(filters::eq_type_line_string_or_polygon),
            ),
            layout: o.layout,
            paint: Some(Paint {
                line_color: Some(self.color_json(color)),
                line_width: Some(o.width),
                line_dash_array: o.style.line_dash_array(),
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Line)
        }
    }

    /// A line whose colour comes from the `LC` prop, one of `options` (`<key>_line_<n>`).
    pub fn line_layer_with_colors(&mut self, options: &[Color], o: LineColorsOpts) -> Layer {
        let id = self.helper_id("line_");
        Layer {
            filter: Some(
                o.filter
                    .unwrap_or_else(filters::eq_type_line_string_or_polygon),
            ),
            layout: Some(Layout {
                line_join: Some(LineJoin::Round),
                line_cap: Some(LineCap::Round),
                ..Default::default()
            }),
            paint: Some(Paint {
                line_color: Some(self.line_color(options)),
                line_width: Some(o.width),
                line_dash_array: o.style.line_dash_array(),
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Line)
        }
    }

    /// A text label along line features (`<key>_label_<n>`).
    pub fn line_layer_with_label(&mut self, label: &Label, o: LineLabelOpts) -> Layer {
        let id = self.helper_id("label_");
        Layer {
            filter: Some(filters::eq_type_line_string_or_polygon()),
            layout: Some(Layout {
                text_font: Some(vec![Font::RobotoBold]),
                text_justify: Some(TextJustify::Center),
                text_field: Some(label.property()),
                text_size: Some(14.0),
                symbol_placement: Some(Placement::Line),
                ..Default::default()
            }),
            paint: Some(self.label_paint(o.label_color, o.highlight_color)),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A symbol repeated along lines, and optionally polygon outlines (`<key>_line_symbol_<n>`).
    pub fn line_layer_with_pattern(&mut self, o: LinePatternOpts) -> Layer {
        let id = self.helper_id("line_symbol_");
        Layer {
            filter: Some(if o.include_polygon_lines {
                filters::eq_type_line_string_or_polygon()
            } else {
                filters::eq_type_line_string()
            }),
            layout: Some(Layout {
                symbol_placement: Some(o.symbol_placement),
                icon_image: Some(sprite_or_prop(o.symbol, "LP")),
                icon_anchor: Some(Anchor::Center),
                icon_rotation_alignment: o.icon_rotation_alignment,
                icon_keep_upright: Some(false),
                icon_allow_overlap: o.icon_allow_overlap,
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A map-aligned symbol repeated along lines and polygon outlines at a given size and spacing
    /// (`<key>_line_symbol_<n>`).
    pub fn line_layer_with_spaced_pattern(
        &mut self,
        allow_overlap: bool,
        o: SpacedLinePatternOpts,
    ) -> Layer {
        let id = self.helper_id("line_symbol_");
        Layer {
            filter: Some(filters::eq_type_line_string_or_polygon()),
            layout: Some(Layout {
                symbol_placement: Some(Placement::Line),
                icon_image: Some(sprite_or_prop(o.symbol, "LP")),
                icon_rotate: o.icon_rotate.map(float_json),
                icon_rotation_alignment: Some(IconRotationAlignment::Map),
                icon_allow_overlap: Some(allow_overlap),
                icon_size: o.icon_size,
                symbol_spacing: o.spacing,
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// The `text_key` prop as text along line features. The id has no separator before the
    /// number: `RECTRC_line_label2`.
    pub fn line_layer_with_text(&mut self, text_key: &str) -> Layer {
        let id = self.helper_id("line_label");
        Layer {
            filter: Some(filters::eq_type_line_string()),
            layout: Some(Layout {
                text_font: Some(vec![Font::RobotoBold]),
                text_justify: Some(TextJustify::Center),
                text_field: Some(json!(["get", text_key])),
                text_size: Some(14.0),
                symbol_placement: Some(Placement::Line),
                ..Default::default()
            }),
            paint: Some(self.label_paint(Color::SNDG2, Color::DEPDW)),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A polygon fill whose colour comes from the `AC` prop, one of `options` (`<key>_fill_<n>`).
    pub fn area_layer_with_fill_colors(&mut self, options: &[Color]) -> Layer {
        let id = self.helper_id("fill_");
        Layer {
            filter: Some(filters::eq_type_polygon()),
            paint: Some(Paint {
                fill_color: Some(self.area_fill_color(options)),
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Fill)
        }
    }

    /// A polygon fill in one fixed colour, optionally translucent (`<key>_fill_<n>`).
    pub fn area_layer_with_fill_color(&mut self, color: Color, opacity: Option<f32>) -> Layer {
        let id = self.helper_id("fill_");
        Layer {
            filter: Some(filters::eq_type_polygon()),
            paint: Some(Paint {
                fill_color: Some(self.color_json(color)),
                fill_opacity: opacity,
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Fill)
        }
    }

    /// A polygon fill pattern: the sprite, or the `AP` prop (`<key>_fill_pattern_<n>`).
    pub fn area_layer_with_fill_pattern(&mut self, symbol: Option<Sprite>) -> Layer {
        let id = self.helper_id("fill_pattern_");
        Layer {
            filter: Some(filters::eq_type_polygon()),
            paint: Some(Paint {
                fill_pattern: Some(sprite_or_prop(symbol, "AP")),
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Fill)
        }
    }

    /// One symbol per polygon. The id has no separator before the number: `CBLARE_area_symbol2`.
    pub fn area_layer_with_single_symbol(&mut self, o: AreaSingleSymbolOpts) -> Layer {
        let id = self.helper_id("area_symbol");
        Layer {
            filter: Some(filters::eq_type_polygon()),
            layout: Some(Layout {
                symbol_placement: Some(Placement::Point),
                icon_image: Some(sprite_or_prop(o.symbol, "SY")),
                icon_offset: o.icon_offset.map(float_list),
                icon_anchor: Some(o.anchor),
                icon_rotation_alignment: Some(o.icon_rotation_alignment),
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A polygon's point symbol, unless the feature set `EA`
    /// ([`ChartFeature::exclude_area_point_symbol`]).
    ///
    /// The id has no separator before the number: `OBSTRN_area_point2`.
    pub fn area_layer_with_point_symbol(&mut self, o: AreaPointSymbolOpts) -> Layer {
        let id = self.helper_id("area_point");
        Layer {
            filter: Some(json!([
                filters::ALL,
                filters::eq_type_polygon(),
                ["!=", "EA", true]
            ])),
            layout: Some(Layout {
                symbol_placement: Some(Placement::Point),
                icon_image: Some(sprite_or_prop(o.symbol, "SY")),
                icon_anchor: Some(o.anchor),
                icon_rotate: o.icon_rotate.map(|x| x.property()),
                icon_rotation_alignment: o.icon_rotation_alignment,
                icon_allow_overlap: Some(o.icon_allow_overlap),
                icon_keep_upright: Some(false),
                icon_offset: o.icon_offset.map(float_list),
                ..Default::default()
            }),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// S-52 `TX`/`TE` text, point placed, so a point, line or area each get one upright label.
    ///
    /// It is unfiltered by geometry type unless `filter` is given.
    pub fn text_layer(&mut self, text_field: Value, o: TextLayerOpts) -> Layer {
        let id = self.helper_id("text_");
        Layer {
            filter: o.filter,
            layout: Some(Layout {
                text_font: Some(vec![Font::RobotoBold]),
                text_anchor: Some(o.text_anchor),
                text_justify: Some(o.text_justify),
                text_field: Some(text_field),
                text_offset: o.text_offset.map(|x| x.property()),
                text_size: Some(14.0),
                symbol_placement: Some(Placement::Point),
                ..Default::default()
            }),
            paint: Some(self.label_paint(Color::SNDG2, Color::DEPDW)),
            ..self.layer(id, LayerType::Symbol)
        }
    }

    /// A text label on points and polygons. The id has no separator before the number:
    /// `LNDRGN_area_label3`.
    pub fn area_layer_with_text(&mut self, label: &Label, o: AreaTextOpts) -> Layer {
        let id = self.helper_id("area_label");
        Layer {
            filter: Some(filters::eq_type_point_or_polygon()),
            layout: Some(Layout {
                text_font: Some(vec![Font::RobotoBold]),
                text_justify: Some(o.text_justify),
                text_field: Some(label.property()),
                text_size: Some(14.0),
                text_offset: o.text_offset.map(|x| x.property()),
                text_optional: o.text_optional,
                ..Default::default()
            }),
            paint: Some(self.label_paint(o.text_color, o.halo_color)),
            ..self.layer(id, LayerType::Symbol)
        }
    }
}

/// The sprite's name, or a `["get", prop]` expression when there is none.
fn sprite_or_prop(symbol: Option<Sprite>, prop: &str) -> Value {
    match symbol {
        Some(s) => json!(s.as_str()),
        None => json!(["get", prop]),
    }
}

/// The values as a JSON array of short-form numbers ([`float_json`]).
fn float_list(values: Vec<f32>) -> Value {
    Value::Array(values.into_iter().map(float_json).collect())
}

/// The optional arguments of [`LayerBuilder::point_layer_from_symbol`].
#[derive(Clone, Debug, PartialEq)]
pub struct PointSymbolOpts {
    pub symbol: Symbol,
    pub anchor: Anchor,
    pub icon_offset: Option<Offset>,
    pub icon_allow_overlap: bool,
    pub icon_rotate: Option<IconRot>,
    pub icon_rotation_alignment: Option<IconRotationAlignment>,
    /// Default `["any", ["==", "$type", "Point"]]`.
    pub filter: Option<Value>,
}

impl Default for PointSymbolOpts {
    fn default() -> Self {
        PointSymbolOpts {
            symbol: Symbol::default(),
            anchor: Anchor::Bottom,
            icon_offset: None,
            icon_allow_overlap: true,
            icon_rotate: None,
            icon_rotation_alignment: None,
            filter: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::point_layer_with_label`].
#[derive(Clone, Debug, PartialEq)]
pub struct PointLabelOpts {
    pub label_color: Color,
    pub highlight_color: Color,
    pub text_anchor: Option<Anchor>,
    pub text_justify: TextJustify,
    pub text_offset: Option<Offset>,
}

impl Default for PointLabelOpts {
    fn default() -> Self {
        PointLabelOpts {
            label_color: Color::SNDG2,
            highlight_color: Color::DEPDW,
            text_anchor: None,
            text_justify: TextJustify::Center,
            text_offset: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::line_layer_with_color`].
#[derive(Clone, Debug, PartialEq)]
pub struct LineColorOpts {
    pub style: LineStyle,
    pub layout: Option<Layout>,
    pub width: f32,
    /// Default `["!=", "$type", "Point"]`.
    pub filter: Option<Value>,
}

impl Default for LineColorOpts {
    fn default() -> Self {
        LineColorOpts {
            style: LineStyle::Solid,
            layout: None,
            width: 2.0,
            filter: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::line_layer_with_colors`].
#[derive(Clone, Debug, PartialEq)]
pub struct LineColorsOpts {
    pub style: LineStyle,
    pub width: f32,
    pub filter: Option<Value>,
}

impl Default for LineColorsOpts {
    fn default() -> Self {
        LineColorsOpts {
            style: LineStyle::Solid,
            width: 2.0,
            filter: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::line_layer_with_label`].
#[derive(Clone, Debug, PartialEq)]
pub struct LineLabelOpts {
    pub label_color: Color,
    pub highlight_color: Color,
}

impl Default for LineLabelOpts {
    fn default() -> Self {
        LineLabelOpts {
            label_color: Color::SNDG2,
            highlight_color: Color::DEPDW,
        }
    }
}

/// The optional arguments of [`LayerBuilder::line_layer_with_pattern`].
#[derive(Clone, Debug, PartialEq)]
pub struct LinePatternOpts {
    /// Default: the `LP` prop.
    pub symbol: Option<Sprite>,
    pub include_polygon_lines: bool,
    pub symbol_placement: Placement,
    pub icon_rotation_alignment: Option<IconRotationAlignment>,
    pub icon_allow_overlap: Option<bool>,
}

impl Default for LinePatternOpts {
    fn default() -> Self {
        LinePatternOpts {
            symbol: None,
            include_polygon_lines: true,
            symbol_placement: Placement::Line,
            icon_rotation_alignment: None,
            icon_allow_overlap: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::line_layer_with_spaced_pattern`].
#[derive(Clone, Debug, Default, PartialEq)]
pub struct SpacedLinePatternOpts {
    /// Default: the `LP` prop.
    pub symbol: Option<Sprite>,
    pub icon_rotate: Option<f32>,
    pub icon_size: Option<f32>,
    pub spacing: Option<f32>,
}

/// The optional arguments of [`LayerBuilder::area_layer_with_single_symbol`].
#[derive(Clone, Debug, PartialEq)]
pub struct AreaSingleSymbolOpts {
    /// Default: the `SY` prop.
    pub symbol: Option<Sprite>,
    pub icon_offset: Option<Vec<f32>>,
    pub anchor: Anchor,
    pub icon_rotation_alignment: IconRotationAlignment,
}

impl Default for AreaSingleSymbolOpts {
    fn default() -> Self {
        AreaSingleSymbolOpts {
            symbol: None,
            icon_offset: None,
            anchor: Anchor::Center,
            icon_rotation_alignment: IconRotationAlignment::Map,
        }
    }
}

/// The optional arguments of [`LayerBuilder::area_layer_with_point_symbol`].
#[derive(Clone, Debug, PartialEq)]
pub struct AreaPointSymbolOpts {
    /// Default: the `SY` prop.
    pub symbol: Option<Sprite>,
    pub anchor: Anchor,
    pub icon_rotate: Option<IconRot>,
    pub icon_rotation_alignment: Option<IconRotationAlignment>,
    pub icon_allow_overlap: bool,
    pub icon_offset: Option<Vec<f32>>,
}

impl Default for AreaPointSymbolOpts {
    fn default() -> Self {
        AreaPointSymbolOpts {
            symbol: None,
            anchor: Anchor::Center,
            icon_rotate: None,
            icon_rotation_alignment: None,
            icon_allow_overlap: true,
            icon_offset: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::text_layer`].
#[derive(Clone, Debug, PartialEq)]
pub struct TextLayerOpts {
    pub text_anchor: Anchor,
    pub text_justify: TextJustify,
    pub text_offset: Option<Offset>,
    pub filter: Option<Value>,
}

impl Default for TextLayerOpts {
    fn default() -> Self {
        TextLayerOpts {
            text_anchor: Anchor::Center,
            text_justify: TextJustify::Center,
            text_offset: None,
            filter: None,
        }
    }
}

/// The optional arguments of [`LayerBuilder::area_layer_with_text`].
#[derive(Clone, Debug, PartialEq)]
pub struct AreaTextOpts {
    pub text_color: Color,
    pub halo_color: Color,
    pub text_justify: TextJustify,
    pub text_offset: Option<Offset>,
    pub text_optional: Option<bool>,
}

impl Default for AreaTextOpts {
    fn default() -> Self {
        AreaTextOpts {
            text_color: Color::CHBLK,
            halo_color: Color::CHWHT,
            text_justify: TextJustify::Center,
            text_offset: None,
            text_optional: None,
        }
    }
}

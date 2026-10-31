//! The MapLibre map behind `/enc` (`viewmodel/ChartController.js.kt`): the chart style, feature
//! queries, light-sector icons, the region overlay and, for an admin, the region editor (drag,
//! box-delete, insert and copy-as-WKT of coverage vertices).
//!
//! One map at a time, held in a thread-local; [`with`] lends it out. Map event handlers use
//! [`try_with`], because MapLibre fires some events (`moveend`) synchronously inside calls made
//! through [`with`].

use std::cell::RefCell;

use geojson::{Bbox, Feature, FeatureCollection, GeoJson, Geometry, GeometryValue};
use leptos::prelude::*;
use leptos::task::spawn_local;
use njord_model::sector_svg::build_sector_svg;
use njord_model::style::{Anchor, Font, LayerType, LineCap, LineJoin, Placement, TextJustify};
use njord_model::{
    Depth, JsonObject, Layer, Layout, Paint, RegionManifestEntry, Source, SourceType, Theme,
    ThemeMode,
};
use serde_json::{Value, json};
use wasm_bindgen::JsCast;
use wasm_bindgen::prelude::*;
use web_sys::{Element, HtmlElement};

use crate::api;
use crate::maplibre::{Map, NavigationControl, ScaleControl, get, get_f64, to_js};
use crate::store::{MapFeature, MapLocation, store};

const REGION_MIN_ZOOM: i32 = 0;
const REGION_MAX_ZOOM_EXCLUSIVE: i32 = 7;

const MOVE_ICON_SVG: &str = r#"<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="5 9 2 12 5 15"></polyline><polyline points="9 5 12 2 15 5"></polyline><polyline points="15 19 12 22 9 19"></polyline><polyline points="19 9 22 12 19 15"></polyline><line x1="2" y1="12" x2="22" y2="12"></line><line x1="12" y1="2" x2="12" y2="22"></line></svg>"#;

const DELETE_ICON_SVG: &str = r#"<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>"#;

type Vertex = [f64; 2];
/// Polygons → rings → vertices, each ring without its closing vertex.
type Parts = Vec<Vec<Vec<Vertex>>>;

#[derive(Clone, Copy)]
struct RectPx {
    left: f64,
    top: f64,
    right: f64,
    bottom: f64,
}

#[derive(Default)]
pub struct Controller {
    map: Option<Map>,
    theme_mode: Option<ThemeMode>,
    scale_control: Option<ScaleControl>,
    container: Option<HtmlElement>,
    regions: Vec<RegionManifestEntry>,
    region_filter: Option<String>,
    edit_region: Option<String>,
    edit_parts: Parts,
    drag_handle: Option<(usize, usize, usize)>,
    delete_mode: bool,
    toolbar: Option<Element>,
    box_start: Option<(f64, f64)>,
    box_element: Option<Element>,
    box_buttons: Option<Element>,
    context_dismiss: Option<Closure<dyn FnMut(web_sys::Event)>>,
}

thread_local! {
    static CONTROLLER: RefCell<Controller> = RefCell::new(Controller::default());
}

pub fn with<R>(f: impl FnOnce(&mut Controller) -> R) -> R {
    CONTROLLER.with(|c| f(&mut c.borrow_mut()))
}

/// [`with`], skipped when the controller is already lent out.
fn try_with(f: impl FnOnce(&mut Controller)) {
    CONTROLLER.with(|c| {
        if let Ok(mut c) = c.try_borrow_mut() {
            f(&mut c)
        }
    })
}

fn document() -> web_sys::Document {
    web_sys::window()
        .and_then(|w| w.document())
        .expect("document")
}

fn listen(map: &Map, kind: &str, layer: Option<&str>, f: impl FnMut(JsValue) + 'static) {
    let closure = Closure::<dyn FnMut(JsValue)>::new(f);
    match layer {
        Some(layer) => map.on_layer(kind, layer, closure.as_ref().unchecked_ref()),
        None => map.on(kind, closure.as_ref().unchecked_ref()),
    }
    closure.forget();
}

fn prevent_default(event: &JsValue) {
    if let Ok(f) = get(event, "preventDefault").dyn_into::<js_sys::Function>() {
        let _ = f.call0(event);
    }
}

/// `event.point` (pixels from the map container's top left).
fn point(event: &JsValue) -> (f64, f64) {
    let p = get(event, "point");
    (
        get_f64(&p, "x").unwrap_or(0.0),
        get_f64(&p, "y").unwrap_or(0.0),
    )
}

/// The `properties` of `event.features[0]`.
fn first_feature_properties(event: &JsValue) -> Option<JsValue> {
    let features: js_sys::Array = get(event, "features").dyn_into().ok()?;
    (features.length() > 0).then(|| get(&features.get(0), "properties"))
}

fn vertex_handle(props: &JsValue) -> Option<(usize, usize, usize)> {
    Some((
        get_f64(props, "part")? as usize,
        get_f64(props, "ring")? as usize,
        get_f64(props, "vertex")? as usize,
    ))
}

/// Only the left button (or none, for touch) starts a drag. A right-button `mousedown` must not
/// be `preventDefault`ed, or Chromium never fires the `contextmenu` that opens the vertex menu.
fn left_button(event: &JsValue) -> bool {
    get_f64(&get(event, "originalEvent"), "button").is_none_or(|b| b == 0.0)
}

fn logged_in() -> bool {
    store().admin.with_untracked(|a| a.is_logged_in())
}

fn geojson_source(data: GeoJson) -> JsValue {
    to_js(&Source {
        kind: SourceType::GeoJson,
        tile_json_url: None,
        promote_id: None,
        data: Some(data),
    })
}

fn feature(geometry: Geometry, properties: Value) -> Feature {
    Feature {
        geometry: Some(geometry),
        properties: match properties {
            Value::Object(o) => Some(o),
            _ => None,
        },
        ..Feature::default()
    }
}

fn collection(features: Vec<Feature>) -> GeoJson {
    GeoJson::FeatureCollection(FeatureCollection {
        bbox: None,
        features,
        foreign_members: None,
    })
}

fn layer(id: &str, kind: LayerType, source: &str) -> Layer {
    Layer {
        id: id.to_owned(),
        paint: None,
        kind,
        source: Some(source.to_owned()),
        source_layer: None,
        filter: None,
        layout: None,
        min_zoom: None,
        max_zoom_exclusive: None,
    }
}

fn highlight_line() -> Layer {
    Layer {
        layout: Some(Layout {
            line_join: Some(LineJoin::Round),
            line_cap: Some(LineCap::Round),
            ..Layout::default()
        }),
        paint: Some(Paint {
            line_color: Some(json!("#D63F24")),
            line_width: Some(8.0),
            ..Paint::default()
        }),
        ..layer("highlight_line", LayerType::Line, "highlight")
    }
}

fn highlight_point() -> Layer {
    Layer {
        filter: Some(json!(["==", "$type", "Point"])),
        paint: Some(Paint {
            circle_stroke_color: Some(json!("#D63F24")),
            circle_opacity: Some(0.0),
            circle_radius: Some(80.0),
            circle_stroke_width: Some(8.0),
            ..Paint::default()
        }),
        ..layer("highlight_point", LayerType::Circle, "highlight")
    }
}

/// A `queryRenderedFeatures` result. A feature that does not decode is listed as an `Error`
/// layer carrying the message, as Kotlin did.
fn decode_features(raw: &JsValue) -> Vec<MapFeature> {
    let text = js_sys::JSON::stringify(raw)
        .ok()
        .and_then(|s| s.as_string())
        .unwrap_or_default();
    let values: Vec<Value> = serde_json::from_str(&text).unwrap_or_default();
    values
        .into_iter()
        .map(|v| {
            serde_json::from_value::<MapFeature>(v).unwrap_or_else(|e| {
                let mut properties = JsonObject::new();
                properties.insert("ERROR".into(), e.to_string().into());
                MapFeature {
                    geometry: None,
                    properties,
                    source_layer: Some("Error".into()),
                }
            })
        })
        .collect()
}

fn dedupe_ring(ring: &[geojson::Position]) -> Vec<Vertex> {
    let mut out: Vec<Vertex> = ring
        .iter()
        .map(|p| {
            let p = p.as_slice();
            [
                p.first().copied().unwrap_or(0.0),
                p.get(1).copied().unwrap_or(0.0),
            ]
        })
        .collect();
    if out.len() > 1 && out.first() == out.last() {
        out.pop();
    }
    out
}

fn closed_ring(ring: &[Vertex]) -> Vec<geojson::Position> {
    ring.iter().chain(ring.first()).map(|&p| p.into()).collect()
}

fn ring_to_wkt(ring: &[Vertex]) -> String {
    let coords: Vec<String> = ring
        .iter()
        .chain(ring.first())
        .map(|p| format!("{} {}", p[0], p[1]))
        .collect();
    format!("({})", coords.join(","))
}

/// The edited coverage as WKT, the form `regionExports` in `application.json` takes.
fn region_wkt(parts: &Parts) -> String {
    let rings = |rings: &Vec<Vec<Vertex>>| {
        rings
            .iter()
            .map(|r| ring_to_wkt(r))
            .collect::<Vec<_>>()
            .join(",")
    };
    if parts.len() <= 1 {
        format!("POLYGON ({})", parts.first().map(rings).unwrap_or_default())
    } else {
        let polygons: Vec<String> = parts.iter().map(|p| format!("({})", rings(p))).collect();
        format!("MULTIPOLYGON ({})", polygons.join(","))
    }
}

fn create_div(id: &str, css: &str) -> Element {
    let div = document().create_element("div").expect("div");
    div.set_id(id);
    let _ = div.set_attribute("style", css);
    div
}

fn on_click(element: &Element, kind: &str, f: impl FnMut() + 'static) {
    let closure = Closure::<dyn FnMut()>::new(f);
    let _ = element.add_event_listener_with_callback(kind, closure.as_ref().unchecked_ref());
    closure.forget();
}

fn set_style(element: &Element, name: &str, value: &str) {
    if let Some(e) = element.dyn_ref::<HtmlElement>() {
        let _ = e.style().set_property(name, value);
    }
}

fn append_to_body(element: &Element) {
    if let Some(body) = document().body() {
        let _ = body.append_child(element);
    }
}

impl Controller {
    pub fn move_to(&self, location: MapLocation) {
        if let Some(map) = &self.map {
            map.jump_to(&to_js(&json!({
                "center": [location.longitude, location.latitude],
                "zoom": location.zoom,
            })));
        }
    }

    /// `bounds` is `[west, south, east, north]`.
    pub fn fit_bounds(&self, bounds: &Bbox) {
        if let (Some(map), [west, south, east, north, ..]) = (&self.map, bounds.as_slice()) {
            map.fit_bounds(&to_js(&json!([[west, north], [east, south]])));
        }
    }

    pub fn set_style(&mut self, theme: &Theme, depth: Depth) {
        self.theme_mode = Some(theme.mode());
        let Some(map) = self.map.clone() else {
            return;
        };
        for image in map.list_images().iter().filter_map(|i| i.as_string()) {
            if image.starts_with("sector_") {
                map.remove_image(&image);
            }
        }
        // diff: false forces a full style reload, so "style.load" fires again and the region
        // source and layers are re-added; a diffed setStyle silently drops sources and layers that
        // are not in the new style JSON without firing "style.load".
        map.set_style(
            &njord_model::colors::style_path(theme, depth),
            &to_js(&json!({"diff": false})),
        );
        self.add_scale_control(&map, depth);
    }

    fn add_scale_control(&mut self, map: &Map, depth: Depth) {
        if let Some(old) = self.scale_control.take() {
            map.remove_control(&old);
        }
        let unit = match depth {
            Depth::Meters => "metric",
            _ => "nautical",
        };
        let control = ScaleControl::new(&to_js(&json!({"unit": unit})));
        map.add_control(&control, "bottom-left");
        self.scale_control = Some(control);
    }

    pub fn highlight(&self, highlight: &Feature) {
        if let Some(map) = &self.map {
            map.add_source(
                "highlight",
                &geojson_source(GeoJson::Feature(highlight.clone())),
            );
        }
    }

    pub fn dispose(&mut self) {
        if let Some(map) = self.map.take() {
            map.remove();
        }
        self.scale_control = None;
    }

    /// Creates the map in `container` from the chart state: style, position, and a pending
    /// highlight or bounds from the chart info or layer page.
    pub fn create(&mut self, container: HtmlElement) {
        let s = store();
        let state = s.chart.get_untracked();
        self.theme_mode = Some(state.theme.mode());
        self.container = Some(container.clone());
        let options = js_sys::Object::new();
        let set = |k: &str, v: &JsValue| {
            let _ = js_sys::Reflect::set(&options, &JsValue::from_str(k), v);
        };
        set("container", &container);
        set(
            "style",
            &njord_model::colors::style_path(&state.theme, state.depth).into(),
        );
        set(
            "center",
            &to_js(&[state.location.longitude, state.location.latitude]),
        );
        set("zoom", &state.location.zoom.into());
        set("attributionControl", &false.into());
        let map = Map::new(&options);
        map.add_control(&NavigationControl::new(), "top-right");
        self.add_scale_control(&map, state.depth);
        self.map = Some(map.clone());

        if let Some(geo) = state.highlight.clone() {
            listen(&map, "load", None, move |_| {
                try_with(|c| {
                    c.highlight(&geo);
                    if let Some(map) = &c.map {
                        map.add_layer(&to_js(&highlight_line()));
                        map.add_layer(&to_js(&highlight_point()));
                    }
                });
            });
        }
        if let Some(bounds) = state.bounds.clone() {
            // On "load", once the container has its size: a fit into a container that has not
            // been laid out yet lands at the wrong zoom.
            listen(&map, "load", None, move |_| {
                try_with(|c| c.fit_bounds(&bounds))
            });
            s.chart.update(|c| {
                c.bounds = None;
                c.highlight = None;
            });
        }

        listen(&map, "moveend", None, move |event| {
            let target: Map = get(&event, "target").unchecked_into();
            let center = target.get_center();
            store().on_map_move(MapLocation {
                longitude: get_f64(&center, "lng").unwrap_or(0.0),
                latitude: get_f64(&center, "lat").unwrap_or(0.0),
                zoom: target.get_zoom(),
            });
        });
        listen(&map, "click", None, move |event| {
            // No feature query while the region overview's own zoom range is shown: regions are
            // broad overviews, not charts to inspect.
            let target: Map = get(&event, "target").unchecked_into();
            if target.get_zoom() >= REGION_MAX_ZOOM_EXCLUSIVE as f64 {
                let (x, y) = point(&event);
                let raw = target
                    .query_rendered_features(&to_js(&[[x - 5.0, y - 5.0], [x + 5.0, y + 5.0]]));
                store().set_query(decode_features(&raw));
            }
        });
        listen(&map, "click", Some("region_fill"), move |event| {
            if logged_in()
                && let Some(name) =
                    first_feature_properties(&event).and_then(|p| get(&p, "name").as_string())
            {
                try_with(|c| c.toggle_region_edit(&name));
            }
        });
        // The region being edited is left out of the "regions" source (so it is not drawn twice
        // under the edit overlay), so a click on it lands on "region_edit_fill": that exits.
        listen(&map, "click", Some("region_edit_fill"), move |_| {
            if logged_in() {
                try_with(Controller::exit_region_edit);
            }
        });
        listen(
            &map,
            "mousedown",
            Some("region_edit_points"),
            move |event| {
                try_with(|c| {
                    if logged_in() && !c.delete_mode && left_button(&event) {
                        prevent_default(&event);
                        if let Some(handle) = first_feature_properties(&event)
                            .as_ref()
                            .and_then(vertex_handle)
                        {
                            c.drag_handle = Some(handle);
                            if let Some(map) = &c.map {
                                map.drag_pan().disable();
                            }
                        }
                    }
                });
            },
        );
        listen(&map, "mousedown", None, move |event| {
            // In delete mode a left-button drag anywhere starts a selection box.
            try_with(|c| {
                if logged_in() && c.delete_mode && c.edit_region.is_some() && left_button(&event) {
                    prevent_default(&event);
                    let (x, y) = point(&event);
                    c.start_box_select(x, y);
                }
            });
        });
        listen(&map, "mousemove", None, move |event| {
            try_with(|c| {
                if let Some((pi, ri, vi)) = c.drag_handle {
                    let ll = get(&event, "lngLat");
                    let (lng, lat) = (
                        get_f64(&ll, "lng").unwrap_or(0.0),
                        get_f64(&ll, "lat").unwrap_or(0.0),
                    );
                    if let Some(v) = c
                        .edit_parts
                        .get_mut(pi)
                        .and_then(|p| p.get_mut(ri))
                        .and_then(|r| r.get_mut(vi))
                    {
                        *v = [lng, lat];
                    }
                    c.refresh_region_edit_sources();
                }
                if c.box_start.is_some() {
                    let (x, y) = point(&event);
                    c.update_box_select(x, y);
                }
            });
        });
        listen(&map, "mouseup", None, move |event| {
            try_with(|c| {
                if c.drag_handle.take().is_some()
                    && let Some(map) = &c.map
                {
                    map.drag_pan().enable();
                }
                if c.box_start.is_some() {
                    let (x, y) = point(&event);
                    c.finish_box_select(x, y);
                    if let Some(map) = &c.map {
                        map.drag_pan().enable();
                    }
                }
            });
        });
        listen(
            &map,
            "contextmenu",
            Some("region_edit_points"),
            move |event| {
                try_with(|c| {
                    if logged_in() && !c.delete_mode {
                        prevent_default(&event);
                        if let Some(handle) = first_feature_properties(&event)
                            .as_ref()
                            .and_then(vertex_handle)
                        {
                            let (x, y) = point(&event);
                            c.show_vertex_context_menu(x, y, handle);
                        }
                    }
                });
            },
        );
        listen(&map, "styleimagemissing", None, move |event| {
            if let Some(id) = get(&event, "id").as_string()
                && id.starts_with("sector_")
            {
                try_with(|c| c.add_sector_image(&id));
            }
        });
        listen(&map, "style.load", None, move |_| {
            spawn_local(load_regions());
        });
    }

    // --- Regions ---

    fn filtered_regions(&self) -> impl Iterator<Item = &RegionManifestEntry> {
        self.regions.iter().filter(|r| {
            self.region_filter
                .as_ref()
                .is_none_or(|name| &r.name == name)
        })
    }

    fn region_features(&self) -> GeoJson {
        let features = self
            .filtered_regions()
            .filter(|r| Some(&r.name) != self.edit_region.as_ref())
            .filter_map(|r| match &r.coverage_geo {
                GeoJson::Geometry(g) => Some(feature(g.clone(), json!({"name": r.name}))),
                _ => None,
            })
            .collect();
        collection(features)
    }

    fn region_label_features(&self) -> GeoJson {
        let features = self
            .filtered_regions()
            .filter_map(|r| {
                let point = r.label_point.clone()?;
                Some(feature(
                    point,
                    json!({"name": r.name, "description": r.description}),
                ))
            })
            .collect();
        collection(features)
    }

    fn region_color(&self) -> &'static str {
        match self.theme_mode {
            Some(ThemeMode::Dusk | ThemeMode::Night) => "#FFFFFF",
            _ => "#000000",
        }
    }

    fn region_label_halo_color(&self) -> &'static str {
        match self.theme_mode {
            Some(ThemeMode::Dusk | ThemeMode::Night) => "#000000",
            _ => "#FFFFFF",
        }
    }

    fn region_layer(&self, id: &str, kind: LayerType, source: &str) -> Layer {
        Layer {
            min_zoom: Some(REGION_MIN_ZOOM),
            max_zoom_exclusive: Some(REGION_MAX_ZOOM_EXCLUSIVE),
            ..layer(id, kind, source)
        }
    }

    fn region_label_layer(
        &self,
        id: &str,
        field: &str,
        text: (Font, f32, Anchor, f32),
        min_zoom: i32,
    ) -> Layer {
        let (font, size, anchor, offset) = text;
        Layer {
            min_zoom: Some(min_zoom),
            layout: Some(Layout {
                text_field: Some(json!(["get", field])),
                text_font: Some(vec![font]),
                text_size: Some(size),
                text_anchor: Some(anchor),
                text_justify: Some(TextJustify::Center),
                text_offset: Some(json!([0.0, offset])),
                text_allow_overlap: Some(true),
                text_ignore_placement: Some(true),
                symbol_placement: Some(Placement::Point),
                ..Layout::default()
            }),
            paint: Some(Paint {
                text_color: Some(json!(self.region_color())),
                text_halo_color: Some(self.region_label_halo_color().to_owned()),
                text_halo_width: Some(1.5),
                ..Paint::default()
            }),
            ..self.region_layer(id, LayerType::Symbol, "region_labels")
        }
    }

    fn add_region_layers(&self) {
        let Some(map) = &self.map else {
            return;
        };
        map.add_source("regions", &geojson_source(self.region_features()));
        map.add_layer(&to_js(&Layer {
            paint: Some(Paint {
                fill_color: Some(json!(self.region_color())),
                fill_opacity: Some(0.5),
                ..Paint::default()
            }),
            ..self.region_layer("region_fill", LayerType::Fill, "regions")
        }));
        map.add_layer(&to_js(&Layer {
            paint: Some(Paint {
                line_color: Some(json!(self.region_color())),
                line_width: Some(1.0),
                ..Paint::default()
            }),
            ..self.region_layer("region_outline", LayerType::Line, "regions")
        }));
        map.add_source(
            "region_labels",
            &geojson_source(self.region_label_features()),
        );
        map.add_layer(&to_js(&self.region_label_layer(
            "region_label_name",
            "name",
            (Font::RobotoBold, 13.0, Anchor::Bottom, -0.5),
            1,
        )));
        map.add_layer(&to_js(&self.region_label_layer(
            "region_label_description",
            "description",
            (Font::RobotoRegular, 11.0, Anchor::Top, 0.1),
            2,
        )));
        if self.edit_region.is_some() {
            self.add_region_edit_layers();
        }
    }

    fn refresh_region_sources(&self) {
        let Some(map) = &self.map else {
            return;
        };
        if let Some(src) = map.get_source("regions") {
            src.set_data(&to_js(&self.region_features()));
        }
        if let Some(src) = map.get_source("region_labels") {
            src.set_data(&to_js(&self.region_label_features()));
        }
    }

    pub fn set_region_filter(&mut self, region: Option<String>) {
        self.region_filter = region;
        self.refresh_region_sources();
    }

    // --- Region editing (admin): click a region's fill to edit its vertices ---

    fn toggle_region_edit(&mut self, name: &str) {
        if self.edit_region.as_deref() == Some(name) {
            self.exit_region_edit();
            return;
        }
        if self.edit_region.is_some() {
            self.exit_region_edit();
        }
        let Some(entry) = self.regions.iter().find(|r| r.name == name) else {
            return;
        };
        let GeoJson::Geometry(geometry) = &entry.coverage_geo else {
            return;
        };
        self.edit_parts = match &geometry.value {
            GeometryValue::Polygon { coordinates: rings } => {
                vec![rings.iter().map(|r| dedupe_ring(r)).collect()]
            }
            GeometryValue::MultiPolygon {
                coordinates: polygons,
            } => polygons
                .iter()
                .map(|rings| rings.iter().map(|r| dedupe_ring(r)).collect())
                .collect(),
            _ => Vec::new(),
        };
        self.edit_region = Some(entry.name.clone());
        self.refresh_region_sources();
        self.add_region_edit_layers();
        self.show_region_edit_toolbar();
    }

    fn exit_region_edit(&mut self) {
        self.remove_vertex_context_menu();
        self.clear_box_select();
        self.remove_region_edit_toolbar();
        self.drag_handle = None;
        self.delete_mode = false;
        if let Some(map) = &self.map {
            map.remove_layer("region_edit_points");
            map.remove_source("region_edit_points");
            map.remove_layer("region_edit_outline");
            map.remove_layer("region_edit_fill");
            map.remove_source("region_edit");
        }
        self.edit_region = None;
        self.edit_parts.clear();
        self.refresh_region_sources();
    }

    fn edit_geometry(&self) -> Geometry {
        let closed: Vec<Vec<Vec<geojson::Position>>> = self
            .edit_parts
            .iter()
            .map(|rings| rings.iter().map(|r| closed_ring(r)).collect())
            .collect();
        let value = if closed.len() <= 1 {
            GeometryValue::Polygon {
                coordinates: closed.into_iter().next().unwrap_or_default(),
            }
        } else {
            GeometryValue::MultiPolygon {
                coordinates: closed,
            }
        };
        Geometry::new(value)
    }

    fn edit_points(&self) -> GeoJson {
        let mut features = Vec::new();
        for (pi, rings) in self.edit_parts.iter().enumerate() {
            for (ri, ring) in rings.iter().enumerate() {
                for (vi, pos) in ring.iter().enumerate() {
                    features.push(feature(
                        Geometry::new_point(*pos),
                        json!({"part": pi, "ring": ri, "vertex": vi}),
                    ));
                }
            }
        }
        collection(features)
    }

    fn edit_point_color(&self) -> &'static str {
        if self.delete_mode {
            "#000000"
        } else {
            "#FF0000"
        }
    }

    fn edit_point_stroke_color(&self) -> &'static str {
        if self.delete_mode {
            "#444444"
        } else {
            "#8B0000"
        }
    }

    fn edit_point_radius(&self) -> f32 {
        if self.delete_mode { 10.0 } else { 20.0 }
    }

    fn add_region_edit_layers(&self) {
        let Some(map) = &self.map else {
            return;
        };
        map.add_source(
            "region_edit",
            &geojson_source(GeoJson::Feature(Feature::from(self.edit_geometry()))),
        );
        map.add_layer(&to_js(&Layer {
            paint: Some(Paint {
                fill_color: Some(json!(self.region_color())),
                fill_opacity: Some(0.5),
                ..Paint::default()
            }),
            ..layer("region_edit_fill", LayerType::Fill, "region_edit")
        }));
        map.add_layer(&to_js(&Layer {
            paint: Some(Paint {
                line_color: Some(json!(self.region_color())),
                line_width: Some(1.0),
                ..Paint::default()
            }),
            ..layer("region_edit_outline", LayerType::Line, "region_edit")
        }));
        map.add_source("region_edit_points", &geojson_source(self.edit_points()));
        map.add_layer(&to_js(&Layer {
            paint: Some(Paint {
                circle_color: Some(json!(self.edit_point_color())),
                circle_radius: Some(self.edit_point_radius()),
                circle_opacity: Some(1.0),
                circle_stroke_color: Some(json!(self.edit_point_stroke_color())),
                circle_stroke_width: Some(2.0),
                ..Paint::default()
            }),
            ..layer(
                "region_edit_points",
                LayerType::Circle,
                "region_edit_points",
            )
        }));
    }

    fn refresh_region_edit_sources(&self) {
        let Some(map) = &self.map else {
            return;
        };
        if let Some(src) = map.get_source("region_edit") {
            src.set_data(&to_js(&Feature::from(self.edit_geometry())));
        }
        if let Some(src) = map.get_source("region_edit_points") {
            src.set_data(&to_js(&self.edit_points()));
        }
    }

    fn update_region_edit_points_style(&self) {
        if let Some(map) = &self.map {
            let set =
                |name: &str, v: JsValue| map.set_paint_property("region_edit_points", name, &v);
            set("circle-color", self.edit_point_color().into());
            set("circle-radius", (self.edit_point_radius() as f64).into());
            set("circle-stroke-color", self.edit_point_stroke_color().into());
        }
    }

    /// MapLibre's event `point` and `project` are relative to the map container, while the
    /// overlays here are `position:fixed` on `document.body`: add the container's offset.
    fn container_offset(&self) -> (f64, f64) {
        self.container
            .as_ref()
            .map(|c| {
                let r = c.get_bounding_client_rect();
                (r.left(), r.top())
            })
            .unwrap_or_default()
    }

    // --- Edit toolbar: "move points" (default) or "box-delete points" ---

    fn show_region_edit_toolbar(&mut self) {
        self.remove_region_edit_toolbar();
        let top = self.container_offset().1 + 10.0;
        let toolbar = create_div(
            "region-edit-toolbar",
            &format!(
                "position:fixed;top:{top}px;left:50%;transform:translateX(-50%);\
                 display:flex;gap:4px;background:#fff;border:1px solid #888;border-radius:6px;\
                 box-shadow:0 2px 8px rgba(0,0,0,0.3);padding:4px;z-index:9000;"
            ),
        );
        let add_button =
            |svg: &str, active: bool, title: &str, delete: bool| {
                let btn = document().create_element("div").expect("div");
                btn.set_inner_html(svg);
                let _ = btn.set_attribute("title", title);
                let _ = btn.set_attribute(
                "style",
                &format!(
                    "width:32px;height:32px;display:flex;align-items:center;justify-content:center;\
                     border-radius:4px;cursor:pointer;color:#222;{}",
                    if active { "background:#cfe3ff;" } else { "background:transparent;" }
                ),
            );
                on_click(&btn, "click", move || {
                    try_with(|c| c.select_edit_tool(delete));
                });
                let _ = toolbar.append_child(&btn);
            };
        add_button(MOVE_ICON_SVG, !self.delete_mode, "Move points", false);
        add_button(DELETE_ICON_SVG, self.delete_mode, "Box-delete points", true);
        append_to_body(&toolbar);
        self.toolbar = Some(toolbar);
    }

    fn select_edit_tool(&mut self, delete: bool) {
        if self.delete_mode == delete {
            return;
        }
        self.delete_mode = delete;
        if delete {
            self.drag_handle = None;
        } else {
            self.clear_box_select();
        }
        self.update_region_edit_points_style();
        self.show_region_edit_toolbar();
    }

    fn remove_region_edit_toolbar(&mut self) {
        if let Some(t) = self.toolbar.take() {
            t.remove();
        }
    }

    // --- Box delete: drag a rectangle, then confirm deleting every point inside it ---

    fn start_box_select(&mut self, x: f64, y: f64) {
        self.clear_box_select();
        self.box_start = Some((x, y));
        let element = create_div(
            "region-edit-box-select",
            "position:fixed;border:2px dashed #2288ff;background:rgba(34,136,255,0.15);\
             z-index:8500;pointer-events:none;",
        );
        append_to_body(&element);
        self.box_element = Some(element);
        self.position_box(x, y, x, y);
    }

    fn position_box(&self, x1: f64, y1: f64, x2: f64, y2: f64) {
        let Some(element) = &self.box_element else {
            return;
        };
        let (ox, oy) = self.container_offset();
        set_style(element, "left", &format!("{}px", ox + x1.min(x2)));
        set_style(element, "top", &format!("{}px", oy + y1.min(y2)));
        set_style(element, "width", &format!("{}px", (x2 - x1).abs()));
        set_style(element, "height", &format!("{}px", (y2 - y1).abs()));
    }

    fn update_box_select(&self, x: f64, y: f64) {
        if let Some((sx, sy)) = self.box_start {
            self.position_box(sx, sy, x, y);
        }
    }

    fn finish_box_select(&mut self, x: f64, y: f64) {
        let Some((sx, sy)) = self.box_start.take() else {
            return;
        };
        let rect = RectPx {
            left: sx.min(x),
            top: sy.min(y),
            right: sx.max(x),
            bottom: sy.max(y),
        };
        self.show_box_select_buttons(rect);
    }

    fn show_box_select_buttons(&mut self, rect: RectPx) {
        if let Some(b) = self.box_buttons.take() {
            b.remove();
        }
        let (ox, oy) = self.container_offset();
        let cx = ox + (rect.left + rect.right) / 2.0;
        let cy = oy + (rect.top + rect.bottom) / 2.0;
        let panel = create_div(
            "region-edit-box-buttons",
            &format!(
                "position:fixed;left:{cx}px;top:{cy}px;transform:translate(-50%,-50%);\
                 display:flex;gap:6px;z-index:9000;"
            ),
        );
        let add_button = |label: &str, bg: &str, delete: bool| {
            let btn = document().create_element("div").expect("div");
            btn.set_text_content(Some(label));
            let _ = btn.set_attribute(
                "style",
                &format!(
                    "padding:6px 12px;background:{bg};color:#fff;border-radius:4px;cursor:pointer;\
                     font-size:13px;box-shadow:0 1px 4px rgba(0,0,0,0.4);white-space:nowrap;"
                ),
            );
            on_click(&btn, "click", move || {
                try_with(|c| {
                    if delete {
                        c.delete_points_in_box(rect);
                    }
                    c.clear_box_select();
                });
            });
            let _ = panel.append_child(&btn);
        };
        add_button("Delete", "#c0392b", true);
        add_button("Clear", "#555555", false);
        append_to_body(&panel);
        self.box_buttons = Some(panel);
    }

    fn clear_box_select(&mut self) {
        self.box_start = None;
        if let Some(e) = self.box_element.take() {
            e.remove();
        }
        if let Some(e) = self.box_buttons.take() {
            e.remove();
        }
    }

    fn delete_points_in_box(&mut self, rect: RectPx) {
        let Some(map) = self.map.clone() else {
            return;
        };
        for rings in &mut self.edit_parts {
            for ring in rings.iter_mut() {
                let inside: Vec<usize> = ring
                    .iter()
                    .enumerate()
                    .filter(|(_, pos)| {
                        let p = map.project(&to_js(pos));
                        let (px, py) = (
                            get_f64(&p, "x").unwrap_or(f64::NAN),
                            get_f64(&p, "y").unwrap_or(f64::NAN),
                        );
                        px >= rect.left && px <= rect.right && py >= rect.top && py <= rect.bottom
                    })
                    .map(|(i, _)| i)
                    .collect();
                // Highest index first so removals do not shift the rest; never below a triangle.
                for vi in inside.into_iter().rev() {
                    if ring.len() > 3 && vi < ring.len() {
                        ring.remove(vi);
                    }
                }
            }
        }
        self.refresh_region_edit_sources();
    }

    // --- Vertex context menu: copy WKT, delete, insert clockwise / counter-clockwise ---

    fn delete_vertex(&mut self, (part, ring, vertex): (usize, usize, usize)) {
        if let Some(r) = self.edit_parts.get_mut(part).and_then(|p| p.get_mut(ring))
            && r.len() > 3
            && vertex < r.len()
        {
            r.remove(vertex);
        }
    }

    fn insert_vertex(&mut self, (part, ring, vertex): (usize, usize, usize), clockwise: bool) {
        let Some(r) = self.edit_parts.get_mut(part).and_then(|p| p.get_mut(ring)) else {
            return;
        };
        let n = r.len();
        if n < 2 || vertex >= n {
            return;
        }
        let neighbor = if clockwise {
            (vertex + 1) % n
        } else {
            (vertex + n - 1) % n
        };
        let (a, b) = (r[vertex], r[neighbor]);
        let mid = [(a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0];
        r.insert(if clockwise { vertex + 1 } else { vertex }, mid);
    }

    fn copy_region_wkt(&self) {
        let wkt = region_wkt(&self.edit_parts);
        let clipboard = web_sys::window()
            .map(|w| get(&w.navigator(), "clipboard"))
            .unwrap_or(JsValue::UNDEFINED);
        if let Ok(write) = get(&clipboard, "writeText").dyn_into::<js_sys::Function>() {
            let _ = write.call1(&clipboard, &JsValue::from_str(&wkt));
        }
    }

    fn remove_vertex_context_menu(&mut self) {
        if let Some(menu) = document().get_element_by_id("region-edit-context-menu") {
            menu.remove();
        }
        if let Some(dismiss) = self.context_dismiss.take() {
            let _ = document()
                .remove_event_listener_with_callback("click", dismiss.as_ref().unchecked_ref());
        }
    }

    fn show_vertex_context_menu(&mut self, x: f64, y: f64, handle: (usize, usize, usize)) {
        self.remove_vertex_context_menu();
        let (ox, oy) = self.container_offset();
        let menu = create_div(
            "region-edit-context-menu",
            &format!(
                "position:fixed;left:{}px;top:{}px;background:#fff;color:#000;\
                 border:1px solid #888;border-radius:4px;box-shadow:0 2px 8px rgba(0,0,0,0.3);\
                 z-index:10000;font-size:14px;min-width:120px;overflow:hidden;",
                ox + x,
                oy + y
            ),
        );
        let add_item = |label: &str, action: fn(&mut Controller, (usize, usize, usize))| {
            let item = document().create_element("div").expect("div");
            item.set_text_content(Some(label));
            let _ = item.set_attribute("style", "padding:8px 12px;cursor:pointer;");
            let hover = item.clone();
            on_click(&item, "mouseenter", move || {
                set_style(&hover, "background-color", "#eee")
            });
            let hover = item.clone();
            on_click(&item, "mouseleave", move || {
                set_style(&hover, "background-color", "#fff")
            });
            on_click(&item, "click", move || {
                try_with(|c| {
                    action(c, handle);
                    c.remove_vertex_context_menu();
                });
            });
            let _ = menu.append_child(&item);
        };
        add_item("Copy WKT", |c, _| c.copy_region_wkt());
        add_item("Delete", |c, h| {
            c.delete_vertex(h);
            c.refresh_region_edit_sources();
        });
        add_item("Add CW", |c, h| {
            c.insert_vertex(h, true);
            c.refresh_region_edit_sources();
        });
        add_item("Add CCW", |c, h| {
            c.insert_vertex(h, false);
            c.refresh_region_edit_sources();
        });
        append_to_body(&menu);

        let menu_node: web_sys::Node = menu.clone().into();
        let dismiss = Closure::<dyn FnMut(web_sys::Event)>::new(move |evt: web_sys::Event| {
            let target = evt
                .target()
                .and_then(|t| t.dyn_into::<web_sys::Node>().ok());
            if !target.is_some_and(|t| menu_node.contains(Some(&t))) {
                try_with(Controller::remove_vertex_context_menu);
            }
        });
        // Registered after this click has finished propagating, or it would close the menu.
        let register = dismiss.as_ref().clone();
        let deferred = Closure::once_into_js(move || {
            let _ = document().add_event_listener_with_callback("click", register.unchecked_ref());
        });
        let _ = web_sys::window()
            .expect("window")
            .set_timeout_with_callback_and_timeout_and_arguments_0(deferred.unchecked_ref(), 0);
        self.context_dismiss = Some(dismiss);
    }

    // --- Light sectors: `sector_<s1>_<s2>_<day>_<dusk>_<night>_<dayLine>_<duskLine>_<nightLine>_<r>` ---

    fn add_sector_image(&self, name: &str) {
        let Some(map) = self.map.clone() else {
            return;
        };
        let parts: Vec<&str> = name.trim_start_matches("sector_").split('_').collect();
        if parts.len() < 9 {
            return;
        }
        let (Ok(sectr1), Ok(sectr2)) = (parts[0].parse::<f64>(), parts[1].parse::<f64>()) else {
            return;
        };
        let radius = parts[8].parse::<f64>().unwrap_or(80.0);
        let (fill, line) = match self.theme_mode {
            Some(ThemeMode::Dusk) => (parts[3], parts[6]),
            Some(ThemeMode::Night) => (parts[4], parts[7]),
            _ => (parts[2], parts[5]),
        };
        let svg = build_sector_svg(sectr1, sectr2, fill, line, radius);
        let url = format!(
            "data:image/svg+xml;charset=utf-8,{}",
            js_sys::encode_uri_component(&svg)
        );
        let Ok(img) = web_sys::HtmlImageElement::new_with_width_and_height(200, 200) else {
            return;
        };
        let loaded = img.clone();
        let id = name.to_owned();
        let on_load = Closure::once_into_js(move || map.add_image(&id, &loaded));
        img.set_onload(Some(on_load.unchecked_ref()));
        img.set_src(&url);
    }
}

/// Fetches the region manifest and adds the region layers (on every `style.load`, since a full
/// style reload drops them). `WORLD` is not drawn.
async fn load_regions() {
    let regions: Vec<RegionManifestEntry> = api::regions()
        .await
        .unwrap_or_default()
        .into_iter()
        .filter(|r| r.name != "WORLD")
        .collect();
    try_with(|c| {
        c.regions = regions;
        c.add_region_layers();
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wkt() {
        let square = vec![vec![[0.0, 0.0], [1.0, 0.0], [1.0, 1.5]]];
        assert_eq!(
            region_wkt(&vec![square.clone()]),
            "POLYGON ((0 0,1 0,1 1.5,0 0))"
        );
        assert_eq!(
            region_wkt(&vec![square.clone(), square]),
            "MULTIPOLYGON (((0 0,1 0,1 1.5,0 0)),((0 0,1 0,1 1.5,0 0)))"
        );
    }

    #[test]
    fn dedupe() {
        let ring: Vec<geojson::Position> =
            vec![[0.0, 0.0].into(), [1.0, 0.0].into(), [0.0, 0.0].into()];
        assert_eq!(dedupe_ring(&ring), vec![[0.0, 0.0], [1.0, 0.0]]);
    }
}

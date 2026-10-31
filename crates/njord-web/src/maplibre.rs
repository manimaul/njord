//! The MapLibre GL calls the UI makes (`js/MapLibre.kt`). MapLibre is loaded from `vendor/` as
//! the global `maplibregl`; option objects and GeoJSON are passed as JSON built with serde.

use serde::Serialize;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = maplibregl)]
    #[derive(Clone)]
    pub type Map;

    #[wasm_bindgen(constructor, js_namespace = maplibregl)]
    pub fn new(options: &JsValue) -> Map;

    #[wasm_bindgen(method)]
    pub fn on(this: &Map, kind: &str, listener: &js_sys::Function);

    #[wasm_bindgen(method, js_name = on)]
    pub fn on_layer(this: &Map, kind: &str, layer_id: &str, listener: &js_sys::Function);

    #[wasm_bindgen(method, js_name = queryRenderedFeatures)]
    pub fn query_rendered_features(this: &Map, geometry: &JsValue) -> JsValue;

    #[wasm_bindgen(method, js_name = setStyle)]
    pub fn set_style(this: &Map, style: &str, options: &JsValue);

    #[wasm_bindgen(method)]
    pub fn remove(this: &Map);

    #[wasm_bindgen(method, js_name = jumpTo)]
    pub fn jump_to(this: &Map, options: &JsValue);

    #[wasm_bindgen(method, js_name = fitBounds)]
    pub fn fit_bounds(this: &Map, bounds: &JsValue);

    #[wasm_bindgen(method, js_name = addLayer)]
    pub fn add_layer(this: &Map, layer: &JsValue);

    #[wasm_bindgen(method, js_name = removeLayer)]
    pub fn remove_layer(this: &Map, id: &str);

    #[wasm_bindgen(method, js_name = setPaintProperty)]
    pub fn set_paint_property(this: &Map, layer_id: &str, name: &str, value: &JsValue);

    #[wasm_bindgen(method, js_name = addSource)]
    pub fn add_source(this: &Map, id: &str, source: &JsValue);

    #[wasm_bindgen(method, js_name = removeSource)]
    pub fn remove_source(this: &Map, id: &str);

    #[wasm_bindgen(method, js_name = getSource)]
    pub fn get_source(this: &Map, id: &str) -> Option<GeoJsonSource>;

    #[wasm_bindgen(method, js_name = addImage)]
    pub fn add_image(this: &Map, id: &str, image: &JsValue);

    #[wasm_bindgen(method, js_name = removeImage)]
    pub fn remove_image(this: &Map, id: &str);

    #[wasm_bindgen(method, js_name = listImages)]
    pub fn list_images(this: &Map) -> js_sys::Array;

    #[wasm_bindgen(method)]
    pub fn project(this: &Map, lng_lat: &JsValue) -> JsValue;

    #[wasm_bindgen(method, js_name = addControl)]
    pub fn add_control(this: &Map, control: &JsValue, position: &str);

    #[wasm_bindgen(method, js_name = removeControl)]
    pub fn remove_control(this: &Map, control: &JsValue);

    #[wasm_bindgen(method, js_name = getCenter)]
    pub fn get_center(this: &Map) -> JsValue;

    #[wasm_bindgen(method, js_name = getZoom)]
    pub fn get_zoom(this: &Map) -> f64;

    #[wasm_bindgen(method, getter = dragPan)]
    pub fn drag_pan(this: &Map) -> Handler;

    #[derive(Clone)]
    pub type Handler;

    #[wasm_bindgen(method)]
    pub fn enable(this: &Handler);

    #[wasm_bindgen(method)]
    pub fn disable(this: &Handler);

    #[derive(Clone)]
    pub type GeoJsonSource;

    #[wasm_bindgen(method, js_name = setData)]
    pub fn set_data(this: &GeoJsonSource, data: &JsValue);

    #[wasm_bindgen(js_namespace = maplibregl, js_name = NavigationControl)]
    #[derive(Clone)]
    pub type NavigationControl;

    #[wasm_bindgen(constructor, js_namespace = maplibregl, js_class = "NavigationControl")]
    pub fn new() -> NavigationControl;

    #[wasm_bindgen(js_namespace = maplibregl, js_name = ScaleControl)]
    #[derive(Clone)]
    pub type ScaleControl;

    #[wasm_bindgen(constructor, js_namespace = maplibregl, js_class = "ScaleControl")]
    pub fn new(options: &JsValue) -> ScaleControl;
}

/// A serde value as a plain JS object (`JSON.parse` of its JSON).
pub fn to_js<T: Serialize + ?Sized>(value: &T) -> JsValue {
    let text = serde_json::to_string(value).expect("serialisable");
    js_sys::JSON::parse(&text).expect("valid JSON")
}

/// A member of a JS object, `undefined` when absent.
pub fn get(target: &JsValue, key: &str) -> JsValue {
    js_sys::Reflect::get(target, &JsValue::from_str(key)).unwrap_or(JsValue::UNDEFINED)
}

pub fn get_f64(target: &JsValue, key: &str) -> Option<f64> {
    get(target, key).as_f64()
}

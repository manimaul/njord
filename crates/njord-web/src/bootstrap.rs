//! The two Bootstrap components the UI drives from code.
//!
//! Bootstrap's bundle is loaded from `vendor/` as the global `bootstrap`.

use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = bootstrap)]
    pub type Modal;

    #[wasm_bindgen(static_method_of = Modal, js_namespace = bootstrap, js_name = getOrCreateInstance)]
    pub fn get_or_create_instance(element: &web_sys::Element) -> Modal;

    #[wasm_bindgen(method)]
    pub fn show(this: &Modal);

    #[wasm_bindgen(method)]
    pub fn hide(this: &Modal);

    #[wasm_bindgen(js_namespace = bootstrap)]
    pub type Collapse;

    /// `selector` is a CSS selector such as `#navbarNavDropdown`.
    #[wasm_bindgen(static_method_of = Collapse, js_namespace = bootstrap, js_name = getOrCreateInstance)]
    pub fn get_or_create_instance(selector: &str) -> Collapse;

    #[wasm_bindgen(method)]
    pub fn toggle(this: &Collapse);
}

/// `Collapse.getOrCreateInstance(selector).toggle()`.
pub fn toggle_collapse(selector: &str) {
    Collapse::get_or_create_instance(selector).toggle();
}

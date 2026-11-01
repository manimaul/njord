//! The Njord web UI: a Leptos client-side (WebAssembly) app over the server's HTTP API.
//!
//! Requests and responses are typed through `njord-model` and nothing else from the backend;
//! [`api`] makes every `/v1` call. App-wide state lives in [`store`], and the map page is driven
//! by [`controller`]. MapLibre GL and Bootstrap stay as JavaScript, vendored under `vendor/` and
//! bound in [`maplibre`] and [`bootstrap`].
//!
//! [`start`] mounts the app; `trunk build --release` in this crate's directory writes the site to
//! `dist/`, which the server serves through its `webStaticContent` setting.
//!
//! [`routing`] and [`display`] are pure and tested natively; everything else needs a browser.

pub mod api;
pub mod bootstrap;
pub mod controller;
pub mod display;
pub mod maplibre;
pub mod routing;
pub mod state;
pub mod storage;
pub mod store;
mod ui;

/// Mounts the app on `<body>`. The state is created inside the mount's root owner, which lives
/// as long as the page.
pub fn start() {
    leptos::mount::mount_to_body(|| {
        store::Store::init();
        leptos::view! { <ui::App/> }
    });
}

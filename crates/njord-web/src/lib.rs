//! The Njord web UI (RUST_PORT_PLAN.md §7): a Leptos client-side app over the HTTP API, typed
//! through `njord-model` and nothing else from the backend. MapLibre GL and Bootstrap stay as
//! JavaScript, vendored under `vendor/` and bound in [`maplibre`] and [`bootstrap`].
//!
//! [`routing`] and [`kotlin`] are pure and tested natively; everything else needs a browser.

pub mod api;
pub mod bootstrap;
pub mod controller;
pub mod kotlin;
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

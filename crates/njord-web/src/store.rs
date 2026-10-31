//! The view models of the Kotlin UI (`viewmodel/*`) as one set of app-wide Leptos signals. They
//! are created once by [`Store::init`], before the app mounts, and live as long as the page, as
//! the Kotlin singletons did. Network loads start the first time a page needs them, so each page
//! issues the same `/v1` requests the Kotlin page did.

use std::cell::{Cell, RefCell};
use std::sync::LazyLock;

use geojson::{Bbox, Feature, Geometry};
use leptos::prelude::*;
use leptos::task::spawn_local;
use njord_model::{
    AdminResponse, ChartCatalog, ChartItem, ColorLibrary, Depth, EncUpload, IconInfo, IndexMap,
    JsonObject, S57Attribute, S57ExpectedInput, S57Object, Sprite, Theme, ThemeMode, WsMsg,
};
use serde::{Deserialize, Serialize};
use wasm_bindgen::JsCast;
use wasm_bindgen::prelude::*;

use crate::api;
use crate::controller;
use crate::routing::{NjordRoute, QueryParams, RouteRegistry, Routing, query_string};
use crate::state::Async;
use crate::storage;

pub static REGISTRY: LazyLock<RouteRegistry> = LazyLock::new(RouteRegistry::default);

#[derive(Clone, Debug, PartialEq)]
pub struct RouteState {
    pub current: Routing,
    pub can_go_back: bool,
    pub replace: bool,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct AdminState {
    pub signature: Async<AdminResponse>,
    /// Seconds until the signature's `expirationDate`, recomputed every second.
    pub remaining: Option<i64>,
}

impl AdminState {
    pub fn is_logged_in(&self) -> bool {
        self.signature.is_complete()
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub struct MapLocation {
    pub longitude: f64,
    pub latitude: f64,
    pub zoom: f64,
}

impl Default for MapLocation {
    fn default() -> Self {
        MapLocation {
            longitude: -118.512,
            latitude: 33.96832,
            zoom: 12.0,
        }
    }
}

/// A feature from `queryRenderedFeatures` (`MapGeoJsonFeature`).
#[derive(Clone, Debug, Default, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MapFeature {
    #[serde(default)]
    pub geometry: Option<Geometry>,
    #[serde(default)]
    pub properties: JsonObject,
    #[serde(default)]
    pub source_layer: Option<String>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct ChartState {
    pub location: MapLocation,
    pub bounds: Option<Bbox>,
    pub highlight: Option<Feature>,
    pub theme: Theme,
    pub depth: Depth,
    pub query: Vec<MapFeature>,
    pub region: Option<String>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct ChartObjectsState {
    pub objects: Async<IndexMap<String, S57Object>>,
    pub attributes: Async<IndexMap<String, S57Attribute>>,
    pub expected_inputs: Async<IndexMap<String, Vec<S57ExpectedInput>>>,
    pub colors: Async<ColorLibrary>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct SpriteState {
    pub sheet: Async<IndexMap<Sprite, IconInfo>>,
    pub sheet_url: Option<String>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct CatalogState {
    pub delete_progress: Option<i32>,
    pub catalog: Async<ChartCatalog>,
    pub filter: Option<String>,
    pub filtered: Vec<ChartItem>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct InstallState {
    pub upload_progress: i32,
    pub enc_upload: Async<EncUpload>,
    pub info: Option<WsMsg>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct NavBarState {
    pub custom_colors: Async<Vec<String>>,
    pub regions: Async<Vec<String>>,
}

#[derive(Clone, Copy)]
pub struct Store {
    pub route: RwSignal<RouteState>,
    pub admin: RwSignal<AdminState>,
    pub chart: RwSignal<ChartState>,
    pub objects: RwSignal<ChartObjectsState>,
    pub sprites: RwSignal<SpriteState>,
    pub catalog: RwSignal<CatalogState>,
    pub install: RwSignal<InstallState>,
    pub nav: RwSignal<NavBarState>,
}

thread_local! {
    static STORE: Cell<Option<Store>> = const { Cell::new(None) };
    static SOCKET: RefCell<Option<web_sys::WebSocket>> = const { RefCell::new(None) };
    static SPRITES_STARTED: Cell<bool> = const { Cell::new(false) };
}

/// The app state. Panics before [`Store::init`].
pub fn store() -> Store {
    STORE.with(Cell::get).expect("Store::init")
}

fn window() -> web_sys::Window {
    web_sys::window().expect("window")
}

pub fn current_href() -> String {
    window().location().href().unwrap_or_default()
}

pub fn current_path() -> String {
    window().location().pathname().unwrap_or_default()
}

/// `registry.from(path)` with the query string of the current URL, as `QueryParams()` read it.
fn routing_here(path: &str) -> Routing {
    REGISTRY.from(path, QueryParams::new(query_string(&current_href())))
}

/// `mapLocation()`: `lat`/`lng`/`z` of an `/enc` URL, else the stored location.
fn initial_location(route: &Routing) -> Option<MapLocation> {
    let from_url = (route.route == NjordRoute::Enc)
        .then_some(route.params.as_ref())
        .flatten()
        .and_then(|p| {
            let num = |k: &str| p.get(k).and_then(|v| v.parse::<f64>().ok());
            Some(MapLocation {
                longitude: num("lng")?,
                latitude: num("lat")?,
                zoom: num("z")?,
            })
        });
    from_url.or_else(|| storage::get("MapLocation"))
}

impl Store {
    /// Creates the state and the effects that tie it to the browser: history, the stored map
    /// location, the admin clock and the sprite sheet of the current theme.
    pub fn init() -> Store {
        let current = routing_here(&current_path());
        let chart = ChartState {
            location: initial_location(&current).unwrap_or_default(),
            bounds: None,
            highlight: None,
            theme: storage::get_theme().unwrap_or(Theme::Mode(ThemeMode::Day)),
            depth: storage::get("Depth").unwrap_or(Depth::Feet),
            query: Vec::new(),
            region: None,
        };
        let admin = AdminState {
            signature: storage::get::<AdminResponse>("AdminResponse")
                .map_or(Async::Uninitialized, Async::Complete),
            remaining: None,
        };
        let store = Store {
            route: RwSignal::new(RouteState {
                current,
                can_go_back: false,
                replace: true,
            }),
            admin: RwSignal::new(admin),
            chart: RwSignal::new(chart),
            objects: RwSignal::new(ChartObjectsState::default()),
            sprites: RwSignal::new(SpriteState::default()),
            catalog: RwSignal::new(CatalogState::default()),
            install: RwSignal::new(InstallState::default()),
            nav: RwSignal::new(NavBarState::default()),
        };
        STORE.with(|s| s.set(Some(store)));
        store.connect_history();
        store.connect_chart();
        store.verify_stored_admin();
        store.start_admin_clock();
        store
    }

    // --- Routing (`RouteViewModel`) ---

    fn connect_history(self) {
        let on_pop = Closure::<dyn FnMut()>::new(move || {
            self.replace_route(routing_here(&current_path()));
        });
        let _ =
            window().add_event_listener_with_callback("popstate", on_pop.as_ref().unchecked_ref());
        on_pop.forget();

        Effect::new(move || {
            let state = self.route.get();
            let Ok(history) = window().history() else {
                return;
            };
            let url = JsValue::from_str(&state.current.path_and_params());
            let title = state.current.route.title();
            let _ = if state.replace {
                history.replace_state_with_url(&JsValue::NULL, title, url.as_string().as_deref())
            } else {
                history.push_state_with_url(&JsValue::NULL, title, url.as_string().as_deref())
            };
        });
    }

    pub fn replace_route(self, routing: Routing) {
        self.route.update(|s| {
            s.current = routing;
            s.replace = true;
        });
    }

    /// Navigates to `path`, which may carry its own query string. (Kotlin kept the query string
    /// of the page being left, so `/enc?lat=…` followed by "About" landed on `/?lat=…`.)
    pub fn push_route(self, path: &str) {
        let (path, query) = match path.split_once('?') {
            Some((p, q)) => (p, Some(q.to_owned())),
            None => (path, None),
        };
        if path == self.route.with_untracked(|s| s.current.path.clone()) {
            return;
        }
        let routing = REGISTRY.from(path, QueryParams::new(query));
        self.route.update(|s| {
            s.current = routing;
            s.can_go_back = true;
            s.replace = false;
        });
    }

    pub fn push_page(self, route: NjordRoute) {
        if self.route.with_untracked(|s| s.current.route) == route {
            return;
        }
        self.route.update(|s| {
            s.current = Routing {
                route,
                path: route.path_pattern().to_owned(),
                args: None,
                params: Some(QueryParams::default()),
            };
            s.can_go_back = true;
            s.replace = false;
        });
    }

    // --- Admin (`AdminViewModel`) ---

    fn verify_stored_admin(self) {
        let Some(stored) = self.admin.with_untracked(|a| a.signature.value().cloned()) else {
            return;
        };
        spawn_local(async move {
            let verified = api::verify_admin(&stored.signature).await;
            self.admin
                .update(|a| a.signature = Async::from_result(verified, None));
        });
    }

    fn start_admin_clock(self) {
        let tick = Closure::<dyn FnMut()>::new(move || {
            let now = (js_sys::Date::now() / 1000.0).floor() as i64;
            let remaining = self.admin.with_untracked(|a| {
                a.signature
                    .value()
                    .map(|s| s.signature.expiration_date.as_second() - now)
            });
            if remaining != self.admin.with_untracked(|a| a.remaining) {
                self.admin.update(|a| a.remaining = remaining);
            }
        });
        let _ = window().set_interval_with_callback_and_timeout_and_arguments_0(
            tick.as_ref().unchecked_ref(),
            1000,
        );
        tick.forget();
    }

    pub fn signature(self) -> Option<AdminResponse> {
        self.admin.with_untracked(|a| a.signature.value().cloned())
    }

    /// `GET /v1/admin` (the browser asks for the basic-auth login), then verify and remember it.
    pub fn login(self) {
        spawn_local(async move {
            let signature = match api::admin().await {
                Ok(admin) => api::verify_admin(&admin.signature).await,
                Err(e) => Err(e),
            };
            let signature = Async::from_result(signature, None);
            if let Some(s) = signature.value() {
                storage::set("AdminResponse", Some(s));
            }
            self.admin.update(|a| a.signature = signature);
        });
    }

    pub fn logout(self) {
        storage::set::<AdminResponse>("AdminResponse", None);
        self.admin.update(|a| a.signature = Async::Uninitialized);
    }

    // --- The chart (`ChartViewModel`) ---

    fn connect_chart(self) {
        // Keep the URL and the stored location in step with the map.
        let location = Memo::new(move |_| self.chart.with(|c| c.location));
        Effect::new(move || {
            let loc = location.get();
            if self.route.with_untracked(|r| r.current.route) == NjordRoute::Enc {
                self.replace_route(REGISTRY.from(
                    "/enc",
                    QueryParams::new(Some(format!(
                        "lat={}&lng={}&z={}",
                        loc.latitude, loc.longitude, loc.zoom
                    ))),
                ));
            }
            storage::set("MapLocation", Some(&loc));
        });

        // The sprite sheet follows the theme mode, once the sprites page has been opened.
        let mode = Memo::new(move |_| self.chart.with(|c| c.theme.mode()));
        Effect::new(move || {
            let mode = mode.get();
            if SPRITES_STARTED.with(Cell::get) {
                self.select_sprite_sheet(mode);
            }
        });
    }

    pub fn on_map_move(self, location: MapLocation) {
        self.chart.update(|c| c.location = location);
    }

    pub fn set_query(self, query: Vec<MapFeature>) {
        self.chart.update(|c| c.query = query);
    }

    pub fn set_depth(self, depth: Depth) {
        storage::set("Depth", Some(&depth));
        let theme = self.chart.with_untracked(|c| c.theme.clone());
        controller::with(|c| c.set_style(&theme, depth));
        self.chart.update(|c| c.depth = depth);
    }

    pub fn set_theme(self, theme: Theme) {
        storage::set_theme(&theme);
        let depth = self.chart.with_untracked(|c| c.depth);
        controller::with(|c| c.set_style(&theme, depth));
        self.chart.update(|c| c.theme = theme);
    }

    /// `"Default"` selects the plain theme mode.
    pub fn set_custom_color(self, color: &str) {
        let mode = self.chart.with_untracked(|c| c.theme.mode());
        let theme = if color == "Default" {
            Theme::Mode(mode)
        } else {
            Theme::Custom(njord_model::CustomTheme {
                mode,
                name: color.to_owned(),
            })
        };
        self.set_theme(theme);
    }

    pub fn set_region(self, region: Option<String>) {
        controller::with(|c| c.set_region_filter(region.clone()));
        self.chart.update(|c| c.region = region);
    }

    pub fn clear_query(self) {
        self.chart.update(|c| c.query.clear());
    }

    /// Moves the map, marking the spot when `highlight` (the layer page's "ENC Zoom").
    pub fn set_location(self, location: MapLocation, highlight: bool) {
        let highlight = highlight
            .then(|| Feature::from(Geometry::new_point([location.longitude, location.latitude])));
        controller::with(|c| {
            c.move_to(location);
            if let Some(h) = &highlight {
                c.highlight(h);
            }
        });
        self.chart.update(|c| {
            c.location = location;
            c.highlight = highlight;
        });
    }

    /// Fits the map to `bounds` (`[west, south, east, north]`), outlining `highlight`.
    pub fn set_bounds(self, bounds: Option<Bbox>, highlight: Option<Feature>) {
        controller::with(|c| {
            if let Some(b) = &bounds {
                c.fit_bounds(b);
            }
            if let Some(h) = &highlight {
                c.highlight(h);
            }
        });
        self.chart.update(|c| {
            c.bounds = bounds;
            c.highlight = highlight;
        });
    }

    // --- S-57 catalogue and colours (`ChartObjectsViewModel`) ---

    pub fn ensure_objects(self) {
        if self
            .objects
            .with_untracked(|o| o.objects == Async::Uninitialized)
        {
            self.reload_objects();
        }
    }

    pub fn reload_objects(self) {
        self.objects.update(|o| {
            o.objects = Async::Loading(None);
            o.attributes = Async::Loading(None);
            o.expected_inputs = Async::Loading(None);
        });
        spawn_local(async move {
            let objects = Async::from_result(api::s57_objects().await, None);
            let attributes = Async::from_result(api::s57_attributes().await, None);
            let expected_inputs = Async::from_result(api::expected_inputs().await, None);
            let colors = Async::from_result(api::colors().await, None);
            self.objects.set(ChartObjectsState {
                objects,
                attributes,
                expected_inputs,
                colors,
            });
        });
    }

    // --- Sprites (`SpriteViewModel`) ---

    pub fn ensure_sprites(self) {
        if !SPRITES_STARTED.with(|s| s.replace(true)) {
            self.select_sprite_sheet(self.chart.with_untracked(|c| c.theme.mode()));
        }
    }

    pub fn reload_sprites(self) {
        self.select_sprite_sheet(self.chart.with_untracked(|c| c.theme.mode()));
    }

    fn select_sprite_sheet(self, mode: ThemeMode) {
        self.sprites.set(SpriteState {
            sheet: Async::Loading(None),
            sheet_url: None,
        });
        spawn_local(async move {
            let sheet = Async::from_result(api::sprite_sheet(mode).await, None);
            self.sprites.set(SpriteState {
                sheet,
                sheet_url: Some(api::sprite_sheet_png(mode)),
            });
        });
    }

    // --- Nav bar (`NavBarViewModel`) ---

    pub fn load_nav(self) {
        self.nav.update(|n| {
            n.custom_colors = Async::Loading(None);
            n.regions = Async::Loading(None);
        });
        spawn_local(async move {
            let colors = api::custom_colors().await.map(|cc| {
                std::iter::once("Default".to_owned())
                    .chain(cc.keys().cloned())
                    .collect()
            });
            self.nav
                .update(|n| n.custom_colors = Async::from_result(colors, None));
        });
        spawn_local(async move {
            let regions = api::regions().await.map(|list| {
                let mut names: Vec<String> = list
                    .into_iter()
                    .map(|r| r.name)
                    .filter(|n| n != "WORLD")
                    .collect();
                names.sort();
                names
            });
            self.nav
                .update(|n| n.regions = Async::from_result(regions, None));
        });
    }

    // --- Chart catalog (`ChartCatalogViewModel`) ---

    pub fn ensure_catalog(self) {
        if self
            .catalog
            .with_untracked(|c| c.catalog == Async::Uninitialized)
        {
            self.reload_catalog();
        }
    }

    pub fn reload_catalog(self) {
        self.catalog.set(CatalogState {
            catalog: Async::Loading(None),
            ..CatalogState::default()
        });
        spawn_local(async move {
            let catalog = Async::from_result(api::chart_catalog("").await, None);
            self.catalog.update(|s| {
                s.filtered = filter_items(s.filter.as_deref(), catalog.value());
                s.catalog = catalog;
            });
        });
    }

    pub fn load_more_charts(self) {
        let current = self.catalog.with_untracked(|s| s.catalog.clone());
        match current {
            Async::Loading(_) => {}
            Async::Uninitialized => self.reload_catalog(),
            Async::Complete(_) | Async::Fail { .. } => {
                let previous = current.into_value();
                let page = previous
                    .as_ref()
                    .map(|c| c.page.clone())
                    .unwrap_or_default();
                let next = previous
                    .as_ref()
                    .and_then(|c| c.next_name.clone())
                    .unwrap_or_default();
                self.catalog
                    .update(|s| s.catalog = Async::Loading(previous));
                spawn_local(async move {
                    let combined = Async::from_result(api::chart_catalog(&next).await, None).map(
                        |mut response| {
                            let mut all = page;
                            all.append(&mut response.page);
                            response.page = all;
                            response
                        },
                    );
                    self.catalog.update(|s| {
                        s.filtered = filter_items(s.filter.as_deref(), combined.value());
                        s.catalog = combined;
                    });
                });
            }
        }
    }

    pub fn filter_charts(self, value: &str) {
        let filter = (!value.trim().is_empty()).then(|| value.to_owned());
        self.catalog.update(|s| {
            s.filtered = filter_items(filter.as_deref(), s.catalog.value());
            s.filter = filter;
        });
    }

    /// Deletes every chart the filter shows, one `DELETE /v1/chart` at a time.
    pub fn delete_filtered_charts(self) {
        let Some(signature) = self.signature() else {
            return;
        };
        let delete = self.catalog.with_untracked(|s| s.filtered.clone());
        self.catalog.update(|s| s.delete_progress = Some(1));
        spawn_local(async move {
            let total = delete.len();
            for (i, item) in delete.iter().enumerate() {
                let _ = api::delete_chart(&signature, &item.name).await;
                let progress = ((i + 1) as f32 / total as f32 * 100.0) as i32;
                self.catalog.update(|s| s.delete_progress = Some(progress));
            }
        });
    }

    // --- Chart installer (`ChartInstallViewModel`) ---

    pub fn connect_install(self, logged_in: bool) {
        if logged_in {
            self.setup_socket();
        }
    }

    /// `DELETE /v1/enc_save`: aborts an ingest or clears a finished report.
    pub fn reset_install(self) {
        let Some(signature) = self.signature() else {
            return;
        };
        spawn_local(async move {
            let _ = api::reset_chart_install(&signature).await;
            self.setup_socket();
        });
    }

    fn setup_socket(self) {
        if SOCKET.with(|s| s.borrow().is_some()) {
            return;
        }
        let Some(uri) = websocket_uri(self.signature()) else {
            return;
        };
        let Ok(ws) = web_sys::WebSocket::new(&uri) else {
            return;
        };
        let closed = Closure::<dyn FnMut()>::new(move || {
            SOCKET.with(|s| s.borrow_mut().take());
        });
        ws.set_onclose(Some(closed.as_ref().unchecked_ref()));
        ws.set_onerror(Some(closed.as_ref().unchecked_ref()));
        closed.forget();
        let on_message = Closure::<dyn FnMut(web_sys::MessageEvent)>::new(
            move |event: web_sys::MessageEvent| {
                let Some(text) = event.data().as_string() else {
                    return;
                };
                match serde_json::from_str::<WsMsg>(&text) {
                    Ok(msg) => self.install.update(|s| {
                        if msg == WsMsg::Idle {
                            s.enc_upload = Async::Uninitialized;
                        }
                        s.info = Some(msg);
                    }),
                    Err(e) => web_sys::console::log_1(&format!("error: {e}\nevent: {text}").into()),
                }
            },
        );
        ws.set_onmessage(Some(on_message.as_ref().unchecked_ref()));
        on_message.forget();
        SOCKET.with(|s| *s.borrow_mut() = Some(ws));
    }

    pub fn upload(self, file: web_sys::File) {
        if self
            .install
            .with_untracked(|s| s.enc_upload == Async::Uninitialized)
        {
            self.start_upload(file);
        }
    }

    /// `POST /v1/enc_save` through an `XMLHttpRequest`, for its upload progress events.
    fn start_upload(self, file: web_sys::File) {
        let Some(signature) = self.signature() else {
            return;
        };
        self.install.update(|s| s.enc_upload = Async::Loading(None));
        let Ok(xhr) = web_sys::XmlHttpRequest::new() else {
            return;
        };
        let on_progress =
            Closure::<dyn FnMut(web_sys::ProgressEvent)>::new(move |e: web_sys::ProgressEvent| {
                let percent = (e.loaded() / e.total() * 100.0) as i32;
                self.install.update(|s| s.upload_progress = percent);
            });
        if let Ok(upload) = xhr.upload() {
            let _ = upload
                .add_event_listener_with_callback("progress", on_progress.as_ref().unchecked_ref());
        }
        on_progress.forget();
        let request = xhr.clone();
        let on_load = Closure::<dyn FnMut()>::new(move || {
            let status = request.status().unwrap_or(0);
            let body = request.response_text().ok().flatten().unwrap_or_default();
            let result = match serde_json::from_str::<EncUpload>(&body) {
                Ok(upload) if (200..300).contains(&status) => Async::Complete(upload),
                // Kotlin failed to decode the error body and stayed on the progress bar.
                _ => Async::Fail {
                    value: None,
                    message: format!("{status} - Network status"),
                },
            };
            self.install.update(|s| {
                s.enc_upload = result;
                s.upload_progress = 100;
            });
        });
        let _ = xhr.add_event_listener_with_callback("load", on_load.as_ref().unchecked_ref());
        on_load.forget();
        let url = format!(
            "/v1/enc_save?signature={}&filename={}",
            signature.signature_encoded,
            js_sys::encode_uri_component(&file.name())
        );
        if xhr.open_with_async("POST", &url, true).is_ok() {
            let _ = xhr.send_with_opt_blob(Some(&file));
        }
    }
}

fn filter_items(filter: Option<&str>, catalog: Option<&ChartCatalog>) -> Vec<ChartItem> {
    let Some(catalog) = catalog else {
        return Vec::new();
    };
    let needle = filter.map(str::to_lowercase);
    catalog
        .page
        .iter()
        .filter(|item| {
            needle
                .as_ref()
                .is_none_or(|n| item.name.to_lowercase().contains(n))
        })
        .cloned()
        .collect()
}

/// The ingest status socket. Under the dev server on :8080 it goes straight to the API on :9000.
fn websocket_uri(signature: Option<AdminResponse>) -> Option<String> {
    let sig = signature?;
    let location = window().location();
    let host = location.host().ok()?;
    let host = if host.ends_with(":8080") {
        format!("{}:9000", location.hostname().ok()?)
    } else {
        host
    };
    let scheme = if location.protocol().ok()? == "https:" {
        "wss"
    } else {
        "ws"
    };
    Some(format!(
        "{scheme}://{host}/v1/ws/enc_process?signature={}",
        sig.signature_encoded
    ))
}

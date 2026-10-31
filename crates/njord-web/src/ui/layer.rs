//! `LayerPage.js.kt`: `/layer/<OBJL>`, where a layer's features are, five per page.

use leptos::prelude::*;
use leptos::task::spawn_local;
use njord_model::LayerQueryResultPage;
use serde_json::Value;

use crate::api;
use crate::routing::NjordRoute;
use crate::state::Async;
use crate::store::{MapLocation, store};
use crate::ui::components::render_async;

fn is_empty(v: &Value) -> bool {
    match v {
        Value::Array(a) => a.is_empty(),
        Value::Object(o) => o.is_empty(),
        Value::String(s) => s.is_empty(),
        Value::Null => true,
        Value::Bool(_) | Value::Number(_) => false,
    }
}

#[component]
pub fn LayerPage(name: String) -> impl IntoView {
    let s = store();
    let pages = RwSignal::new(Async::<Vec<LayerQueryResultPage>>::Uninitialized);
    let layer = name.clone();
    let reload = move || {
        let layer = layer.clone();
        pages.set(Async::Loading(None));
        spawn_local(async move {
            let first = Async::from_result(api::features_by_layer(&layer, 0).await, None);
            let _ = pages.try_set(first.map(|p| vec![p]));
        });
    };
    reload();
    let layer = name.clone();
    let next_page = move || {
        let Async::Complete(list) = pages.get_untracked() else {
            return;
        };
        let layer = layer.clone();
        spawn_local(async move {
            let id = list.last().map_or(0, |p| p.last_id);
            let next = Async::from_result(api::features_by_layer(&layer, id).await, None);
            let _ = pages.try_set(next.map(|p| {
                let mut list = list;
                list.push(p);
                list
            }));
        });
    };
    let title = format!("Chart Locations for layer {name}");
    view! {
        <h1>{title}</h1>
        {move || {
            let next_page = next_page.clone();
            render_async(pages.get(), reload.clone(), move |pages| {
                let more = pages.last().is_some_and(|p| p.last_id != 0);
                let more_button = move || {
                    let next_page = next_page.clone();
                    more.then(|| view! {
                        <button class="btn btn-outline-success btn-sm" on:click=move |_| next_page()>
                            "Load more"
                        </button>
                    })
                };
                let items: Vec<_> = pages.into_iter().flat_map(|p| p.items).collect();
                let count = format!("{} feature(s) found", items.len());
                let list = items
                    .into_iter()
                    .map(|item| {
                        let location = MapLocation {
                            latitude: item.lat,
                            longitude: item.lng,
                            zoom: item.zoom as f64,
                        };
                        let props = item
                            .props
                            .iter()
                            .filter(|(_, v)| !is_empty(v))
                            .map(|(k, v)| view! { <div><b>{format!("{k}: {v}")}</b></div> })
                            .collect_view();
                        view! {
                            <li class="my-1">
                                {format!(
                                    "{} {} {} {} {} ",
                                    item.chart_name, item.lat, item.lng, item.zoom, item.geom_type
                                )}
                                <button
                                    class="btn btn-outline-secondary btn-sm"
                                    on:click=move |_| {
                                        s.set_location(location, true);
                                        s.push_page(NjordRoute::Enc);
                                    }
                                >
                                    "ENC Zoom"
                                </button>
                                {props}
                            </li>
                        }
                    })
                    .collect_view();
                view! {
                    <b>{count}</b>
                    <br/>
                    {more_button()}
                    <ol>{list}</ol>
                    {more_button()}
                }
                .into_any()
            })
        }}
    }
}

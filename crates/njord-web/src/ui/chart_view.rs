//! The map page (`/enc`) and its feature query modal.

use leptos::html;
use leptos::prelude::*;
use leptos::task::spawn_local;
use njord_model::{Color, IndexMap, S57Attribute, ThemeMode};
use serde_json::Value;
use wasm_bindgen::JsCast;

use crate::api;
use crate::controller;
use crate::display::mode_name;
use crate::state::Async;
use crate::store::{MapFeature, store};
use crate::ui::components::{
    Accordion, AccordionItem, ErrorDisplay, Link, LoadingSpinner, Modal, modal_hide, modal_show,
    render_async,
};

#[component]
pub fn ChartView() -> impl IntoView {
    let s = store();
    s.ensure_objects();
    let container = NodeRef::<html::Div>::new();
    Effect::new(move || {
        if let Some(div) = container.get() {
            let element: web_sys::HtmlElement = div.unchecked_into();
            controller::with(|c| c.create(element));
        }
    });
    on_cleanup(|| controller::with(controller::Controller::dispose));

    let query = Memo::new(move |_| s.chart.with(|c| c.query.clone()));
    Effect::new(move || {
        if query.with(Vec::is_empty) {
            modal_hide();
        } else {
            modal_show();
        }
    });

    view! {
        <div class="nj-warning shrink-0 p-4">"EXPERIMENTAL! - NOT FOR NAVIGATION"</div>
        <div class="min-h-0 flex-1" node_ref=container></div>
        <Modal title="Chart Query" on_close=Callback::new(move |_| s.clear_query())>
            {move || chart_query(query.get())}
        </Modal>
    }
}

const SKIP_KEYS: [&str; 5] = ["SY", "AP", "AC", "LC", "LP"];

/// `JsonPrimitive.content` for a primitive (a string unquoted), JSON text otherwise.
fn value_str(v: &Value) -> String {
    match v {
        Value::String(s) => s.clone(),
        other => other.to_string(),
    }
}

fn chart_query(features: Vec<MapFeature>) -> AnyView {
    let s = store();
    let objects = s.objects.get();
    let mode = s.chart.with(|c| c.theme.mode());
    let theme_colors = objects
        .colors
        .clone()
        .map_not_null(|c| c.get(&mode).cloned());
    let reload = move || s.reload_objects();
    match (objects.objects, theme_colors) {
        (Async::Complete(obj), Async::Complete(colors)) => {
            let attributes = objects.attributes;
            let mut names: Vec<String> = Vec::new();
            for f in &features {
                if let Some(name) = f.properties.get("NAME").map(value_str)
                    && !names.contains(&name)
                {
                    names.push(name);
                }
            }
            let mut items: Vec<AccordionItem> = names
                .into_iter()
                .map(|name| AccordionItem {
                    title: format!("Chart name:({name})"),
                    body: view! { <ChartInfoList name=name/> }.into_any(),
                })
                .collect();
            items.extend(features.into_iter().filter_map(|feature| {
                let layer = feature.source_layer.clone()?;
                let title = match obj.get(&layer) {
                    Some(o) => format!("{layer} - ({})", o.object_class),
                    None => layer,
                };
                Some(AccordionItem {
                    title,
                    body: feature_body(feature, mode, &colors, attributes.clone(), reload),
                })
            }));
            view! { <Accordion items=items/> }.into_any()
        }
        (Async::Loading(_), _) | (_, Async::Loading(_)) => view! { <LoadingSpinner/> }.into_any(),
        (Async::Fail { message, .. }, _) | (_, Async::Fail { message, .. }) => {
            view! { <ErrorDisplay message=message on_ok=Callback::new(move |_| reload())/> }
                .into_any()
        }
        _ => ().into_any(),
    }
}

fn feature_body(
    feature: MapFeature,
    mode: ThemeMode,
    colors: &IndexMap<Color, String>,
    attributes: Async<IndexMap<String, S57Attribute>>,
    reload: impl Fn() + Copy + Send + Sync + 'static,
) -> AnyView {
    let prop = |k: &str| feature.properties.get(k).map(value_str);
    let geometry_type = feature
        .geometry
        .as_ref()
        .map_or("?".to_owned(), |g| g.value.type_name().to_owned());
    let position = feature.geometry.as_ref().and_then(|g| match &g.value {
        geojson::GeometryValue::Point { coordinates: p } if p.len() >= 2 => Some(view! {
            <p><b>"Position: "</b>{format!("{}, {}", p[1], p[0])}</p>
        }),
        _ => None,
    });
    let properties = feature.properties.clone();
    let attribute_list = render_async(attributes, reload, move |att| {
        properties
            .iter()
            .filter(|(k, _)| !SKIP_KEYS.contains(&k.as_str()))
            .map(|(key, value)| match att.get(key) {
                Some(a) => view! {
                    <li><b>{key.clone()}</b>{format!(" - {}", a.attribute)}": "<i>{value.to_string()}</i></li>
                }
                    .into_any(),
                None => view! { <li>{key.clone()}":"{value.to_string()}</li> }.into_any(),
            })
            .collect_view()
            .into_any()
    });
    view! {
        <div>
            <p><b>"Geometry: "</b>{geometry_type}</p>
            {position}
            <b>"Properties:"</b>
            {prop("SY").map(|v| view! { <DisplaySymbol key="SY" desc="Symbol" symbol=v/> })}
            {prop("AP").map(|v| view! { <DisplaySymbol key="AP" desc="Area Pattern" symbol=v/> })}
            {prop("AC").map(|v| display_color("AC", "Area Color", &v, mode, colors))}
            {prop("LC").map(|v| display_color("LC", "Line Color", &v, mode, colors))}
            {prop("LP").map(|v| view! { <DisplaySymbol key="LP" desc="Line Pattern" symbol=v/> })}
        </div>
        {attribute_list}
    }
    .into_any()
}

#[component]
fn DisplaySymbol(key: &'static str, desc: &'static str, symbol: String) -> impl IntoView {
    view! {
        <li>
            <b>{key}</b>
            {format!(" - {desc} {symbol} ")}
            <img src=format!("/v1/icon/{symbol}.png") alt=symbol.clone()/>
        </li>
    }
}

fn display_color(
    key: &'static str,
    desc: &'static str,
    symbol: &str,
    mode: ThemeMode,
    colors: &IndexMap<Color, String>,
) -> AnyView {
    match symbol.parse::<Color>().ok().and_then(|c| colors.get(&c)) {
        Some(hex) => {
            let circle = format!(
                r#"<svg width="20" height="20"><circle cx="10" cy="10" r="10" fill="{hex}"></circle></svg>"#
            );
            view! {
                <li>
                    <b>{key}</b>
                    {format!(" - {desc} {symbol} ({}) {hex} ", mode_name(mode))}
                    <span inner_html=circle></span>
                </li>
            }
            .into_any()
        }
        None => {
            view! { <li><b>{key}</b>{format!(" ERROR! finding {desc} {symbol}")}</li> }.into_any()
        }
    }
}

/// `ChartInfoList`: the summary of one chart a queried feature belongs to.
#[component]
pub fn ChartInfoList(name: String) -> impl IntoView {
    let info = RwSignal::new(Async::Loading(None));
    let reload = move || {
        let name = name.clone();
        info.set(Async::Loading(None));
        spawn_local(async move {
            let _ = info.try_set(Async::from_result(api::chart_info(&name).await, None));
        });
    };
    reload();
    move || {
        render_async(info.get(), reload.clone(), |chart| {
            view! {
                <ol>
                    <li><Link label=chart.name.clone() path=format!("/chart/{}", chart.name)/></li>
                    <li><b>"File name: "</b>{chart.file_name}</li>
                    <li><b>"Scale: "</b>{chart.scale.to_string()}</li>
                    <li><b>"Updated: "</b>{chart.updated}</li>
                    <li><b>"Issued: "</b>{chart.issued}</li>
                    <li><b>"Calculated zoom: "</b>{chart.zoom.to_string()}</li>
                    <li><b>"Feature count: "</b>{chart.feature_count.to_string()}</li>
                </ol>
            }
            .into_any()
        })
    }
}

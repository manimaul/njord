//! The control panel (`/control/<tab>`) and its tabs: the chart catalog, the chart installer,
//! and the S-57 symbol, sprite and colour references.

use leptos::html;
use leptos::prelude::*;
use njord_model::{ChartCatalog, S57Object, WsMsg};

use crate::display::ws_msg_to_string;
use crate::state::Async;
use crate::store::{CatalogState, InstallState, store};
use crate::ui::components::{
    DropdownButton, ErrorDisplay, Link, LoadingSpinner, Modal, Progress, modal_hide, modal_show,
    render_async,
};

struct TabData {
    key: &'static str,
    sub_path: Option<&'static str>,
    title: &'static str,
}

const TABS: [TabData; 5] = [
    TabData {
        key: "charts_catalog",
        sub_path: None,
        title: "Chart Catalog",
    },
    TabData {
        key: "charts_installer",
        sub_path: None,
        title: "Chart Installer",
    },
    TabData {
        key: "symbols",
        sub_path: Some("ADMARE"),
        title: "Symbols",
    },
    TabData {
        key: "sprites",
        sub_path: None,
        title: "Sprites",
    },
    TabData {
        key: "colors",
        sub_path: None,
        title: "Colors",
    },
];

#[component]
pub fn ControlPanel(tab: String, path: String) -> impl IntoView {
    let _ = path;
    let s = store();
    let buttons = TABS
        .iter()
        .map(|t| {
            let active = t.key == tab;
            let target = match t.sub_path {
                Some(sub) => format!("/control/{}/{sub}", t.key),
                None => format!("/control/{}", t.key),
            };
            view! {
                <li class="nav-item">
                    <button
                        class=if active { "nav-link active" } else { "nav-link" }
                        aria-current=active.then_some("page")
                        on:click=move |_| s.push_route(&target)
                    >
                        {t.title}
                    </button>
                </li>
            }
        })
        .collect_view();
    let content = TABS.iter().find(|t| t.key == tab).map(|t| {
        let page = match t.key {
            "charts_catalog" => view! { <ChartCatalogTab/> }.into_any(),
            "charts_installer" => view! { <ChartInstaller/> }.into_any(),
            "symbols" => view! { <Symbols/> }.into_any(),
            "sprites" => view! { <Sprites/> }.into_any(),
            _ => view! { <Colors/> }.into_any(),
        };
        view! { <div class="mt-3">{page}</div> }
    });
    view! {
        <div class="container Content">
            <h1>"Control Panel"</h1>
            <ul class="nav nav-tabs">{buttons}</ul>
            {content}
        </div>
    }
}

// --- Chart catalog ---

fn chart_table(catalog: &ChartCatalog, state: &CatalogState) -> AnyView {
    let s = store();
    let total = catalog.total_chart_count;
    let rows = state
        .filtered
        .iter()
        .enumerate()
        .map(|(i, item)| {
            let count = state
                .filter
                .is_none()
                .then(|| format!(" ({} of {total})", i + 1));
            view! {
                <tr>
                    <td><Link label=item.name.clone() path=format!("/chart/{}", item.name)/>{count}</td>
                </tr>
            }
        })
        .collect_view();
    let more = if state.catalog.is_complete() && (catalog.page.len() as i32) < total {
        view! {
            <button class="btn btn-primary" on:click=move |_| s.load_more_charts()>"Load More..."</button>
        }
        .into_any()
    } else if state.catalog.is_loading() {
        view! { <LoadingSpinner/> }.into_any()
    } else {
        ().into_any()
    };
    view! {
        <table class="table table-striped table-bordered table-hover">
            <thead><tr><th>"Chart Name"</th></tr></thead>
            <tbody>{rows}</tbody>
        </table>
        {more}
    }
    .into_any()
}

#[component]
fn ChartCatalogTab() -> impl IntoView {
    let s = store();
    s.ensure_catalog();
    let body = move || {
        let state = s.catalog.get();
        let logged_in = s.admin.with(|a| a.is_logged_in());
        let catalog = state.catalog.value()?.clone();
        let total = catalog.total_chart_count;
        let controls = match state.delete_progress {
            Some(progress) => view! {
                <Progress title=format!("Deleting charts {progress}%") progress=progress/>
                {(progress == 100).then(|| view! {
                    <button
                        class="btn btn-sm btn-outline-secondary mt-3"
                        on:click=move |_| s.reload_catalog()
                    >
                        "Reload"
                    </button>
                })}
            }
            .into_any(),
            None => {
                let filter = state.filter.clone();
                let filter_box = (total > 0).then(|| {
                    let clear_class = if filter.is_some() {
                        "btn btn-outline-success"
                    } else {
                        "btn btn-outline-success disabled"
                    };
                    view! {
                        <div class="input-group my-3">
                            <input
                                type="text"
                                placeholder="Type to filter name..."
                                class="form-control"
                                prop:value=filter.clone().unwrap_or_default()
                                on:input=move |ev| s.filter_charts(&event_target_value(&ev))
                            />
                            <button class=clear_class on:click=move |_| s.filter_charts("")>"Clear"</button>
                        </div>
                    }
                });
                let delete_class = if logged_in && !state.filtered.is_empty() {
                    "btn btn-sm btn-danger mb-3 me-3"
                } else {
                    "btn btn-sm btn-danger disabled mb-3 me-3"
                };
                view! {
                    {filter_box}
                    <button class=delete_class on:click=|_| modal_show()>"Delete these"</button>
                    <button class="btn btn-sm btn-outline-secondary mb-3" on:click=move |_| s.reload_catalog()>
                        "Reload"
                    </button>
                    {(total > 0).then(|| chart_table(&catalog, &state))}
                }
                .into_any()
            }
        };
        Some(view! {
            <h1>{format!("Installed S57 ENCs: {total}")}</h1>
            {controls}
        })
    };
    let confirm = move || {
        s.catalog.with(|state| {
            format!(
                "Proceeding will delete {} of {} charts?",
                state.filtered.len(),
                state.catalog.value().map_or(0, |c| c.total_chart_count)
            )
        })
    };
    // The confirmation modal is rendered only once the catalog has loaded.
    view! {
        <Show when=move || s.catalog.with(|c| c.catalog.value().is_some())>
            <div>
                {body}
                <Modal title="Are you sure?">
                    <h6>{confirm}</h6>
                    <button
                        class="btn btn-danger btn-sm me-2"
                        on:click=move |_| {
                            modal_hide();
                            s.delete_filtered_charts();
                        }
                    >
                        "Confirm"
                    </button>
                    <button class="btn btn-success btn-sm" on:click=|_| modal_hide()>"Cancel"</button>
                </Modal>
            </div>
        </Show>
    }
}

// --- Chart installer ---

#[component]
fn ChartInstaller() -> impl IntoView {
    let s = store();
    let logged_in = Memo::new(move |_| s.admin.with(|a| a.is_logged_in()));
    Effect::new(move || s.connect_install(logged_in.get()));
    move || {
        if !logged_in.get() {
            return "Admin access required".into_any();
        }
        let state = s.install.get();
        match &state.info {
            None | Some(WsMsg::Idle) => match state.enc_upload {
                Async::Loading(_) => view! {
                    <Progress title="Uploading chart file" progress=state.upload_progress/>
                }
                .into_any(),
                Async::Fail { message, .. } => view! {
                    <ErrorDisplay message=message on_ok=Callback::new(move |_| s.reset_install())/>
                }
                .into_any(),
                _ => view! { <ChartInstallForm/> }.into_any(),
            },
            Some(_) => install_progress(&state),
        }
    }
}

fn install_progress(state: &InstallState) -> AnyView {
    let s = store();
    let (status, working) = match &state.info {
        Some(msg @ (WsMsg::CompletionReport { .. } | WsMsg::Error { .. })) => {
            (ws_msg_to_string(msg).into_any(), false)
        }
        Some(WsMsg::Extracting { progress }) => {
            let p = (*progress as f64 * 100.0) as i32;
            (
                view! { <Progress title="Extracting chart files" progress=p/> }.into_any(),
                true,
            )
        }
        Some(WsMsg::Info {
            feature,
            total_features,
            chart,
            total_charts,
        }) => {
            let p = (*feature as f64 / *total_features as f64 * 100.0) as i32;
            let title = format!(
                "Installed: features {feature} of {total_features} - charts {chart} of {total_charts}"
            );
            (
                view! { <Progress title=title progress=p/> }.into_any(),
                true,
            )
        }
        _ => (().into_any(), false),
    };
    view! {
        {status}
        <Modal title="Are you sure?">
            <h6>"This will halt the current chart installation leaving it partially completed!"</h6>
            <button
                class="btn btn-danger btn-sm me-2"
                on:click=move |_| {
                    s.reset_install();
                    modal_hide();
                }
            >
                "Confirm"
            </button>
            <button class="btn btn-success btn-sm" on:click=|_| modal_hide()>"Cancel"</button>
        </Modal>
        <br/>
        <button
            class="btn btn-danger"
            on:click=move |_| if working { modal_show() } else { s.reset_install() }
        >
            {if working { "Abort" } else { "Reset" }}
        </button>
    }
    .into_any()
}

#[component]
fn ChartInstallForm() -> impl IntoView {
    let s = store();
    let chosen = RwSignal::new(false);
    let input = NodeRef::<html::Input>::new();
    view! {
        <form>
            <div class="mb-3">
                <label class="form-label" for="encZip">"ENC Chart(s) Zip File"</label>
                <input
                    node_ref=input
                    type="file"
                    id="encZip"
                    accept="application/zip"
                    class="form-control"
                    name="enczip"
                    on:change=move |_| chosen.set(true)
                />
            </div>
            <button
                type="submit"
                class=move || if chosen.get() { "btn btn-primary" } else { "btn btn-primary disabled" }
                on:click=move |ev| {
                    ev.prevent_default();
                    if let Some(file) = input.get().and_then(|i| i.files()).and_then(|f| f.item(0)) {
                        s.upload(file);
                    }
                }
            >
                "Submit"
            </button>
        </form>
    }
}

// --- Symbols: S-57 objects and attributes ---

#[component]
fn Symbols() -> impl IntoView {
    store().ensure_objects();
    view! {
        <div class="row">
            <div class="col"><SymbolObjects/></div>
            <div class="col"><SymbolAttributes/></div>
        </div>
    }
}

/// `/control/symbols/<OBJL>/<ATTR>`: the selected object and attribute acronyms.
fn selection() -> (Option<String>, Option<String>) {
    store().route.with(|r| {
        let segments = r.current.path_segments();
        (
            segments.get(2).map(|s| s.to_string()),
            segments.get(3).map(|s| s.to_string()),
        )
    })
}

#[component]
fn SymbolObjects() -> impl IntoView {
    let s = store();
    view! {
        <h2>"S57 Object"</h2>
        {move || {
            let objects = s.objects.with(|o| o.objects.clone());
            render_async(objects, move || s.reload_objects(), |objects| {
                let Some(obj) = selection().0.and_then(|k| objects.get(&k).cloned()) else {
                    return ().into_any();
                };
                let options = objects.keys().cloned().collect();
                let acronym = obj.acronym.clone();
                view! {
                    <DropdownButton
                        selected=obj.acronym.clone()
                        options=options
                        on_select=Callback::new(move |key: String| s.push_route(&format!("/control/symbols/{key}")))
                    />
                    <br/>
                    <button
                        class="btn btn-primary"
                        on:click=move |_| s.push_route(&format!("/layer/{acronym}"))
                    >
                        "Locate on chart"
                    </button>
                    <br/>
                    <br/>
                    <p><b>"Geometry Primitives: "</b>{obj.primitives.join(", ")}</p>
                    <p><b>"Object: "</b>{obj.object_class.clone()}</p>
                    <p><b>"Acronym: "</b>{obj.acronym.clone()}</p>
                    <p><b>"Code: "</b>{obj.code.to_string()}</p>
                    {attribute_set(&obj)}
                }
                .into_any()
            })
        }}
    }
}

fn attribute_links(obj: &S57Object, attributes: &[String]) -> AnyView {
    let s = store();
    attributes
        .iter()
        .map(|att| {
            let path = format!("/control/symbols/{}/{att}", obj.acronym);
            let href = path.clone();
            view! {
                <span>
                    <a
                        href=href
                        on:click=move |ev| {
                            ev.prevent_default();
                            s.push_route(&path);
                        }
                    >
                        {att.clone()}
                    </a>
                    " "
                </span>
            }
        })
        .collect_view()
        .into_any()
}

fn attribute_set(obj: &S57Object) -> AnyView {
    view! {
        <p>
            <b>"Attribute A"</b>
            <br/>
            "(Attributes in this subset define the individual characteristics of the object.)"
            <br/>
            {attribute_links(obj, &obj.attribute_a)}
        </p>
        <p>
            <b>"Attribute B"</b>
            <br/>
            "(Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an information system.)"
            <br/>
            {attribute_links(obj, &obj.attribute_b)}
        </p>
        <p>
            <b>"Attribute C"</b>
            <br/>
            "(Attributes in this subset provide administrative information about the object and data describing it.)"
            <br/>
            {attribute_links(obj, &obj.attribute_c)}
        </p>
    }
    .into_any()
}

#[component]
fn SymbolAttributes() -> impl IntoView {
    let s = store();
    view! {
        <h2>"S57 Attribute"</h2>
        {move || {
            let state = s.objects.get();
            let (obj, att) = selection();
            // `expectedInputFlow`: the selected attribute, once the object and attribute
            // catalogues have loaded, with its expected inputs.
            let selected = match (&state.objects, &state.attributes) {
                (Async::Complete(objects), Async::Complete(attributes)) => obj
                    .filter(|o| objects.contains_key(o))
                    .and(att)
                    .and_then(|a| attributes.get(&a).cloned()),
                _ => None,
            };
            let combined = state.expected_inputs.and_then(|inputs| match selected {
                Some(att) => {
                    let list = inputs.get(&att.code.to_string()).cloned().unwrap_or_default();
                    Async::Complete((att, list))
                }
                None => Async::Uninitialized,
            });
            if combined == Async::Uninitialized {
                return view! { <span>"Attribute not selected"</span> }.into_any();
            }
            render_async(combined, move || s.reload_objects(), |(att, inputs)| {
                let table = (!inputs.is_empty()).then(|| {
                    let rows = inputs
                        .into_iter()
                        .map(|i| view! { <tr><td>{i.id.to_string()}</td><td>{i.meaning}</td></tr> })
                        .collect_view();
                    view! {
                        <table class="table">
                            <thead><tr><th>"ID"</th><th>"Meaning"</th></tr></thead>
                            <tbody>{rows}</tbody>
                        </table>
                    }
                });
                view! {
                    <p><b>"Attribute: "</b>{att.attribute}</p>
                    <p><b>"Acronym: "</b>{att.acronym}</p>
                    <p><b>"Code: "</b>{att.code.to_string()}</p>
                    <p><b>"Attribute Type: "</b>{att.attribute_type}</p>
                    <p><b>"Attribute Class: "</b>{att.class}</p>
                    <ul>
                        <li>"Attribute type: one-character code for the attribute type - there are six possible types:"</li>
                        <li>"Enumerated (\"E\") - the expected input is a number selected from a list of predefined attribute values; exactly one value must be chosen."</li>
                        <li>"List (\"L\") - the expected input is a list of one or more numbers selected from a list of pre-defined attribute values."</li>
                        <li>"Float (\"F\") - the expected input is a floating point numeric value with defined range, resolution, units and format."</li>
                        <li>"Integer (\"I\") - the expected input is an integer numeric value with defined range, units and format."</li>
                        <li>"Coded String (\"A\") - the expected input is a string of ASCII characters in a predefined format; the information is encoded according to defined coding systems."</li>
                        <li>"Free Text (\"S\") - the expected input is a free-format alphanumeric string; it may be a file name which points to a text or graphic file."</li>
                    </ul>
                    {table}
                }
                .into_any()
            })
        }}
    }
}

// --- Sprites and colours ---

#[component]
fn Sprites() -> impl IntoView {
    let s = store();
    s.ensure_sprites();
    view! {
        <h2>"Sprites"</h2>
        <div class="row">
            {move || {
                let state = s.sprites.get();
                let url = state.sheet_url.clone();
                render_async(state.sheet, move || s.reload_sprites(), move |sheet| {
                    sheet
                        .into_iter()
                        .map(|(sprite, info)| {
                            let mut style = format!("width: {}px; height: {}px;", info.width, info.height);
                            if let Some(url) = &url {
                                style.push_str(&format!(
                                    " background: url({url}); background-position: -{}px -{}px; display: inline-block; border-width: 4px;",
                                    info.x, info.y
                                ));
                            }
                            view! {
                                <div class="col mb-3">
                                    <div class="col-sm">{sprite.as_str()}<br/><div style=style></div></div>
                                </div>
                            }
                        })
                        .collect_view()
                        .into_any()
                })
            }}
        </div>
    }
}

#[component]
fn Colors() -> impl IntoView {
    let s = store();
    s.ensure_objects();
    view! {
        <h2>"Colors"</h2>
        <div class="row">
            {move || {
                let mode = s.chart.with(|c| c.theme.mode());
                let colors = s.objects.with(|o| o.colors.clone());
                render_async(colors, move || s.reload_objects(), move |colors| {
                    colors
                        .get(&mode)
                        .map(|map| {
                            map.iter()
                                .map(|(color, hex)| {
                                    let style = format!("height: 25px; width: 25px; background: {hex};");
                                    view! {
                                        <div class="col mb-3">
                                            {format!("{} {hex}", color.as_str())}
                                            <div style=style></div>
                                        </div>
                                    }
                                })
                                .collect_view()
                        })
                        .into_any()
                })
            }}
        </div>
    }
}

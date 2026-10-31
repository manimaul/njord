//! Shared widgets (`ui/LoadingSpinner`, `ErrorDisplay`, `Link`, `Modal`, `Accordion`,
//! `Dropdown`, `Progress`), with the Bootstrap markup of the Kotlin UI.

use std::cell::{Cell, RefCell};

use leptos::html;
use leptos::prelude::*;
use wasm_bindgen::JsCast;
use wasm_bindgen::prelude::*;

use crate::bootstrap;
use crate::state::Async;
use crate::store::store;

thread_local! {
    static NEXT_ID: Cell<u32> = const { Cell::new(0) };
    static MODAL: RefCell<ModalState> = RefCell::new(ModalState::default());
}

/// `"<prefix>-<n>"`, unique for the page.
pub fn next_id(prefix: &str) -> String {
    let n = NEXT_ID.with(|c| {
        c.set(c.get() + 1);
        c.get()
    });
    format!("{prefix}-{n}")
}

const SPINNER: &str = r#"<svg width="50" height="50"><defs><linearGradient id="grad"><stop offset="0%" stop-color="black"></stop><stop offset="100%" stop-color="black" stop-opacity="0"></stop></linearGradient></defs><circle cx="25" cy="25" r="20" fill="none" stroke="url(#grad)" stroke-width="5" stroke-dasharray="100.53096491487338" stroke-linecap="round"><animateTransform attributeName="transform" attributeType="XML" type="rotate" repeatCount="indefinite" from="0, 25, 25" to="360, 25, 25" dur="2s"></animateTransform></circle></svg>"#;

#[component]
pub fn LoadingSpinner() -> impl IntoView {
    view! { <span inner_html=SPINNER></span> }
}

#[component]
pub fn ErrorDisplay(#[prop(into)] message: String, on_ok: Callback<()>) -> impl IntoView {
    view! {
        <h1>"Something went wrong"</h1>
        <p><b>{message}</b></p>
        <button class="btn btn-danger" on:click=move |_| on_ok.run(())>"OK"</button>
    }
}

/// `Async.complete`: the value, an error with a retry button, a spinner, or nothing yet.
pub fn render_async<T>(
    value: Async<T>,
    reload: impl Fn() + Send + Sync + 'static,
    complete: impl FnOnce(T) -> AnyView,
) -> AnyView {
    match value {
        Async::Complete(v) => complete(v),
        Async::Fail { message, .. } => {
            view! { <ErrorDisplay message=message on_ok=Callback::new(move |_| reload())/> }
                .into_any()
        }
        Async::Loading(_) => view! { <LoadingSpinner/> }.into_any(),
        Async::Uninitialized => ().into_any(),
    }
}

/// An in-app link: pushes the route (and closes the open modal) instead of loading the page.
#[component]
pub fn Link(#[prop(into)] label: String, #[prop(into)] path: String) -> impl IntoView {
    let href = path.clone();
    view! {
        <a
            href=href
            on:click=move |ev| {
                ev.prevent_default();
                store().push_route(&path);
                modal_hide();
            }
        >
            {label}
        </a>
    }
}

#[component]
pub fn Progress(#[prop(into)] title: String, progress: i32) -> impl IntoView {
    view! {
        <h6>{title.clone()}</h6>
        <div
            class="progress"
            role="progressbar"
            aria-label=title
            aria-valuenow=progress.to_string()
            aria-valuemin="0"
            aria-valuemax="100"
        >
            <div style=format!("width: {progress}%;") class=format!("progress-bar w-{progress}")></div>
        </div>
    }
}

// --- Modal: one Bootstrap modal on screen at a time, shown and hidden from code ---

#[derive(Default)]
struct ModalState {
    shown: bool,
    modal: Option<bootstrap::Modal>,
}

fn modal_created(element: &web_sys::Element) {
    for (event, shown) in [("hidden.bs.modal", false), ("shown.bs.modal", true)] {
        let listener = Closure::<dyn FnMut()>::new(move || {
            MODAL.with(|m| m.borrow_mut().shown = shown);
        });
        let _ = element.add_event_listener_with_callback(event, listener.as_ref().unchecked_ref());
        listener.forget();
    }
    let modal = bootstrap::Modal::get_or_create_instance(element);
    MODAL.with(|m| m.borrow_mut().modal = Some(modal));
}

pub fn modal_show() {
    MODAL.with(|m| {
        let m = m.borrow();
        if !m.shown
            && let Some(modal) = &m.modal
        {
            modal.show();
        }
    });
}

pub fn modal_hide() {
    MODAL.with(|m| {
        let m = m.borrow();
        if m.shown
            && let Some(modal) = &m.modal
        {
            modal.hide();
        }
    });
}

#[component]
pub fn Modal(
    #[prop(into)] title: String,
    #[prop(optional)] on_close: Option<Callback<()>>,
    children: Children,
) -> impl IntoView {
    let id = next_id("modal");
    let title_id = format!("{id}-title");
    let labelled_by = title_id.clone();
    let node = NodeRef::<html::Div>::new();
    Effect::new(move || {
        if let Some(el) = node.get() {
            modal_created(&el);
        }
    });
    on_cleanup(|| MODAL.with(|m| *m.borrow_mut() = ModalState::default()));
    view! {
        <div
            node_ref=node
            id=id
            class="modal fade"
            tabindex="-1"
            aria-labelledby=labelled_by
            aria-hidden="true"
        >
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h1 class="modal-title fs-5" id=title_id>{title}</h1>
                        <button
                            type="button"
                            class="btn-close"
                            data-bs-dismiss="modal"
                            aria-label="Close"
                            on:click=move |_| {
                                if let Some(f) = on_close {
                                    f.run(())
                                }
                            }
                        ></button>
                    </div>
                    <div class="modal-body">{children()}</div>
                </div>
            </div>
        </div>
    }
}

// --- Accordion ---

pub struct AccordionItem {
    pub title: String,
    pub body: AnyView,
}

#[component]
pub fn Accordion(items: Vec<AccordionItem>) -> impl IntoView {
    let id = next_id("accordion");
    let parent = format!("#{id}");
    let items = items
        .into_iter()
        .enumerate()
        .map(|(i, item)| {
            let item_id = format!("item{i}{id}");
            let selector = format!("#{item_id}");
            view! {
                <div class="accordion-item">
                    <h2 class="accordion-header">
                        <button
                            class="accordion-button collapsed"
                            on:click=move |_| bootstrap::toggle_collapse(&selector)
                        >
                            {item.title}
                        </button>
                    </h2>
                    <div id=item_id class="accordion-collapse collapse" data-bs-parent=parent.clone()>
                        <div class="accordion-body">{item.body}</div>
                    </div>
                </div>
            }
        })
        .collect_view();
    view! { <div id=id class="accordion">{items}</div> }
}

// --- Dropdowns ---

/// A button dropdown with a filter box (the symbols page's object picker).
#[component]
pub fn DropdownButton(
    #[prop(into)] selected: String,
    options: Vec<String>,
    on_select: Callback<String>,
) -> impl IntoView {
    let expanded = RwSignal::new(false);
    let filter = RwSignal::new(String::new());
    let show = move || if expanded.get() { "show" } else { "hide" };
    view! {
        <div class="dropdown">
            <button
                class="btn btn-success dropdown-toggle"
                aria-expanded=move || expanded.get().to_string()
                data-bs-toggle="dropdown"
                on:click=move |_| expanded.update(|e| *e = !*e)
            >
                {selected}
            </button>
            <div class=move || format!("dropdown-menu {}", show())>
                <input
                    type="text"
                    class="mx-3 my-2 form-control w-auto"
                    placeholder="Type to filter..."
                    prop:value=move || filter.get()
                    on:input=move |ev| filter.set(event_target_value(&ev).trim().to_owned())
                />
                {move || {
                    let needle = filter.get().to_lowercase();
                    options
                        .iter()
                        .filter(|o| o.to_lowercase().contains(&needle))
                        .cloned()
                        .map(|option| {
                            let label = option.clone();
                            view! {
                                <a
                                    class="dropdown-item"
                                    href="#"
                                    on:click=move |ev| {
                                        ev.prevent_default();
                                        on_select.run(option.clone());
                                        expanded.set(false);
                                    }
                                >
                                    {label}
                                </a>
                            }
                        })
                        .collect_view()
                }}
            </div>
        </div>
    }
}

/// A nav bar dropdown; `options` are `(label, value)` pairs.
#[component]
pub fn NavDropdown<T>(
    #[prop(into)] title: Signal<String>,
    options: Vec<(String, T)>,
    on_select: Callback<T>,
) -> impl IntoView
where
    T: Clone + Send + Sync + 'static,
{
    let expanded = RwSignal::new(false);
    let show = move || if expanded.get() { "show" } else { "hide" };
    let items = options
        .into_iter()
        .enumerate()
        .map(|(i, (label, value))| {
            view! {
                <a
                    href="#"
                    data-rr-ui-dropdown-item=""
                    class="dropdown-item"
                    tabindex=i.to_string()
                    on:click=move |ev| {
                        ev.prevent_default();
                        on_select.run(value.clone());
                    }
                >
                    {label}
                </a>
            }
        })
        .collect_view();
    view! {
        <div
            class=move || format!("nav-item dropdown {}", show())
            on:click=move |_| expanded.update(|e| *e = !*e)
        >
            <a
                href="#"
                class="dropdown-toggle nav-link"
                tabindex="0"
                aria-expanded=move || expanded.get().to_string()
                role="button"
                on:click=|ev| ev.prevent_default()
            >
                {title}
            </a>
            <div
                class=move || format!("dropdown-menu {}", show())
                aria-labelledby="basic-nav-dropdown"
                data-bs-popper="static"
            >
                {items}
            </div>
        </div>
    }
}

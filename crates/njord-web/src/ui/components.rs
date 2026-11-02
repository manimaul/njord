//! Shared widgets (`LoadingSpinner`, `ErrorDisplay`, `Link`, `Modal`, `Accordion`, `Dropdown`,
//! `Progress`). How they look is the `nj-*` classes in `assets/app.css`; what they do is here,
//! as component state and the native `<dialog>` element.

use std::cell::{Cell, RefCell};

use leptos::ev;
use leptos::html;
use leptos::prelude::*;
use wasm_bindgen::JsCast;

use crate::state::Async;
use crate::store::store;

thread_local! {
    static NEXT_ID: Cell<u32> = const { Cell::new(0) };
    static MODAL: RefCell<Option<web_sys::HtmlDialogElement>> = const { RefCell::new(None) };
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
        <button class="nj-btn nj-btn-danger" on:click=move |_| on_ok.run(())>"OK"</button>
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
            class="nj-progress"
            role="progressbar"
            aria-label=title
            aria-valuenow=progress.to_string()
            aria-valuemin="0"
            aria-valuemax="100"
        >
            <div class="nj-progress-bar" style=format!("width: {progress}%;")></div>
        </div>
    }
}

// --- Modal: one <dialog> on screen at a time, shown and hidden from code ---

pub fn modal_show() {
    MODAL.with(|m| {
        if let Some(dialog) = m.borrow().as_ref()
            && !dialog.open()
        {
            let _ = dialog.show_modal();
        }
    });
}

pub fn modal_hide() {
    MODAL.with(|m| {
        if let Some(dialog) = m.borrow().as_ref()
            && dialog.open()
        {
            dialog.close();
        }
    });
}

/// A modal dialog. `on_close` runs however it is dismissed: the close button, Esc or a click on the
/// backdrop. Closing it from code ([`modal_hide`]) runs it too.
#[component]
pub fn Modal(
    #[prop(into)] title: String,
    #[prop(optional)] on_close: Option<Callback<()>>,
    children: Children,
) -> impl IntoView {
    let id = next_id("modal");
    let title_id = format!("{id}-title");
    let labelled_by = title_id.clone();
    let node = NodeRef::<html::Dialog>::new();
    Effect::new(move || {
        if let Some(el) = node.get() {
            let dialog: web_sys::HtmlDialogElement = el.unchecked_into();
            MODAL.with(|m| *m.borrow_mut() = Some(dialog));
        }
    });
    on_cleanup(|| MODAL.with(|m| *m.borrow_mut() = None));
    view! {
        <dialog
            node_ref=node
            id=id
            class="nj-modal"
            aria-labelledby=labelled_by
            on:close=move |_| {
                if let Some(f) = on_close {
                    f.run(())
                }
            }
            // a click on the dialog itself is a click on the backdrop, outside the panel
            on:click=move |ev| {
                let on_backdrop = ev
                    .target()
                    .and_then(|t| t.dyn_into::<web_sys::Node>().ok())
                    .zip(node.get_untracked())
                    .is_some_and(|(target, dialog)| {
                        target.is_same_node(Some(dialog.unchecked_ref::<web_sys::Node>()))
                    });
                if on_backdrop {
                    modal_hide();
                }
            }
        >
            <div class="nj-modal-panel">
                <div class="nj-modal-header">
                    <h1 class="nj-modal-title" id=title_id>{title}</h1>
                    <button
                        type="button"
                        class="nj-modal-close"
                        aria-label="Close"
                        on:click=|_| modal_hide()
                    >
                        "×"
                    </button>
                </div>
                <div class="nj-modal-body">{children()}</div>
            </div>
        </dialog>
    }
}

// --- Accordion ---

pub struct AccordionItem {
    pub title: String,
    pub body: AnyView,
}

/// A list of collapsible sections, at most one open at a time.
#[component]
pub fn Accordion(items: Vec<AccordionItem>) -> impl IntoView {
    let id = next_id("accordion");
    let open = RwSignal::new(None::<usize>);
    let items = items
        .into_iter()
        .enumerate()
        .map(|(i, item)| {
            let item_id = format!("item{i}{id}");
            let controls = item_id.clone();
            let expanded = move || open.get() == Some(i);
            view! {
                <div class="nj-accordion-item">
                    <h2 class="nj-accordion-header">
                        <button
                            class="nj-accordion-button"
                            aria-expanded=move || expanded().to_string()
                            aria-controls=controls
                            on:click=move |_| open.update(|o| *o = if *o == Some(i) { None } else { Some(i) })
                        >
                            {item.title}
                        </button>
                    </h2>
                    <div id=item_id hidden=move || !expanded()>
                        <div class="nj-accordion-body">{item.body}</div>
                    </div>
                </div>
            }
        })
        .collect_view();
    view! { <div id=id class="nj-accordion">{items}</div> }
}

// --- Dropdowns ---

/// Closes a dropdown (`expanded`) on a press outside `root` or on Escape, for as long as the
/// calling component lives.
fn close_dropdown_outside<E>(root: NodeRef<E>, expanded: RwSignal<bool>)
where
    E: html::ElementType + 'static,
    E::Output: JsCast + Clone + 'static,
{
    let press = window_event_listener(ev::pointerdown, move |ev| {
        if !expanded.get_untracked() {
            return;
        }
        let inside = ev
            .target()
            .and_then(|t| t.dyn_into::<web_sys::Node>().ok())
            .zip(root.get_untracked())
            .is_some_and(|(target, root)| {
                root.unchecked_ref::<web_sys::Node>().contains(Some(&target))
            });
        if !inside {
            expanded.set(false);
        }
    });
    let key = window_event_listener(ev::keydown, move |ev| {
        if expanded.get_untracked() && ev.key() == "Escape" {
            // only the menu closes, not a modal it may be in
            ev.prevent_default();
            ev.stop_propagation();
            expanded.set(false);
        }
    });
    on_cleanup(move || {
        press.remove();
        key.remove();
    });
}

/// A button dropdown with a filter box (the symbols page's object picker).
#[component]
pub fn DropdownButton(
    #[prop(into)] selected: String,
    options: Vec<String>,
    on_select: Callback<String>,
) -> impl IntoView {
    let root = NodeRef::<html::Div>::new();
    let expanded = RwSignal::new(false);
    let filter = RwSignal::new(String::new());
    close_dropdown_outside(root, expanded);
    view! {
        <div class="nj-dropdown inline-block" node_ref=root>
            <button
                class="nj-btn nj-btn-success nj-dropdown-toggle"
                aria-expanded=move || expanded.get().to_string()
                on:click=move |_| expanded.update(|e| *e = !*e)
            >
                {selected}
            </button>
            <div class="nj-dropdown-menu" hidden=move || !expanded.get()>
                <div class="nj-dropdown-filter">
                    <input
                        type="text"
                        class="nj-input"
                        placeholder="Type to filter..."
                        prop:value=move || filter.get()
                        on:input=move |ev| filter.set(event_target_value(&ev).trim().to_owned())
                    />
                </div>
                <div class="nj-dropdown-items">
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
                                        class="nj-dropdown-item"
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
    let root = NodeRef::<html::Li>::new();
    let expanded = RwSignal::new(false);
    close_dropdown_outside(root, expanded);
    let menu_id = next_id("nav-menu");
    let controls = menu_id.clone();
    let items = options
        .into_iter()
        .map(|(label, value)| {
            view! {
                <a
                    href="#"
                    class="nj-dropdown-item"
                    on:click=move |ev| {
                        ev.prevent_default();
                        on_select.run(value.clone());
                        expanded.set(false);
                    }
                >
                    {label}
                </a>
            }
        })
        .collect_view();
    view! {
        <li class="nj-dropdown" node_ref=root>
            <button
                class="nj-nav-link nj-dropdown-toggle"
                aria-expanded=move || expanded.get().to_string()
                aria-controls=controls
                on:click=move |_| expanded.update(|e| *e = !*e)
            >
                {title}
            </button>
            <div id=menu_id class="nj-dropdown-menu nj-nav-menu" hidden=move || !expanded.get()>
                <div class="nj-dropdown-items">{items}</div>
            </div>
        </li>
    }
}

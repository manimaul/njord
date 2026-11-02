//! The pages, the shared widgets they are built from, and the [`App`] router.

mod chart_info;
mod chart_view;
mod components;
mod control_panel;
mod home;
mod layer;
mod nav;

use leptos::prelude::*;

use crate::routing::NjordRoute;
use crate::store::store;

/// The nav bar over the current route's page.
///
/// Pages are rebuilt when the route or
/// its path arguments change, not when only the query string does (the map rewrites its own
/// `?lat=…&lng=…&z=…` as it moves).
#[component]
pub fn App() -> impl IntoView {
    let s = store();
    let page = Memo::new(move |_| {
        s.route.with(|r| {
            (
                r.current.route,
                r.current.args.clone(),
                r.current.path.clone(),
            )
        })
    });
    view! {
        <div class="flex min-h-screen flex-col">
            <nav::NavBar/>
            {move || {
                let (route, args, path) = page.get();
                let arg = |k: &str| {
                    args.as_ref()
                        .and_then(|a| a.iter().find(|(key, _)| key == k))
                        .map(|(_, v)| v.clone())
                };
                match route {
                    NjordRoute::About => view! { <RouteContent><home::Home/></RouteContent> }.into_any(),
                    NjordRoute::NotFound => {
                        view! { <RouteContent><NotFound path=path/></RouteContent> }.into_any()
                    }
                    NjordRoute::Enc => view! { <chart_view::ChartView/> }.into_any(),
                    NjordRoute::ControlPanel => match arg("tab") {
                        Some(tab) => {
                            let path = arg("path").unwrap_or_default();
                            view! {
                                <RouteContent><control_panel::ControlPanel tab=tab path=path/></RouteContent>
                            }
                                .into_any()
                        }
                        None => view! { <RouteContent><NotFound path=path/></RouteContent> }.into_any(),
                    },
                    NjordRoute::Chart => match arg("name") {
                        Some(name) => {
                            view! { <RouteContent><chart_info::ChartInfoPage name=name/></RouteContent> }
                                .into_any()
                        }
                        None => view! { <RouteContent><NotFound path=path/></RouteContent> }.into_any(),
                    },
                    NjordRoute::Layer => match arg("name") {
                        Some(name) => {
                            view! { <RouteContent><layer::LayerPage name=name/></RouteContent> }.into_any()
                        }
                        None => view! { <RouteContent><NotFound path=path/></RouteContent> }.into_any(),
                    },
                }
            }}
        </div>
    }
}

#[component]
fn RouteContent(children: Children) -> impl IntoView {
    view! { <div class="m-2 min-w-0">{children()}</div> }
}

#[component]
fn NotFound(path: String) -> impl IntoView {
    view! { <div>"Whoops " <b>{path}</b> " is not a known location!"</div> }
}

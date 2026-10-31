//! `NavBar.js.kt`: pages, depth / theme / custom colour / region pickers, login.

use leptos::prelude::*;
use njord_model::{Depth, ThemeMode};

use crate::bootstrap;
use crate::kotlin::{depth_name, mode_name};
use crate::routing::nav_bar_routes;
use crate::state::Async;
use crate::store::{REGISTRY, store};
use crate::ui::components::NavDropdown;

#[component]
pub fn NavBar() -> impl IntoView {
    let s = store();
    s.load_nav();
    let current_route = Memo::new(move |_| s.route.with(|r| r.current.route));
    let routes = nav_bar_routes(&REGISTRY)
        .into_iter()
        .map(|routing| {
            let route = routing.route;
            let path = routing.path_and_params();
            view! {
                <li class="nav-item">
                    <button
                        class=move || {
                            if current_route.get() == route { "nav-link active" } else { "nav-link" }
                        }
                        on:click=move |_| s.push_route(&path)
                    >
                        {route.title()}
                    </button>
                </li>
            }
        })
        .collect_view();

    let depth_title =
        Signal::derive(move || format!("Depths: {}", depth_name(s.chart.with(|c| c.depth))));
    let depths = Depth::ALL
        .iter()
        .map(|&d| (depth_name(d).to_owned(), d))
        .collect();
    let theme_title =
        Signal::derive(move || format!("Theme: {}", mode_name(s.chart.with(|c| c.theme.mode()))));
    let themes = ThemeMode::ALL
        .iter()
        .map(|&m| (mode_name(m).to_owned(), m))
        .collect();

    view! {
        <nav
            class="navbar navbar-expand-lg bg-body-tertiary sticky-top border-body border-bottom"
            data-bs-theme="dark"
        >
            <div class="container-fluid">
                <a class="navbar-brand" href="#">"Njord"</a>
                <button
                    class="navbar-toggler"
                    on:click=|_| bootstrap::toggle_collapse("#navbarNavDropdown")
                >
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div id="navbarNavDropdown" class="collapse navbar-collapse">
                    <ul class="navbar-nav">
                        {routes}
                        <NavDropdown
                            title=depth_title
                            options=depths
                            on_select=Callback::new(move |d| s.set_depth(d))
                        />
                        <NavDropdown
                            title=theme_title
                            options=themes
                            on_select=Callback::new(move |m: ThemeMode| s.set_theme(m.into()))
                        />
                        {move || {
                            s.nav
                                .with(|n| n.custom_colors.value().cloned())
                                .map(|colors| {
                                    let title = Signal::derive(move || {
                                        format!("Colors: {}", s.chart.with(|c| c.theme.color_name().to_owned()))
                                    });
                                    let options = colors.into_iter().map(|c| (c.clone(), c)).collect();
                                    view! {
                                        <NavDropdown
                                            title=title
                                            options=options
                                            on_select=Callback::new(move |c: String| s.set_custom_color(&c))
                                        />
                                    }
                                })
                        }}
                        {move || {
                            s.nav
                                .with(|n| n.regions.value().cloned())
                                .map(|names| {
                                    let title = Signal::derive(move || {
                                        format!(
                                            "Region: {}",
                                            s.chart.with(|c| c.region.clone()).unwrap_or_else(|| "All".into())
                                        )
                                    });
                                    let options = std::iter::once(("All".to_owned(), None))
                                        .chain(names.into_iter().map(|n| (n.clone(), Some(n))))
                                        .collect();
                                    view! {
                                        <NavDropdown
                                            title=title
                                            options=options
                                            on_select=Callback::new(move |r: Option<String>| s.set_region(r))
                                        />
                                    }
                                })
                        }}
                    </ul>
                </div>
            </div>
            {move || match s.admin.with(|a| a.signature.clone()) {
                Async::Complete(_) => {
                    view! {
                        <button class="btn btn-danger btn-sm mx-2" on:click=move |_| s.logout()>
                            "Logout"
                        </button>
                    }
                        .into_any()
                }
                Async::Loading(_) => ().into_any(),
                Async::Fail { .. } | Async::Uninitialized => {
                    view! {
                        <button class="btn btn-info btn-sm mx-2" on:click=move |_| s.login()>
                            "Login"
                        </button>
                    }
                        .into_any()
                }
            }}
        </nav>
    }
}

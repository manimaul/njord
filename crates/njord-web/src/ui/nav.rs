//! The nav bar: page links, the depth / theme / custom colour / region pickers, and login.
//!
//! Links sit in a row from `lg` up; below that a hamburger button opens them as a vertical menu
//! (`.nj-navbar-collapse` in `assets/app.css`). Choosing a page closes the menu.

use leptos::prelude::*;
use njord_model::{Depth, ThemeMode};

use crate::display::{depth_name, mode_name};
use crate::routing::nav_bar_routes;
use crate::state::Async;
use crate::store::{REGISTRY, store};
use crate::ui::components::NavDropdown;

#[component]
pub fn NavBar() -> impl IntoView {
    let s = store();
    s.load_nav();
    let menu_open = RwSignal::new(false);
    let current_route = Memo::new(move |_| s.route.with(|r| r.current.route));
    let routes = nav_bar_routes(&REGISTRY)
        .into_iter()
        .map(|routing| {
            let route = routing.route;
            let path = routing.path_and_params();
            view! {
                <li>
                    <button
                        class="nj-nav-link"
                        aria-current=move || (current_route.get() == route).then_some("page")
                        on:click=move |_| {
                            s.push_route(&path);
                            menu_open.set(false);
                        }
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
        <nav class="nj-navbar">
            <div class="nj-navbar-inner">
                <a class="nj-navbar-brand" href="#">"Njord"</a>
                <div class="nj-navbar-end">
                    {move || match s.admin.with(|a| a.signature.clone()) {
                        Async::Complete(_) => {
                            view! {
                                <button class="nj-btn nj-btn-sm nj-btn-danger" on:click=move |_| s.logout()>
                                    "Logout"
                                </button>
                            }
                                .into_any()
                        }
                        Async::Loading(_) => ().into_any(),
                        Async::Fail { .. } | Async::Uninitialized => {
                            view! {
                                <button class="nj-btn nj-btn-sm nj-btn-info" on:click=move |_| s.login()>
                                    "Login"
                                </button>
                            }
                                .into_any()
                        }
                    }}
                    <button
                        class="nj-navbar-toggler lg:hidden"
                        aria-controls="navbarNavDropdown"
                        aria-expanded=move || menu_open.get().to_string()
                        aria-label="Toggle navigation"
                        on:click=move |_| menu_open.update(|o| *o = !*o)
                    >
                        <HamburgerIcon/>
                    </button>
                </div>
                <div class="nj-navbar-collapse" data-open=move || menu_open.get().to_string()>
                    <ul id="navbarNavDropdown" class="m-0 flex list-none flex-col p-0 lg:flex-row">
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
        </nav>
    }
}

#[component]
fn HamburgerIcon() -> impl IntoView {
    view! {
        <svg viewBox="0 0 30 30" width="30" height="30" aria-hidden="true">
            <path d="M4 7h22M4 15h22M4 23h22" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
        </svg>
    }
}

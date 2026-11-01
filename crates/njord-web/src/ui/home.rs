//! The about page (`/`).

use leptos::prelude::*;
use leptos::task::spawn_local;

use crate::api;
use crate::display::duration_to_string;
use crate::state::Async;
use crate::store::store;
use crate::ui::components::render_async;

#[component]
pub fn Home() -> impl IntoView {
    let s = store();
    let about = RwSignal::new(Async::Uninitialized);
    let reload = move || {
        about.set(Async::Loading(None));
        spawn_local(async move {
            let _ = about.try_set(Async::from_result(api::about().await, None));
        });
    };
    reload();
    let year = js_sys::Date::new_0().get_full_year();
    view! {
        <div class="Column Fill">
            <div class="Container Fill">
                <header class="Header">
                    <img src="/njord.jpg" class="img-fluid w-25"/>
                </header>
                <div class="Center">
                    {move || {
                        render_async(
                            about.get(),
                            reload,
                            |info| {
                                view! {
                                    <table class="w-50 table table-striped table-bordered table-hover">
                                        <thead>
                                            <tr>
                                                <th colspan="2">
                                                    {format!("Njord Electronic Navigation Chart Server ©{year}")}
                                                </th>
                                            </tr>
                                            <tr>
                                                <td>"Njord Version"</td>
                                                <td>{info.version}</td>
                                            </tr>
                                            <tr>
                                                <td>"Git Commit"</td>
                                                <td>
                                                    <a
                                                        class="link-secondary link-underline-opacity-75"
                                                        href=format!("https://github.com/manimaul/njord/commit/{}", info.git_hash)
                                                    >
                                                        {info.git_hash.clone()}
                                                    </a>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>"Gdal Version"</td>
                                                <td>{info.gdal_version}</td>
                                            </tr>
                                            <tr>
                                                <td>"Build Date"</td>
                                                <td>{info.build_date}</td>
                                            </tr>
                                            {move || {
                                                s.admin
                                                    .with(|a| a.remaining)
                                                    .map(|r| {
                                                        view! {
                                                            <tr>
                                                                <td>"Admin"</td>
                                                                <td>{duration_to_string(r)}</td>
                                                            </tr>
                                                        }
                                                    })
                                            }}
                                            <tr>
                                                <td>"License"</td>
                                                <td>
                                                    <a href="https://github.com/manimaul/njord/blob/master/LICENSE">
                                                        <img src="/apache_2_badge.svg"/>
                                                    </a>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>"Source"</td>
                                                <td>
                                                    <a href="https://github.com/manimaul/njord">
                                                        <img src="/github.svg" style="width: 25px; height: 25px;"/>
                                                    </a>
                                                    " "
                                                    <a
                                                        class="link-secondary link-underline-opacity-75"
                                                        href="https://github.com/manimaul/njord"
                                                    >
                                                        " https://github.com/manimaul/njord"
                                                    </a>
                                                </td>
                                            </tr>
                                        </thead>
                                    </table>
                                }
                                    .into_any()
                            },
                        )
                    }}
                </div>
            </div>
        </div>
    }
}

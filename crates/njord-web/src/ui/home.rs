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
        <div class="flex flex-1 flex-col">
            <div class="flex-1">
                <header class="flex flex-col items-center justify-center p-7 text-xl">
                    <img src="/njord.jpg" class="h-auto w-1/2 max-w-full md:w-1/4"/>
                </header>
                <div class="flex justify-center">
                    {move || {
                        render_async(
                            about.get(),
                            reload,
                            |info| {
                                view! {
                                    <table class="nj-table nj-table-striped w-full max-w-2xl">
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
                                                        class="text-muted"
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
                                                        <img src="/github.svg" class="h-[25px] w-[25px]"/>
                                                    </a>
                                                    " "
                                                    <a
                                                        class="text-muted"
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

//! `ChartInfoPage.js.kt`: `/chart/<name>`.

use leptos::prelude::*;
use leptos::task::spawn_local;

use crate::api;
use crate::routing::NjordRoute;
use crate::state::Async;
use crate::store::store;
use crate::ui::components::{Link, render_async};

#[component]
pub fn ChartInfoPage(name: String) -> impl IntoView {
    let s = store();
    let info = RwSignal::new(Async::Uninitialized);
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
            let (bounds, covr) = (chart.bounds.clone(), chart.covr.clone());
            let layers = chart
                .layers
                .iter()
                .map(|layer| {
                    view! { <Link label=layer.clone() path=format!("/control/symbols/{layer}")/>" " }
                })
                .collect_view();
            let txt = chart
                .chart_txt
                .iter()
                .map(|(k, v)| view! { <tr><td>{k.clone()}</td><td>{v.clone()}</td></tr> })
                .collect_view();
            let dsid = chart
                .dsid_props
                .iter()
                .map(|(k, v)| view! { <tr><td>{k.clone()}</td><td>{v.to_string()}</td></tr> })
                .collect_view();
            view! {
                <div class="container Content">
                    <table class="table table-striped table-bordered table-hover">
                        <thead><tr><th>"Name"</th><th>"Value"</th></tr></thead>
                        <tbody>
                            <tr>
                                <td>"Name"</td>
                                <td>
                                    {format!("{} ", chart.name)}
                                    <button
                                        class="btn btn-outline-secondary btn-sm"
                                        on:click=move |_| {
                                            s.set_bounds(bounds.clone(), Some(covr.clone()));
                                            s.push_page(NjordRoute::Enc);
                                        }
                                    >
                                        "ENC Zoom"
                                    </button>
                                </td>
                            </tr>
                            <tr><td>"Feature Count"</td><td>{chart.feature_count.to_string()}</td></tr>
                            <tr><td>"Updated"</td><td>{chart.updated.clone()}</td></tr>
                            <tr><td>"Issued"</td><td>{chart.issued.clone()}</td></tr>
                            <tr><td>"Zoom"</td><td>{chart.zoom.to_string()}</td></tr>
                            <tr><td>"Scale"</td><td>{chart.scale.to_string()}</td></tr>
                            <tr><td>"Layers"</td><td><p>{layers}</p></td></tr>
                            {txt}
                            {dsid}
                        </tbody>
                    </table>
                </div>
            }
            .into_any()
        })
    }
}

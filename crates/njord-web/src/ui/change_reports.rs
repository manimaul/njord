//! The Change Report tab (`/control/change_reports`): the days with chart or base map changes,
//! 25 per page, and one day's report (`/control/change_reports/<date>`) listing every change
//! (`docs/change_reports.md`).
//!
//! Both routes are signed, so the tab asks for the admin login first. The day list's page is
//! the `?page=` query parameter, so the back button and bookmarks keep it. Dates are UTC and
//! labelled so.

use leptos::prelude::*;
use njord_model::{ChangeCause, ChangeItem, ChangeKind, ChangeOutcome, ChangeReport};

use crate::store::store;
use crate::ui::components::{Link, render_async};

const TAB_PATH: &str = "/control/change_reports";

/// The report date in the tab's sub-path: `2026-10-31` from `2026-10-31` or `2026-10-31/`,
/// `None` for anything that is not a `YYYY-MM-DD` date.
pub fn report_date(path: &str) -> Option<&str> {
    let date = path.trim_matches('/');
    let digits = |s: &str| !s.is_empty() && s.bytes().all(|b| b.is_ascii_digit());
    let parts: Vec<&str> = date.split('-').collect();
    let shaped = parts.len() == 3
        && parts[0].len() == 4
        && parts[1].len() == 2
        && parts[2].len() == 2
        && parts.iter().all(|p| digits(p));
    if !shaped {
        return None;
    }
    let month: u32 = parts[1].parse().ok()?;
    let day: u32 = parts[2].parse().ok()?;
    ((1..=12).contains(&month) && (1..=31).contains(&day)).then_some(date)
}

/// The display text of an outcome.
pub const fn outcome_name(outcome: ChangeOutcome) -> &'static str {
    match outcome {
        ChangeOutcome::New => "New",
        ChangeOutcome::Update => "Update",
        ChangeOutcome::Delete => "Delete",
        ChangeOutcome::Nuke => "Nuke",
        ChangeOutcome::Error => "Error",
    }
}

/// The display text of a cause.
pub const fn cause_name(cause: ChangeCause) -> &'static str {
    match cause {
        ChangeCause::Admin => "Admin",
        ChangeCause::NoaaEncCron => "NOAA ENC Cron",
    }
}

/// A feature count change with its sign: `+312`, `−40`, `0`.
pub fn signed(change: i64) -> String {
    if change > 0 {
        format!("+{change}")
    } else if change < 0 {
        format!("\u{2212}{}", change.unsigned_abs())
    } else {
        "0".to_owned()
    }
}

#[component]
pub fn ChangeReportsTab(path: String) -> impl IntoView {
    let s = store();
    let logged_in = Memo::new(move |_| s.admin.with(|a| a.is_logged_in()));
    move || {
        if !logged_in.get() {
            return "Admin access required".into_any();
        }
        match report_date(&path) {
            Some(date) => view! { <DayReport date=date.to_owned()/> }.into_any(),
            None if path.trim_matches('/').is_empty() => view! { <DayList/> }.into_any(),
            None => view! {
                <p>{format!("{path} is not a report date (YYYY-MM-DD).")}</p>
                <Link label="Back to Change Report" path=TAB_PATH/>
            }
            .into_any(),
        }
    }
}

/// `?page=` of the current URL, 0 when absent or not a non-negative integer.
fn page_param() -> i32 {
    store().route.with(|r| {
        r.current
            .params
            .as_ref()
            .and_then(|p| p.get("page"))
            .and_then(|v| v.parse::<i32>().ok())
            .filter(|&p| p >= 0)
            .unwrap_or(0)
    })
}

#[component]
fn DayList() -> impl IntoView {
    let s = store();
    let page_index = Memo::new(move |_| page_param());
    Effect::new(move || s.load_change_report_page(page_index.get()));
    let go = move |page: i32| s.push_query(TAB_PATH, &format!("page={page}"));
    view! {
        <h2>"Change Report"</h2>
        <p>"One row per UTC day with a chart or base map change. Actions are the successful changes, Errors the attempts that failed."</p>
        {move || {
            let state = s.change_reports.get();
            let index = page_index.get();
            render_async(state.page, move || s.load_change_report_page(index), move |page| {
                if page.total_days == 0 {
                    return view! { <p>"No changes recorded yet."</p> }.into_any();
                }
                let rows = page
                    .page
                    .iter()
                    .map(|day| {
                        view! {
                            <tr>
                                <td><Link label=day.date.clone() path=format!("{TAB_PATH}/{}", day.date)/></td>
                                <td>{day.actions}</td>
                                <td>{day.errors}</td>
                            </tr>
                        }
                    })
                    .collect_view();
                let last_page = (page.total_days - 1) / i64::from(page.page_size.max(1));
                let newer = page.page_index > 0;
                let older = i64::from(page.page_index) < last_page;
                let shown = page.page_index;
                view! {
                    <table class="nj-table nj-table-striped">
                        <thead><tr><th>"Date (UTC)"</th><th>"Actions"</th><th>"Errors"</th></tr></thead>
                        <tbody>{rows}</tbody>
                    </table>
                    <div class="flex items-center gap-4">
                        <button
                            class="nj-btn nj-btn-sm nj-btn-outline-secondary"
                            disabled=!newer
                            on:click=move |_| go(shown - 1)
                        >
                            "Newer"
                        </button>
                        <span>{format!("Page {} of {}", shown + 1, last_page + 1)}</span>
                        <button
                            class="nj-btn nj-btn-sm nj-btn-outline-secondary"
                            disabled=!older
                            on:click=move |_| go(shown + 1)
                        >
                            "Older"
                        </button>
                    </div>
                }
                .into_any()
            })
        }}
    }
}

#[component]
fn DayReport(date: String) -> impl IntoView {
    let s = store();
    let load = {
        let date = date.clone();
        move || s.load_change_report(&date)
    };
    load();
    view! {
        <Link label="Back to Change Report" path=TAB_PATH/>
        <h2>{format!("{date} (UTC)")}</h2>
        {move || {
            let state = s.change_reports.get();
            render_async(state.report, load.clone(), |report| report_view(&report))
        }}
    }
}

fn report_view(report: &ChangeReport) -> AnyView {
    let counts = [
        ("New", report.counts.new),
        ("Update", report.counts.update),
        ("Delete", report.counts.delete),
        ("Nuke", report.counts.nuke),
        ("Error", report.counts.error),
    ]
    .into_iter()
    .map(|(label, count)| {
        view! {
            <div class="nj-btn nj-btn-sm nj-btn-outline-secondary" style="pointer-events: none;">
                {format!("{label}: {count}")}
            </div>
        }
    })
    .collect_view();
    let rows = report.items.iter().map(item_row).collect_view();
    view! {
        <div class="mb-4 flex flex-wrap gap-2">{counts}</div>
        <table class="nj-table nj-table-striped">
            <thead>
                <tr><th>"Outcome"</th><th>"Chart"</th><th>"Feature Count Change"</th><th>"Cause"</th></tr>
            </thead>
            <tbody>{rows}</tbody>
        </table>
    }
    .into_any()
}

fn item_row(item: &ChangeItem) -> AnyView {
    let outcome = match (item.outcome, item.attempted) {
        (ChangeOutcome::Error, Some(attempted)) => {
            format!("Error ({})", outcome_name(attempted).to_ascii_lowercase())
        }
        (outcome, _) => outcome_name(outcome).to_owned(),
    };
    let name = match item.kind {
        ChangeKind::Chart => {
            view! { <Link label=item.name.clone() path=format!("/chart/{}", item.name)/> }
                .into_any()
        }
        ChangeKind::BaseMap => format!("{} (base map)", item.name).into_any(),
        ChangeKind::All => "all charts and base maps".into_any(),
    };
    let message = item.message.clone();
    let title = message.clone().unwrap_or_default();
    let is_error = item.outcome == ChangeOutcome::Error;
    let detail = message.filter(|_| is_error).map(|m| {
        view! {
            <details>
                <summary>"message"</summary>
                <pre class="whitespace-pre-wrap">{m}</pre>
            </details>
        }
    });
    // Only the outcome cell is tinted, so the chart link keeps its contrast.
    view! {
        <tr title=title>
            <td class=is_error.then_some("nj-warning")>{outcome}{detail}</td>
            <td>{name}</td>
            <td>{signed(item.feature_count_change)}</td>
            <td>{cause_name(item.cause)}</td>
        </tr>
    }
    .into_any()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sub_path_to_date() {
        assert_eq!(report_date("2026-10-31"), Some("2026-10-31"));
        assert_eq!(report_date("2026-10-31/"), Some("2026-10-31"));
        assert_eq!(report_date("/2026-10-31"), Some("2026-10-31"));
        assert_eq!(report_date(""), None);
        assert_eq!(report_date("2026-13-01"), None);
        assert_eq!(report_date("2026-1-5"), None);
        assert_eq!(report_date("20261031"), None);
        assert_eq!(report_date("today"), None);
    }

    #[test]
    fn display_text() {
        assert_eq!(signed(312), "+312");
        assert_eq!(signed(-40), "\u{2212}40");
        assert_eq!(signed(0), "0");
        assert_eq!(cause_name(ChangeCause::NoaaEncCron), "NOAA ENC Cron");
        assert_eq!(outcome_name(ChangeOutcome::Nuke), "Nuke");
    }
}

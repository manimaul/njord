//! The Change Report tab (`/control/change_reports`): the days with chart or base map changes,
//! 25 per page, and one day's report (`/control/change_reports/<date>`) listing every change
//! (`docs/change_reports.md`).
//!
//! Both routes are signed, so the tab asks for the admin login first. The day list's page is
//! the `?page=` query parameter, so the back button and bookmarks keep it. Days are UTC (that is
//! how the server groups them and how the URL names them) and labelled so; beside each the
//! browser shows the span that day covers in its own time zone, and each change shows the local
//! date and time it occurred at.

use jiff::Timestamp;
use jiff::civil::Date;
use jiff::tz::TimeZone;
use leptos::prelude::*;
use njord_model::{ChangeCause, ChangeItem, ChangeKind, ChangeOutcome, ChangeReport};
use wasm_bindgen::JsValue;

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

/// The instants a UTC day starts and ends at: `2026-10-31` is `2026-10-31T00:00Z` to
/// `2026-11-01T00:00Z`. `None` for anything that is not a `YYYY-MM-DD` date.
pub fn utc_day_bounds(date: &str) -> Option<(Timestamp, Timestamp)> {
    let day: Date = date.parse().ok()?;
    let start = day.to_zoned(TimeZone::UTC).ok()?.timestamp();
    let end = day
        .tomorrow()
        .ok()?
        .to_zoned(TimeZone::UTC)
        .ok()?
        .timestamp();
    Some((start, end))
}

/// `ts` as the browser's local date and time with its zone, as `Oct 30, 2026, 5:00 PM PDT`.
/// Only the browser can know the viewer's zone and locale, so this goes through
/// `Date.toLocaleString` rather than `jiff`.
fn local_date_time(ts: Timestamp) -> String {
    let date = js_sys::Date::new(&JsValue::from_f64(ts.as_millisecond() as f64));
    let options = js_sys::Object::new();
    for (key, value) in [
        ("year", "numeric"),
        ("month", "short"),
        ("day", "numeric"),
        ("hour", "numeric"),
        ("minute", "2-digit"),
        ("timeZoneName", "short"),
    ] {
        // Setting a property on a fresh object cannot fail.
        let _ = js_sys::Reflect::set(&options, &JsValue::from_str(key), &JsValue::from_str(value));
    }
    date.to_locale_string("default", &options).into()
}

/// The browser-local span a UTC day covers, as `Oct 30, 2026, 5:00 PM PDT – Oct 31, 2026,
/// 5:00 PM PDT`; empty for a malformed date.
fn local_span(date: &str) -> String {
    utc_day_bounds(date)
        .map(|(start, end)| {
            format!(
                "{} \u{2013} {}",
                local_date_time(start),
                local_date_time(end)
            )
        })
        .unwrap_or_default()
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
        <p>"One row per UTC day with a chart or base map change. Local time is the span that day covers in your time zone. Actions are the successful changes, Errors the attempts that failed."</p>
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
                                <td>{local_span(&day.date)}</td>
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
                        <thead><tr><th>"Date (UTC)"</th><th>"Local time"</th><th>"Actions"</th><th>"Errors"</th></tr></thead>
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
    let span = local_span(&date);
    view! {
        <Link label="Back to Change Report" path=TAB_PATH/>
        <h2>{format!("{date} (UTC)")}</h2>
        <p>{format!("Local time: {span}")}</p>
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
                <tr><th>"Outcome"</th><th>"Chart"</th><th>"Feature Count Change"</th><th>"Cause"</th><th>"Local time"</th></tr>
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
            <td>{local_date_time(item.occurred_at)}</td>
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
    fn utc_day_bounds_span_the_day() {
        let (start, end) = utc_day_bounds("2026-10-31").unwrap();
        assert_eq!(start, "2026-10-31T00:00:00Z".parse().unwrap());
        assert_eq!(end, "2026-11-01T00:00:00Z".parse().unwrap());
        let (start, end) = utc_day_bounds("2026-12-31").unwrap();
        assert_eq!(start, "2026-12-31T00:00:00Z".parse().unwrap());
        assert_eq!(end, "2027-01-01T00:00:00Z".parse().unwrap());
        assert_eq!(utc_day_bounds("2026-13-01"), None);
        assert_eq!(utc_day_bounds("today"), None);
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

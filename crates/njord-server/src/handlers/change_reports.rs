//! `GET /v1/change_reports?page=` and `GET /v1/change_reports/{date}?page=`, signed: the
//! change history by day (`docs/change_reports.md`).
//!
//! Both are behind the admin signature, like the mutating routes: the report is operator data,
//! and its error messages come from GDAL and sqlx.

use std::sync::Arc;

use axum::Json;
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use njord_db::{REPORT_ITEM_PAGE_SIZE, REPORT_PAGE_SIZE};

use super::{Signed, query_param};
use crate::state::AppState;

/// The zero-based `?page=` of `query`: 0 when absent, `None` (a 400) when it is not a
/// non-negative integer.
fn page_param(query: &[(String, String)]) -> Option<i32> {
    match query_param(query, "page") {
        None => Some(0),
        Some(value) => value.parse::<i32>().ok().filter(|&page| page >= 0),
    }
}

/// `GET /v1/change_reports?page=<n>`: one page of days, newest first, [`REPORT_PAGE_SIZE`] per
/// page. `page` is zero-based and defaults to 0; a value that is not a non-negative integer is
/// 400. 500 when the database fails.
pub async fn days(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    let Some(page) = page_param(&query) else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    match state.db.change_report_days(page, REPORT_PAGE_SIZE).await {
        Ok(days) => Json(days).into_response(),
        Err(e) => {
            tracing::error!("change reports page {page}: {e}");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

/// Whether `date` is a calendar date written `YYYY-MM-DD`.
pub fn valid_date(date: &str) -> bool {
    date.len() == 10 && date.parse::<jiff::civil::Date>().is_ok()
}

/// `GET /v1/change_reports/{date}?page=<n>`: the report of one UTC day, with page `page` of its
/// events, [`REPORT_ITEM_PAGE_SIZE`] per page (zero-based, default 0; a page past the end has
/// the counts and no items). 400 for anything but a `YYYY-MM-DD` date or for a `page` that is
/// not a non-negative integer, 404 for a day with no events, 500 when the database fails.
pub async fn report(
    _: Signed,
    State(state): State<Arc<AppState>>,
    Path(date): Path<String>,
    Query(query): Query<Vec<(String, String)>>,
) -> Response {
    if !valid_date(&date) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let Some(page) = page_param(&query) else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    match state
        .db
        .change_report(&date, page, REPORT_ITEM_PAGE_SIZE)
        .await
    {
        Ok(Some(report)) => Json(report).into_response(),
        Ok(None) => StatusCode::NOT_FOUND.into_response(),
        Err(e) => {
            tracing::error!("change report {date}: {e}");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{page_param, valid_date};

    fn query(pairs: &[(&str, &str)]) -> Vec<(String, String)> {
        pairs
            .iter()
            .map(|(k, v)| ((*k).to_owned(), (*v).to_owned()))
            .collect()
    }

    #[test]
    fn pages() {
        assert_eq!(page_param(&query(&[])), Some(0));
        assert_eq!(page_param(&query(&[("signature", "x")])), Some(0));
        assert_eq!(page_param(&query(&[("page", "3")])), Some(3));
        assert_eq!(page_param(&query(&[("page", "-1")])), None);
        assert_eq!(page_param(&query(&[("page", "x")])), None);
        assert_eq!(page_param(&query(&[("page", "")])), None);
    }

    #[test]
    fn dates() {
        assert!(valid_date("2026-10-31"));
        assert!(valid_date("2024-02-29"));
        assert!(!valid_date("2026-02-30"));
        assert!(!valid_date("2026-1-5"));
        assert!(!valid_date("20261031"));
        assert!(!valid_date("2026-10-31T00:00:00Z"));
        assert!(!valid_date("yesterday"));
    }
}

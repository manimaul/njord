//! The change history (`change_events`): one row per chart or base map per action, read by the
//! change report routes (`docs/change_reports.md`).
//!
//! Rows are written by ingest, the chart handlers and the nuke, through
//! [`record_change_event`] on whatever connection or transaction made the change, so a change
//! and its record commit or roll back together ([`FeatureInstall::record`](crate::FeatureInstall::record)).
//! Nothing deletes rows: a chart's history outlives the chart, and a nuke records itself
//! ([`Db::truncate_all`] leaves the table alone).
//!
//! `occurred_at` defaults to `now()`, as `region_export_state.exported_at` is written, and `day`
//! is its UTC date (a stored generated column), so grouping by day never depends on the session
//! time zone.

use njord_model::{
    ChangeCause, ChangeCounts, ChangeItem, ChangeKind, ChangeOutcome, ChangeReport,
    ChangeReportDay, ChangeReportPage,
};
use sqlx::Row;
use sqlx::postgres::{PgConnection, PgRow};

use crate::{Db, Result};

/// Days per page of [`Db::change_report_days`].
pub const REPORT_PAGE_SIZE: i32 = 25;

/// A change event to record.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NewChangeEvent {
    pub kind: ChangeKind,
    /// The chart's `DSID_DSNM`, the shapefile name, or [`NewChangeEvent::NUKE_NAME`].
    pub name: String,
    pub outcome: ChangeOutcome,
    /// For [`ChangeOutcome::Error`]: what was attempted. Ignored otherwise.
    pub attempted: Option<ChangeOutcome>,
    /// `after − before` of the stored feature count.
    pub feature_count_change: i64,
    pub cause: ChangeCause,
    /// The failure message of an error.
    pub message: Option<String>,
    /// The claimed upload the change came from, when it came from one.
    pub zip_file: Option<String>,
}

impl NewChangeEvent {
    /// The `name` of a nuke event.
    pub const NUKE_NAME: &'static str = "*";

    /// A successful change of `kind` `name`: [`ChangeOutcome::New`] when `before` is `None`,
    /// else [`ChangeOutcome::Update`], with the measured change.
    pub fn installed(
        kind: ChangeKind,
        name: &str,
        before: Option<i64>,
        after: i64,
        cause: ChangeCause,
    ) -> NewChangeEvent {
        NewChangeEvent {
            kind,
            name: name.to_owned(),
            outcome: if before.is_some() {
                ChangeOutcome::Update
            } else {
                ChangeOutcome::New
            },
            attempted: None,
            feature_count_change: after - before.unwrap_or(0),
            cause,
            message: None,
            zip_file: None,
        }
    }

    /// A failed install of `kind` `name`: `attempted` from `before` as [`installed`](Self::installed)
    /// decides the outcome, and the change measured from what is left.
    pub fn install_failed(
        kind: ChangeKind,
        name: &str,
        before: Option<i64>,
        after: i64,
        cause: ChangeCause,
        message: &str,
    ) -> NewChangeEvent {
        NewChangeEvent {
            kind,
            name: name.to_owned(),
            outcome: ChangeOutcome::Error,
            attempted: Some(if before.is_some() {
                ChangeOutcome::Update
            } else {
                ChangeOutcome::New
            }),
            feature_count_change: after - before.unwrap_or(0),
            cause,
            message: Some(message.to_owned()),
            zip_file: None,
        }
    }

    /// A successful delete of a chart that had `before` features.
    pub fn deleted(name: &str, before: i64, cause: ChangeCause) -> NewChangeEvent {
        NewChangeEvent {
            kind: ChangeKind::Chart,
            name: name.to_owned(),
            outcome: ChangeOutcome::Delete,
            attempted: None,
            feature_count_change: -before,
            cause,
            message: None,
            zip_file: None,
        }
    }

    /// A failed attempt at `attempted` on `kind` `name` with nothing changed.
    pub fn failed(
        kind: ChangeKind,
        name: &str,
        attempted: ChangeOutcome,
        cause: ChangeCause,
        message: &str,
    ) -> NewChangeEvent {
        NewChangeEvent {
            kind,
            name: name.to_owned(),
            outcome: ChangeOutcome::Error,
            attempted: Some(attempted),
            feature_count_change: 0,
            cause,
            message: Some(message.to_owned()),
            zip_file: None,
        }
    }

    /// The nuke: every chart's and base map's features (`before` in all) are gone.
    pub fn nuked(before: i64) -> NewChangeEvent {
        NewChangeEvent {
            kind: ChangeKind::All,
            name: Self::NUKE_NAME.to_owned(),
            outcome: ChangeOutcome::Nuke,
            attempted: None,
            feature_count_change: -before,
            cause: ChangeCause::Admin,
            message: None,
            zip_file: None,
        }
    }

    /// The same event, noting the upload it came from.
    pub fn from_zip(mut self, zip_file: &str) -> NewChangeEvent {
        self.zip_file = Some(zip_file.to_owned());
        self
    }
}

/// Inserts `event` on `conn`, which may be a transaction, and returns the row id.
///
/// `attempted` is stored only for an error.
///
/// # Errors
///
/// [`Error::Sql`](crate::Error::Sql) when the insert fails.
pub async fn record_change_event(conn: &mut PgConnection, event: &NewChangeEvent) -> Result<i64> {
    let attempted = (event.outcome == ChangeOutcome::Error)
        .then_some(event.attempted)
        .flatten();
    let id: i64 = sqlx::query_scalar(
        "INSERT INTO change_events
             (kind, name, outcome, attempted, feature_count_change, cause, message, zip_file)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
         RETURNING id;",
    )
    .bind(event.kind.as_str())
    .bind(&event.name)
    .bind(event.outcome.as_str())
    .bind(attempted.map(ChangeOutcome::as_str))
    .bind(event.feature_count_change)
    .bind(event.cause.as_str())
    .bind(&event.message)
    .bind(&event.zip_file)
    .fetch_one(&mut *conn)
    .await?;
    Ok(id)
}

fn parse<T: std::str::FromStr>(row: &PgRow, index: usize, what: &str) -> Result<T> {
    let text: String = row.try_get(index)?;
    text.parse().map_err(|_| {
        crate::Error::Json(serde::de::Error::custom(format!(
            "change_events.{what} holds {text:?}"
        )))
    })
}

fn item(row: &PgRow) -> Result<ChangeItem> {
    let attempted: Option<String> = row.try_get(1)?;
    let micros: i64 = row.try_get(7)?;
    Ok(ChangeItem {
        outcome: parse(row, 0, "outcome")?,
        attempted: attempted
            .map(|a| {
                a.parse::<ChangeOutcome>().map_err(|_| {
                    crate::Error::Json(serde::de::Error::custom(format!(
                        "change_events.attempted holds {a:?}"
                    )))
                })
            })
            .transpose()?,
        kind: parse(row, 2, "kind")?,
        name: row.try_get(3)?,
        feature_count_change: row.try_get(4)?,
        cause: parse(row, 5, "cause")?,
        message: row.try_get(6)?,
        occurred_at: jiff::Timestamp::from_microsecond(micros).map_err(|e| {
            crate::Error::Json(serde::de::Error::custom(format!(
                "change_events.occurred_at {micros}: {e}"
            )))
        })?,
    })
}

impl Db {
    /// Inserts `event` on its own connection (autocommitted).
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when the insert fails.
    pub async fn record_change_event(&self, event: &NewChangeEvent) -> Result<i64> {
        let mut conn = self.conn().await?;
        record_change_event(&mut conn, event).await
    }

    /// One page of the days with events, newest first (`GET /v1/change_reports?page=`).
    ///
    /// `page` is zero-based; a page past the end is empty. Offset paging is fine here: there is
    /// at most one row per day.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
    pub async fn change_report_days(&self, page: i32, page_size: i32) -> Result<ChangeReportPage> {
        let page = page.max(0);
        let page_size = page_size.max(1);
        let mut conn = self.conn().await?;
        let total_days: i64 = sqlx::query_scalar("SELECT count(DISTINCT day) FROM change_events;")
            .fetch_one(&mut *conn)
            .await?;
        let rows = sqlx::query(
            "SELECT day::text,
                    count(*) FILTER (WHERE outcome <> 'error'),
                    count(*) FILTER (WHERE outcome = 'error')
             FROM change_events
             GROUP BY day ORDER BY day DESC
             LIMIT $1 OFFSET $2;",
        )
        .bind(i64::from(page_size))
        .bind(i64::from(page) * i64::from(page_size))
        .fetch_all(&mut *conn)
        .await?;
        let days = rows
            .iter()
            .map(|row| {
                Ok(ChangeReportDay {
                    date: row.try_get(0)?,
                    actions: row.try_get(1)?,
                    errors: row.try_get(2)?,
                })
            })
            .collect::<Result<Vec<_>>>()?;
        Ok(ChangeReportPage {
            page: days,
            page_index: page,
            page_size,
            total_days,
        })
    }

    /// The report of one UTC `day` (`YYYY-MM-DD`), or `None` when it has no events
    /// (`GET /v1/change_reports/{date}`).
    ///
    /// Errors come first, then the rest by name and id. `day` must already be a valid date:
    /// the cast fails otherwise.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails (a malformed `day` included), and
    /// [`Error::Json`](crate::Error::Json) when a row holds a value outside the wire enums.
    pub async fn change_report(&self, day: &str) -> Result<Option<ChangeReport>> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "SELECT outcome, attempted, kind, name, feature_count_change, cause, message,
                    (extract(epoch FROM occurred_at) * 1000000)::bigint
             FROM change_events
             WHERE day = $1::date
             ORDER BY (outcome <> 'error'), name, id;",
        )
        .bind(day)
        .fetch_all(&mut *conn)
        .await?;
        if rows.is_empty() {
            return Ok(None);
        }
        let items = rows.iter().map(item).collect::<Result<Vec<_>>>()?;
        let mut counts = ChangeCounts::default();
        for i in &items {
            match i.outcome {
                ChangeOutcome::New => counts.new += 1,
                ChangeOutcome::Update => counts.update += 1,
                ChangeOutcome::Delete => counts.delete += 1,
                ChangeOutcome::Nuke => counts.nuke += 1,
                ChangeOutcome::Error => counts.error += 1,
            }
        }
        Ok(Some(ChangeReport {
            date: day.to_owned(),
            counts,
            items,
        }))
    }
}

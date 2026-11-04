//! `GET /v1/change_reports` and `GET /v1/change_reports/{date}`: the durable history of chart
//! and base map changes (`docs/change_reports.md`).
//!
//! The wire values of [`ChangeOutcome`], [`ChangeCause`] and [`ChangeKind`] are also what the
//! `change_events` table stores, so they never change; the UI turns them into display text.

use jiff::Timestamp;
use serde::{Deserialize, Serialize};

use crate::string_enum;

string_enum! {
    /// What happened to a chart or base map, or `Nuke` for the whole server.
    pub enum ChangeOutcome {
        New = "new",
        Update = "update",
        Delete = "delete",
        Nuke = "nuke",
        Error = "error",
    }
}

string_enum! {
    /// Who asked for the change.
    pub enum ChangeCause {
        Admin = "admin",
        NoaaEncCron = "noaa_enc_cron",
    }
}

string_enum! {
    /// What the event is about: one chart, one base map shapefile, or everything (a nuke).
    pub enum ChangeKind {
        Chart = "chart",
        BaseMap = "base_map",
        All = "all",
    }
}

/// The sidecar an upload carries into `save/` (`<zip>.meta.json`), naming who sent it, so the
/// ingest worker records the cause of every change the zip makes.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct UploadMeta {
    pub cause: ChangeCause,
}

impl UploadMeta {
    /// The sidecar's file name for the zip `zip_file`.
    pub fn file_name(zip_file: &str) -> String {
        format!("{zip_file}.meta.json")
    }
}

/// One day of the change report list: its successful changes and its failures.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeReportDay {
    /// `YYYY-MM-DD`, UTC.
    pub date: String,
    /// Successful `new` + `update` + `delete` + `nuke` events.
    pub actions: i64,
    /// `error` events.
    pub errors: i64,
}

/// One page of the day list, newest first.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeReportPage {
    pub page: Vec<ChangeReportDay>,
    /// Zero-based.
    pub page_index: i32,
    pub page_size: i32,
    /// Days with at least one event, over the whole history.
    pub total_days: i64,
}

/// The per-outcome totals of one day.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeCounts {
    pub new: i64,
    pub update: i64,
    pub delete: i64,
    pub nuke: i64,
    pub error: i64,
}

/// One change event.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeItem {
    pub outcome: ChangeOutcome,
    /// For an `error`: what was being attempted.
    pub attempted: Option<ChangeOutcome>,
    pub kind: ChangeKind,
    /// The chart's `DSID_DSNM`, the shapefile name, or `*` for a nuke.
    pub name: String,
    /// The signed change in the stored feature count.
    pub feature_count_change: i64,
    pub cause: ChangeCause,
    /// The failure message of an `error`.
    pub message: Option<String>,
    #[serde(with = "crate::instant")]
    pub occurred_at: Timestamp,
}

/// One day's report: the counts and every event of that day.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeReport {
    /// `YYYY-MM-DD`, UTC.
    pub date: String,
    pub counts: ChangeCounts,
    pub items: Vec<ChangeItem>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wire_values_are_the_stored_values() {
        let outcomes: Vec<&str> = ChangeOutcome::ALL.iter().map(|o| o.as_str()).collect();
        assert_eq!(outcomes, ["new", "update", "delete", "nuke", "error"]);
        let causes: Vec<&str> = ChangeCause::ALL.iter().map(|c| c.as_str()).collect();
        assert_eq!(causes, ["admin", "noaa_enc_cron"]);
        let kinds: Vec<&str> = ChangeKind::ALL.iter().map(|k| k.as_str()).collect();
        assert_eq!(kinds, ["chart", "base_map", "all"]);
        assert_eq!(
            serde_json::to_string(&ChangeCause::NoaaEncCron).unwrap(),
            "\"noaa_enc_cron\""
        );
        assert_eq!(
            serde_json::from_str::<ChangeKind>("\"base_map\"").unwrap(),
            ChangeKind::BaseMap
        );
        assert!(serde_json::from_str::<ChangeOutcome>("\"nuked\"").is_err());
    }

    #[test]
    fn upload_meta_is_a_one_key_object() {
        let meta = UploadMeta {
            cause: ChangeCause::NoaaEncCron,
        };
        assert_eq!(
            serde_json::to_string(&meta).unwrap(),
            r#"{"cause":"noaa_enc_cron"}"#
        );
        assert_eq!(UploadMeta::file_name("a.zip"), "a.zip.meta.json");
    }

    #[test]
    fn report_round_trips_with_explicit_nulls() {
        let json = r#"{"date":"2026-10-31","counts":{"new":1,"update":0,"delete":0,"nuke":0,"error":1},"items":[{"outcome":"new","attempted":null,"kind":"chart","name":"US5WA22M.000","featureCountChange":5158,"cause":"noaa_enc_cron","message":null,"occurredAt":"2026-10-31T12:00:00Z"},{"outcome":"error","attempted":"update","kind":"base_map","name":"ne_10m_ocean.shp","featureCountChange":-3,"cause":"admin","message":"aborted","occurredAt":"2026-10-31T12:00:01.500Z"}]}"#;
        let report: ChangeReport = serde_json::from_str(json).unwrap();
        assert_eq!(report.items[1].attempted, Some(ChangeOutcome::Update));
        assert_eq!(serde_json::to_string(&report).unwrap(), json);
        let page = ChangeReportPage {
            page: vec![ChangeReportDay {
                date: "2026-10-31".into(),
                actions: 1,
                errors: 1,
            }],
            page_index: 0,
            page_size: 25,
            total_days: 1,
        };
        assert_eq!(
            serde_json::to_string(&page).unwrap(),
            r#"{"page":[{"date":"2026-10-31","actions":1,"errors":1}],"pageIndex":0,"pageSize":25,"totalDays":1}"#
        );
    }
}

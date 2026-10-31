//! The ingest tally (`ChartIngest.kt` `Report`): what the status file announces while charts are
//! installed and the `CompletionReport` sent at the end.
//!
//! Decision D5 (b): a chart's features are counted only after its transaction committed, so a
//! chart that failed is listed once in `failedCharts` with no features and never inflates the
//! totals. Kotlin counted every attempt of its retry loop. Rows inserted but not yet committed
//! are `pending`: they move the progress message so an operator sees a large chart advance, and
//! become counted ([`Report::commit_pending`]) or vanish ([`Report::drop_pending`]) with the
//! transaction.

use indexmap::IndexMap;
use njord_model::{InsertItem, WsMsg};
use std::time::Instant;

pub struct Report {
    total_feature_count: i64,
    total_chart_count: i32,
    aborted: bool,
    feature_install_count: i64,
    chart_install_count: i32,
    items: IndexMap<String, i64>,
    pending: IndexMap<String, i64>,
    failed: IndexMap<String, String>,
    started: Instant,
}

impl Report {
    pub fn new(total_feature_count: i64, total_chart_count: i32) -> Report {
        Report {
            total_feature_count,
            total_chart_count,
            aborted: false,
            feature_install_count: 0,
            chart_install_count: 0,
            items: IndexMap::new(),
            pending: IndexMap::new(),
            failed: IndexMap::new(),
            started: Instant::now(),
        }
    }

    pub fn abort(&mut self) {
        tracing::info!("result set to aborted");
        self.aborted = true;
    }

    pub fn is_aborted(&self) -> bool {
        self.aborted
    }

    /// `progressMessage`.
    pub fn progress(&self) -> WsMsg {
        WsMsg::Info {
            feature: self.feature_install_count + self.pending.values().sum::<i64>(),
            total_features: self.total_feature_count,
            chart: self.chart_install_count,
            total_charts: self.total_chart_count,
        }
    }

    /// `completionMessage`: `Idle` after an abort, else the report.
    pub fn completion(&self) -> WsMsg {
        if self.aborted {
            return WsMsg::Idle;
        }
        WsMsg::CompletionReport {
            total_feature_count: self.total_feature_count,
            total_chart_count: self.total_chart_count,
            items: self
                .items
                .iter()
                .map(|(chart_name, feature_count)| InsertItem {
                    chart_name: chart_name.clone(),
                    feature_count: *feature_count,
                })
                .collect(),
            failed_charts: self
                .failed
                .iter()
                .map(|(name, msg)| format!("{name} msg: {msg}"))
                .collect(),
            ms: self.started.elapsed().as_millis() as i64,
        }
    }

    /// `appendChartFeatureCount`.
    pub fn append_features(&mut self, chart_name: &str, count: i64) {
        self.feature_install_count += count;
        *self.items.entry(chart_name.to_owned()).or_insert(0) += count;
    }

    /// `failChart`: a second failure of the same chart appends to its message.
    pub fn fail_chart(&mut self, name: &str, msg: &str) {
        match self.failed.get_mut(name) {
            Some(existing) => {
                existing.push_str(", ");
                existing.push_str(msg);
            }
            None => {
                self.failed.insert(name.to_owned(), msg.to_owned());
            }
        }
    }

    /// Rows inserted for `chart_name` in a transaction that has not committed yet.
    pub fn add_pending(&mut self, chart_name: &str, count: i64) {
        *self.pending.entry(chart_name.to_owned()).or_insert(0) += count;
    }

    /// The chart's transaction committed `count` rows: they are counted now (D5).
    pub fn commit_pending(&mut self, chart_name: &str, count: i64) {
        self.pending.shift_remove(chart_name);
        self.append_features(chart_name, count);
    }

    /// The chart's transaction rolled back: its rows were never installed.
    pub fn drop_pending(&mut self, chart_name: &str) {
        self.pending.shift_remove(chart_name);
    }

    /// `completedChart`.
    pub fn completed_chart(&mut self) {
        self.chart_install_count += 1;
    }
}

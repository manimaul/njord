//! `ingest/RegionExportWorker.kt` (P-CC-3 `testdata/concurrency/cadence.json`): renders stale
//! regions one at a time, forever. While zips wait in `save/` or this instance holds the lock it
//! polls every [`RegionWorker::POLL`]; after a render it loops at once (more regions may be
//! stale); when nothing is stale it idles [`RegionWorker::IDLE`]. [`AppState::wake_region_worker`]
//! cuts either wait short (after an ingest, a chart delete or `POST /v1/regions`).

use std::sync::Arc;
use std::time::Duration;

use super::export::{ExportResult, export_next};
use crate::ingest::IngestWorker;
use crate::state::AppState;

pub struct RegionWorker {
    state: Arc<AppState>,
}

impl RegionWorker {
    /// `POLL_DELAY_MS`.
    pub const POLL: Duration = Duration::from_secs(5);
    /// `IDLE_DELAY_MS`.
    pub const IDLE: Duration = Duration::from_secs(15 * 60);

    pub fn new(state: Arc<AppState>) -> RegionWorker {
        RegionWorker { state }
    }

    pub async fn run(self) {
        loop {
            tracing::debug!("region export worker run");
            if !IngestWorker::pending_zips(&self.state).is_empty() || self.state.lock.acquired() {
                self.idle(Self::POLL).await;
                continue;
            }
            match export_next(&self.state).await {
                ExportResult::Rendered => tracing::info!("region export worker rendered"),
                ExportResult::LockBusy => {
                    tracing::info!("region export worker lock busy");
                    self.idle(Self::POLL).await;
                }
                ExportResult::NothingToDo => {
                    tracing::debug!("region export worker nothing to do");
                    self.idle(Self::IDLE).await;
                }
            }
        }
    }

    /// Sleeps `d`, or until woken.
    async fn idle(&self, d: Duration) {
        let _ = tokio::time::timeout(d, self.state.region_wake.notified()).await;
    }
}

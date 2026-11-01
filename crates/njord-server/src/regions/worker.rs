//! The region export worker: renders stale regions one at a time, forever.
//!
//! The cadence is pinned by `testdata/concurrency/cadence.json`. While zips wait in `save/` or this
//! instance holds the lock it polls every [`RegionWorker::POLL`]; after a render it loops at once
//! (more regions may be stale); when nothing is stale it idles [`RegionWorker::IDLE`].
//! [`AppState::wake_region_worker`] cuts either wait short (after an ingest, a chart delete or
//! `POST /v1/regions`).

use std::sync::Arc;
use std::time::Duration;

use super::export::{ExportResult, export_next};
use crate::ingest::IngestWorker;
use crate::state::AppState;

/// The background task that keeps region archives current; see the [module docs](self).
pub struct RegionWorker {
    state: Arc<AppState>,
}

impl RegionWorker {
    /// The wait while an ingest is queued or running, or the lock is busy.
    pub const POLL: Duration = Duration::from_secs(5);
    /// The wait when no region is stale.
    pub const IDLE: Duration = Duration::from_secs(15 * 60);

    pub fn new(state: Arc<AppState>) -> RegionWorker {
        RegionWorker { state }
    }

    /// Runs forever.
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

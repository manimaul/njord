//! The ingest worker: claims uploaded zips from `save/` one at a time and ingests them.
//!
//! Every [`IngestWorker::POLL`] (`testdata/concurrency/cadence.json`) the worker looks in
//! `<chartTempData>/save` for a `.zip`; when there is one it takes the lock, empties
//! `<chartTempData>/ingest`, renames one zip into `ingest/<uuid>/` (an atomic claim against other
//! replicas) and ingests it, as charts ([`super::chart`]) or as Natural Earth shapefiles
//! ([`super::natural_earth`]) when the zip holds a `.shp`. One zip per lock cycle; the lock is
//! released by the ingest.
//!
//! The zip's `.meta.json` sidecar ([`super::cause`]) is renamed into the claim directory with it;
//! a zip without one is still claimed.
//!
//! An upload does not abort an ingest in flight (`POST /v1/enc_save` only stores the zip), so a
//! second zip waits here for the first to finish. After an ingest the worker re-checks `save/` at
//! once rather than after the poll delay, and announces `Extracting(0)` before taking the lock,
//! so a client that connects between two queued ingests sees work queued rather than the
//! previous completion report.

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use njord_model::WsMsg;

use crate::state::AppState;

/// The background task that claims and ingests uploads; see the [module docs](self).
pub struct IngestWorker {
    state: Arc<AppState>,
}

impl IngestWorker {
    /// How often `save/` is checked.
    pub const POLL: Duration = Duration::from_secs(5);

    pub fn new(state: Arc<AppState>) -> IngestWorker {
        IngestWorker { state }
    }

    /// Runs forever.
    pub async fn run(self) {
        let _ = std::fs::create_dir_all(&self.state.ingest_dir);
        let _ = std::fs::create_dir_all(&self.state.save_dir);
        self.state.status.init_if_needed();
        loop {
            tokio::time::sleep(Self::POLL).await;
            while self.try_claim_and_ingest().await {}
        }
    }

    /// The `.zip` files waiting in `save/`, by name.
    pub fn pending_zips(state: &AppState) -> Vec<PathBuf> {
        let mut zips: Vec<PathBuf> = std::fs::read_dir(&state.save_dir)
            .map(|entries| {
                entries
                    .flatten()
                    .map(|e| e.path())
                    .filter(|p| {
                        p.is_file()
                            && p.file_name().is_some_and(|n| {
                                n.to_string_lossy().to_ascii_lowercase().ends_with(".zip")
                            })
                    })
                    .collect()
            })
            .unwrap_or_default();
        zips.sort();
        zips
    }

    /// Claims and ingests one waiting zip; returns whether an ingest ran, so the caller looks
    /// again at once.
    pub async fn try_claim_and_ingest(&self) -> bool {
        let state = &self.state;
        let zips = Self::pending_zips(state);
        if zips.is_empty() {
            return false;
        }
        if state.lock.is_free() {
            state
                .status
                .write_ignore_lock(&WsMsg::Extracting { progress: 0.0 });
        }
        if !state.lock.try_acquire().await {
            return false;
        }
        tracing::info!("lock acquired to perform chart ingestion");
        tracing::info!("clearing work dir");
        let _ = tokio::fs::remove_dir_all(&state.ingest_dir).await;
        let claim_dir = state.ingest_dir.join(state.lock.uuid());
        if let Err(e) = tokio::fs::create_dir_all(&claim_dir).await {
            tracing::error!("{}: {e}", claim_dir.display());
            state.lock.try_clear();
            return false;
        }
        let mut claimed: Option<PathBuf> = None;
        for zip in zips {
            let name = zip.file_name().unwrap_or_default();
            let dest = claim_dir.join(name);
            if tokio::fs::rename(&zip, &dest).await.is_ok() {
                let zip_file = name.to_string_lossy();
                let _ = tokio::fs::rename(
                    super::cause::sidecar_path(&state.save_dir, &zip_file),
                    super::cause::sidecar_path(&claim_dir, &zip_file),
                )
                .await;
                claimed = Some(dest);
                break;
            }
        }
        let Some(claimed) = claimed.filter(|p| p.metadata().is_ok_and(|m| m.len() > 0)) else {
            tracing::info!("claimed files empty - clearing lock and cleaning up");
            let _ = tokio::fs::remove_dir_all(&claim_dir).await;
            state.lock.try_clear();
            return false;
        };
        let zip_file = claimed
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_default();
        let has_shapefiles = super::zip::entry_names(&claimed)
            .map(|names| {
                names
                    .iter()
                    .any(|n| n.to_ascii_lowercase().ends_with(".shp"))
            })
            .unwrap_or(false);
        if has_shapefiles {
            super::natural_earth::ingest(state, &claim_dir, &zip_file).await;
        } else {
            super::chart::ingest(state, &claim_dir, &zip_file).await;
        }
        true
    }
}

//! `<chartTempData>/status.json` (`ingest/IngestStatus.kt`; P-CC-2): the current ingest state as
//! one JSON [`WsMsg`], written atomically (temp file and rename) so a WebSocket poller or another
//! replica never reads a torn file. [`IngestStatus::write`] only writes while this instance holds
//! the lock, so a replica whose ingest was aborted cannot overwrite the operator's `Idle`.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use njord_model::WsMsg;

use crate::lock::DistributedLock;

pub struct IngestStatus {
    path: PathBuf,
    lock: Arc<DistributedLock>,
}

impl IngestStatus {
    pub fn new(path: impl Into<PathBuf>, lock: Arc<DistributedLock>) -> IngestStatus {
        IngestStatus {
            path: path.into(),
            lock,
        }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// The file's text, `""` when missing; the WebSocket route sends `Idle` for an empty one.
    pub fn read(&self) -> String {
        std::fs::read_to_string(&self.path).unwrap_or_default()
    }

    /// `WsMsg.Idle` as the file and the socket carry it.
    pub fn idle_json() -> String {
        serde_json::to_string(&WsMsg::Idle).expect("WsMsg serialises")
    }

    /// `initIfNeeded`: a server starting on a volume without a status file writes `Idle`. (Kotlin
    /// went through the lock-conditional write and so never wrote it; the socket answered `Idle`
    /// for the missing file anyway, so the visible behaviour is the same.)
    pub fn init_if_needed(&self) {
        if !self.path.is_file() {
            self.write_ignore_lock(&WsMsg::Idle);
        }
    }

    /// `writeMsg`: writes only while this instance holds the lock.
    pub fn write(&self, msg: &WsMsg) {
        if self.lock.acquired() {
            self.write_ignore_lock(msg);
        }
    }

    /// `writeMsgIgnoreLock`: writes unconditionally (the upload and reset routes).
    pub fn write_ignore_lock(&self, msg: &WsMsg) {
        let json = serde_json::to_string(msg).expect("WsMsg serialises");
        let dir = self.path.parent().unwrap_or(Path::new("."));
        let name = self
            .path
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_else(|| "status.json".to_owned());
        let tmp = dir.join(format!("{name}.{}.tmp", uuid::Uuid::new_v4().simple()));
        let result = std::fs::create_dir_all(dir)
            .and_then(|()| std::fs::write(&tmp, json))
            .and_then(|()| std::fs::rename(&tmp, &self.path));
        if let Err(e) = result {
            tracing::error!("status {}: {e}", self.path.display());
            let _ = std::fs::remove_file(&tmp);
        }
    }
}

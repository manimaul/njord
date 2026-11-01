//! The ingest/export lock shared between replicas through the data volume.
//!
//! The semantics are pinned by `testdata/concurrency/lock_semantics.json`, and the file protocol is
//! the one existing data volumes already use: `<chartTempData>/lock` holds the owner's uuid as text
//! while held and is empty (or missing) while free. Acquiring writes the uuid to `lock.<random>`
//! beside it and renames that over the lock; clearing renames an empty file over it. Anyone may
//! clear: clearing is the operator's abort (`DELETE /v1/enc_save`), and there is no liveness
//! information on shared storage that would make ownership meaningful.
//!
//! There is no check-then-rename window: after the rename the acquirer waits
//! [`DistributedLock::SETTLE`] and re-reads, and holds the lock only if the file still names it.
//! Two instances that both saw an empty file and both renamed therefore both re-read the same
//! winner, and the loser reports failure instead of running.
//!
//! [`DistributedLock::acquired`] reads the file each time rather than caching it behind a file
//! watch: it is a few bytes, it is asked a few times per layer at most, and a read is never
//! staler than a watch.

use std::path::{Path, PathBuf};
use std::time::Duration;

use crate::signature::random_uuid;

/// The lock file at `<chartTempData>/lock`, as seen by one instance.
pub struct DistributedLock {
    path: PathBuf,
    uuid: String,
    settle: Duration,
}

impl DistributedLock {
    /// How long an acquirer waits before re-reading the lock.
    ///
    /// It is longer than one second, so an instance that polls the lock directory once a second
    /// (the fallback when a file watch is unavailable) has seen the change.
    pub const SETTLE: Duration = Duration::from_millis(1500);

    /// A lock on `path` (`<chartTempData>/lock`) with a fresh instance uuid.
    pub fn new(path: impl Into<PathBuf>) -> DistributedLock {
        DistributedLock {
            path: path.into(),
            uuid: random_uuid(),
            settle: Self::SETTLE,
        }
    }

    /// A shorter settle, for tests that exercise the protocol rather than the race.
    pub fn with_settle(self, settle: Duration) -> DistributedLock {
        DistributedLock { settle, ..self }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// This instance's uuid, the text the lock file holds while this instance owns it.
    pub fn uuid(&self) -> &str {
        &self.uuid
    }

    /// The lock file's text, `""` when it is missing or unreadable.
    pub fn holder(&self) -> String {
        std::fs::read_to_string(&self.path).unwrap_or_default()
    }

    /// Whether the lock currently names this instance.
    pub fn acquired(&self) -> bool {
        self.holder() == self.uuid
    }

    /// Whether nobody holds the lock.
    pub fn is_free(&self) -> bool {
        self.holder().is_empty()
    }

    /// Claims the lock if the file is empty or missing, settles, and answers whether this
    /// instance holds it afterwards.
    pub async fn try_acquire(&self) -> bool {
        if self.is_free() {
            if let Err(e) = self.rename_over(self.uuid.as_bytes()) {
                tracing::warn!("lock {}: {e}", self.path.display());
            }
            tokio::time::sleep(self.settle).await;
        }
        let acquired = self.acquired();
        tracing::info!("try acquire lock acquired = {acquired}");
        acquired
    }

    /// Frees the lock whoever holds it, logging the evicted uuid.
    ///
    /// Returns `false` only when the file could not be written.
    pub fn try_clear(&self) -> bool {
        let holder = self.holder();
        if !holder.is_empty() && holder != self.uuid {
            tracing::warn!("clearing lock held by {holder}");
        }
        match self.rename_over(b"") {
            Ok(()) => true,
            Err(e) => {
                tracing::error!("clear lock {}: {e}", self.path.display());
                false
            }
        }
    }

    /// Writes `content` to `lock.<random>` in the lock directory and renames it over the lock.
    fn rename_over(&self, content: &[u8]) -> std::io::Result<()> {
        let dir = self.path.parent().unwrap_or(Path::new("."));
        std::fs::create_dir_all(dir)?;
        let tmp = dir.join(format!("lock.{}", uuid::Uuid::new_v4().simple()));
        std::fs::write(&tmp, content)?;
        std::fs::rename(&tmp, &self.path).inspect_err(|_| {
            let _ = std::fs::remove_file(&tmp);
        })
    }
}

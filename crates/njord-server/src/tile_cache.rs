//! The on-disk tile cache (`db/TileCache.kt`): `<chartTempData>/tiles/{z}/{x}/{y}.mvt`, the
//! layout the deb package and the operations docs describe, shared with a Kotlin install.
//!
//! Best effort: every I/O error is logged and treated as a miss, never as a failed request. A
//! tile is written to `{y}.mvt.<random>.tmp` and renamed into place, so a reader on a shared NFS
//! volume never sees a torn file.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use njord_model::TileCacheCounts;

pub struct TileCache {
    base: PathBuf,
}

impl TileCache {
    /// The cache under `base` (`<chartTempData>/tiles`), created if missing.
    pub fn new(base: impl Into<PathBuf>) -> TileCache {
        let base = base.into();
        if let Err(e) = std::fs::create_dir_all(&base) {
            tracing::warn!("tile cache {}: {e}", base.display());
        }
        TileCache { base }
    }

    pub fn base(&self) -> &Path {
        &self.base
    }

    fn path(&self, z: i32, x: i32, y: i32) -> PathBuf {
        self.base.join(format!("{z}/{x}/{y}.mvt"))
    }

    /// The cached tile; `None` when absent, empty or unreadable.
    pub async fn get(&self, z: i32, x: i32, y: i32) -> Option<Vec<u8>> {
        match tokio::fs::read(self.path(z, x, y)).await {
            Ok(bytes) if !bytes.is_empty() => Some(bytes),
            Ok(_) => None,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => None,
            Err(e) => {
                tracing::warn!("tile cache read error {z}/{x}/{y}: {e}");
                None
            }
        }
    }

    /// Stores a non-empty tile.
    pub async fn put(&self, z: i32, x: i32, y: i32, data: &[u8]) {
        if data.is_empty() {
            return;
        }
        let dir = self.base.join(format!("{z}/{x}"));
        let tmp = dir.join(format!("{y}.mvt.{}.tmp", nonce()));
        let result = async {
            tokio::fs::create_dir_all(&dir).await?;
            tokio::fs::write(&tmp, data).await?;
            tokio::fs::rename(&tmp, self.path(z, x, y)).await
        };
        if let Err(e) = result.await {
            tracing::warn!("tile cache write error {z}/{x}/{y}: {e}");
            let _ = tokio::fs::remove_file(&tmp).await;
        }
    }

    /// Deletes every cached tile (after ingest, chart deletion or `DELETE /v1/cache`).
    pub async fn clear(&self) {
        if !tokio::fs::metadata(&self.base)
            .await
            .is_ok_and(|m| m.is_dir())
        {
            return;
        }
        let result = async {
            tokio::fs::remove_dir_all(&self.base).await?;
            tokio::fs::create_dir_all(&self.base).await
        };
        match result.await {
            Ok(()) => tracing::info!("tile cache cleared"),
            Err(e) => tracing::warn!("tile cache clear error: {e}"),
        }
    }

    /// `GET /v1/cache`: the number of `.mvt` files per zoom directory, for every directory whose
    /// name is an integer, in directory listing order. Empty on error.
    pub fn counts(&self) -> TileCacheCounts {
        let mut counts = TileCacheCounts::new();
        let Ok(entries) = std::fs::read_dir(&self.base) else {
            return counts;
        };
        for entry in entries.flatten() {
            let Some(z) = entry
                .file_name()
                .to_str()
                .and_then(|n| n.parse::<i32>().ok())
            else {
                continue;
            };
            if entry.path().is_dir() {
                counts.insert(z, count_mvt(&entry.path()));
            }
        }
        counts
    }
}

fn count_mvt(dir: &Path) -> i32 {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return 0;
    };
    entries
        .flatten()
        .map(|e| {
            let path = e.path();
            if path.is_dir() {
                count_mvt(&path)
            } else {
                i32::from(path.to_string_lossy().ends_with(".mvt"))
            }
        })
        .sum()
}

/// A per-process unique suffix for temporary files.
fn nonce() -> u64 {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| d.subsec_nanos() as u64);
    (u64::from(std::process::id()) << 32) ^ nanos ^ COUNTER.fetch_add(1, Ordering::Relaxed)
}

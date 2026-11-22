//! Recovery from a worker process that stopped while it held the lock.
//!
//! A process that is killed in the middle of an ingest or a region export (out of memory, a node
//! failure) leaves its uuid in the lock file. The file carries no sign of whether its holder is
//! alive, so no later process could tell that lock from a live one, and nothing would be
//! ingested again until an operator reset the ingest (`DELETE /v1/enc_save`).
//!
//! [`recover_interrupted`] ends that when the workers start. It relies on one rule: **at most one
//! process runs the workers on a data volume.** A process that is starting holds nothing, so
//! under that rule whatever the lock names has stopped. Where two processes ran the workers on
//! one volume, the second to start would abort the first one's work.
//!
//! The interrupted upload is dropped, not queued again: an upload that kills the process would
//! otherwise kill it on every start. Rows the interrupted ingest had already inserted stay; the
//! same upload replaces them when it is sent again.

use std::path::Path;

use njord_model::WsMsg;

use super::IngestStatus;
use crate::lock::DistributedLock;

/// The work a stopped process left unfinished, as [`recover_interrupted`] found it.
#[derive(Debug, PartialEq, Eq)]
pub struct Interrupted {
    /// The uuid the lock file named.
    pub holder: String,
    /// The zip files the stopped process had claimed from `save/` and not finished, by name.
    /// Empty when it stopped during a region export, or before it claimed anything.
    pub uploads: Vec<String>,
}

/// Frees a lock that another process left held, and clears away that process's unfinished ingest.
///
/// Call it once, before this process's workers start, and only where no other process runs the
/// workers on the same data volume (see the [module docs](self)). Returns `None`, having changed
/// nothing, when the lock is free or names this instance.
///
/// Otherwise it removes `ingest_dir` with the claimed upload in it, sets the status, and clears
/// the lock last. The status becomes a fatal [`WsMsg::Error`] naming the dropped upload, so that
/// whoever sent it learns that it has to be sent again. When no upload was claimed, a status that
/// still reports work in progress becomes [`WsMsg::Idle`] and any other is kept.
pub fn recover_interrupted(
    lock: &DistributedLock,
    status: &IngestStatus,
    ingest_dir: &Path,
) -> Option<Interrupted> {
    let holder = lock.holder();
    if holder.is_empty() || holder == lock.uuid() {
        return None;
    }
    let uploads = claimed_zips(ingest_dir);
    tracing::warn!(
        "the lock is held by {holder}, which is not running; its unfinished work is dropped: {}",
        if uploads.is_empty() {
            "no claimed upload".to_owned()
        } else {
            uploads.join(", ")
        }
    );
    if let Err(e) = std::fs::remove_dir_all(ingest_dir)
        && e.kind() != std::io::ErrorKind::NotFound
    {
        tracing::warn!("{}: {e}", ingest_dir.display());
    }
    if uploads.is_empty() {
        let in_progress = matches!(
            serde_json::from_str(&status.read()),
            Ok(WsMsg::Info { .. } | WsMsg::Extracting { .. })
        );
        if in_progress {
            status.write_ignore_lock(&WsMsg::Idle);
        }
    } else {
        status.write_ignore_lock(&WsMsg::Error {
            is_fatal: true,
            message: format!(
                "The ingest of {} was interrupted: the ingest process stopped before it \
                 finished. Upload it again.",
                uploads.join(", ")
            ),
        });
    }
    lock.try_clear();
    Some(Interrupted { holder, uploads })
}

/// The names of the `.zip` files in the claim directories under `ingest_dir`, sorted.
fn claimed_zips(ingest_dir: &Path) -> Vec<String> {
    let entries = |dir: &Path| {
        std::fs::read_dir(dir)
            .into_iter()
            .flatten()
            .flatten()
            .map(|entry| entry.path())
            .collect::<Vec<_>>()
    };
    let mut zips: Vec<String> = entries(ingest_dir)
        .iter()
        .filter(|claim_dir| claim_dir.is_dir())
        .flat_map(|claim_dir| entries(claim_dir))
        .filter(|path| path.is_file())
        .filter_map(|path| Some(path.file_name()?.to_string_lossy().into_owned()))
        .filter(|name| name.to_ascii_lowercase().ends_with(".zip"))
        .collect();
    zips.sort();
    zips
}

//! The lock file protocol and the status file (`testdata/concurrency/lock_semantics.json`).
//!
//! These are behavioural tests, since a race cannot be golden-filed.

use std::sync::Arc;
use std::time::Duration;

use njord_model::WsMsg;
use njord_server::ingest::{IngestStatus, Interrupted, recover_interrupted};
use njord_server::lock::DistributedLock;

fn dir(name: &str) -> std::path::PathBuf {
    let dir = std::path::Path::new(env!("CARGO_TARGET_TMPDIR"))
        .join("njord-server-tests/lock")
        .join(name);
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

fn lock(dir: &std::path::Path) -> DistributedLock {
    DistributedLock::new(dir.join("lock")).with_settle(Duration::from_millis(20))
}

fn entries(dir: &std::path::Path) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(dir)
        .unwrap()
        .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    names
}

#[tokio::test]
async fn the_lock_file_holds_the_owner_uuid_and_an_empty_file_means_unlocked() {
    let d = dir("format");
    let lock = lock(&d);
    assert!(!lock.path().exists());
    assert!(lock.is_free());
    assert!(lock.try_acquire().await);
    assert_eq!(std::fs::read_to_string(lock.path()).unwrap(), lock.uuid());
    assert!(lock.acquired());
    assert!(lock.try_clear());
    assert!(lock.path().exists(), "clearing leaves an empty file");
    assert_eq!(std::fs::read_to_string(lock.path()).unwrap(), "");
    assert!(lock.is_free() && !lock.acquired());
    assert_eq!(
        entries(&d),
        ["lock"],
        "no temp files left by the rename protocol"
    );
}

#[tokio::test]
async fn a_held_lock_cannot_be_acquired_and_a_stale_uuid_is_never_ours() {
    let d = dir("held");
    let first = lock(&d);
    let second = lock(&d);
    assert!(first.try_acquire().await);
    assert!(!second.try_acquire().await);
    assert_eq!(std::fs::read_to_string(first.path()).unwrap(), first.uuid());
    let stale = dir("stale");
    std::fs::write(stale.join("lock"), "some-other-process-uuid").unwrap();
    assert!(!lock(&stale).try_acquire().await);
}

#[tokio::test]
async fn a_non_owner_may_clear_the_lock() {
    let d = dir("clear");
    let owner = lock(&d);
    let other = lock(&d);
    assert!(owner.try_acquire().await);
    assert!(other.try_clear());
    assert!(!owner.acquired());
    assert!(other.try_acquire().await);
    assert!(!owner.try_acquire().await);
}

/// Instances racing on one file settle on exactly one holder, and the settle re-read makes
/// every loser report failure.
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn contention_settles_on_exactly_one_holder() {
    let d = dir("race");
    let locks: Vec<Arc<DistributedLock>> = (0..8).map(|_| Arc::new(lock(&d))).collect();
    let claims = futures_util::future::join_all(locks.iter().map(|l| {
        let l = l.clone();
        tokio::spawn(async move { l.try_acquire().await })
    }))
    .await;
    let winners: Vec<bool> = claims.into_iter().map(|c| c.unwrap()).collect();
    assert_eq!(winners.iter().filter(|w| **w).count(), 1, "{winners:?}");
    let holder = std::fs::read_to_string(d.join("lock")).unwrap();
    let holders: Vec<&Arc<DistributedLock>> = locks.iter().filter(|l| l.uuid() == holder).collect();
    assert_eq!(holders.len(), 1);
    for l in &locks {
        if l.uuid() != holder {
            assert!(!l.try_acquire().await);
        }
    }
}

#[tokio::test]
async fn status_writes_only_under_the_lock_and_are_atomic() {
    let d = dir("status");
    let lock = Arc::new(lock(&d));
    let status = IngestStatus::new(d.join("status.json"), lock.clone());
    status.write(&WsMsg::Extracting { progress: 0.5 });
    assert!(!status.path().exists(), "no lock, no write");
    assert_eq!(status.read(), "");
    status.write_ignore_lock(&WsMsg::Idle);
    assert_eq!(status.read(), IngestStatus::idle_json());
    assert!(lock.try_acquire().await);
    let info = WsMsg::Info {
        feature: 1,
        total_features: 2,
        chart: 0,
        total_charts: 1,
    };
    status.write(&info);
    assert_eq!(status.read(), serde_json::to_string(&info).unwrap());
    assert_eq!(serde_json::from_str::<WsMsg>(&status.read()).unwrap(), info);
    lock.try_clear();
    status.write(&WsMsg::Idle);
    assert_eq!(
        status.read(),
        serde_json::to_string(&info).unwrap(),
        "released lock, write ignored"
    );
    status.init_if_needed();
    assert_eq!(
        serde_json::from_str::<WsMsg>(&status.read()).unwrap(),
        info,
        "an existing status is not reset"
    );
    assert_eq!(entries(&d), ["lock", "status.json"], "no .tmp left behind");
    let fresh = dir("status_fresh");
    let fresh_lock = Arc::new(DistributedLock::new(fresh.join("lock")));
    let status = IngestStatus::new(fresh.join("status.json"), fresh_lock);
    status.init_if_needed();
    assert_eq!(status.read(), IngestStatus::idle_json());
}

/// A data volume as a process killed mid-ingest leaves it: the lock names the dead process, its
/// claim directory holds the upload it had claimed, and the status reports progress.
fn killed_mid_ingest(name: &str) -> (std::path::PathBuf, Arc<DistributedLock>, IngestStatus) {
    let d = dir(name);
    std::fs::write(d.join("lock"), "dead-process-uuid").unwrap();
    let claim = d.join("ingest/dead-process-uuid");
    std::fs::create_dir_all(&claim).unwrap();
    std::fs::write(claim.join("ne_10m_ocean.zip"), b"zip").unwrap();
    std::fs::write(claim.join("ne_10m_ocean.zip.meta.json"), b"{}").unwrap();
    std::fs::write(claim.join("ne_10m_ocean.shp"), b"shp").unwrap();
    let lock = Arc::new(lock(&d));
    let status = IngestStatus::new(d.join("status.json"), lock.clone());
    status.write_ignore_lock(&WsMsg::Info {
        feature: 0,
        total_features: 1,
        chart: 0,
        total_charts: 1,
    });
    (d, lock, status)
}

#[tokio::test]
async fn a_starting_worker_frees_the_lock_of_a_process_killed_mid_ingest() {
    let (d, lock, status) = killed_mid_ingest("recover_ingest");
    assert!(!lock.try_acquire().await, "the dead holder blocks everyone");

    let found = recover_interrupted(&lock, &status, &d.join("ingest"));
    assert_eq!(
        found,
        Some(Interrupted {
            holder: "dead-process-uuid".into(),
            uploads: vec!["ne_10m_ocean.zip".into()],
        })
    );
    assert!(lock.is_free());
    assert!(!d.join("ingest").exists(), "the claimed upload is dropped");
    let WsMsg::Error { is_fatal, message } = serde_json::from_str(&status.read()).unwrap() else {
        panic!(
            "the status must report the dropped upload: {}",
            status.read()
        );
    };
    assert!(
        is_fatal && message.contains("ne_10m_ocean.zip") && message.contains("Upload it again")
    );
    assert!(lock.try_acquire().await, "ingestion goes on");
    assert_eq!(entries(&d), ["lock", "status.json"]);
}

#[tokio::test]
async fn recovery_without_a_claimed_upload_keeps_a_finished_status() {
    // A process killed during a region export claimed nothing, and the status is the last
    // ingest's completion report.
    let d = dir("recover_export");
    std::fs::write(d.join("lock"), "dead-process-uuid").unwrap();
    let lock = Arc::new(lock(&d));
    let status = IngestStatus::new(d.join("status.json"), lock.clone());
    let report = WsMsg::CompletionReport {
        total_feature_count: 3,
        total_chart_count: 1,
        items: Vec::new(),
        failed_charts: Vec::new(),
        ms: 10,
    };
    status.write_ignore_lock(&report);
    let found = recover_interrupted(&lock, &status, &d.join("ingest")).unwrap();
    assert!(found.uploads.is_empty());
    assert!(lock.is_free());
    assert_eq!(
        serde_json::from_str::<WsMsg>(&status.read()).unwrap(),
        report
    );

    // Killed after taking the lock and before claiming: the status still says work is under way.
    std::fs::write(d.join("lock"), "dead-process-uuid").unwrap();
    status.write_ignore_lock(&WsMsg::Extracting { progress: 0.0 });
    recover_interrupted(&lock, &status, &d.join("ingest")).unwrap();
    assert_eq!(status.read(), IngestStatus::idle_json());
}

#[tokio::test]
async fn recovery_leaves_a_free_lock_and_its_own_lock_alone() {
    let (d, lock, status) = killed_mid_ingest("recover_noop");
    let before = status.read();
    let claimed = d.join("ingest/dead-process-uuid/ne_10m_ocean.zip");

    lock.try_clear();
    assert_eq!(recover_interrupted(&lock, &status, &d.join("ingest")), None);
    assert!(claimed.exists() && status.read() == before);

    assert!(lock.try_acquire().await);
    assert_eq!(recover_interrupted(&lock, &status, &d.join("ingest")), None);
    assert!(lock.acquired() && claimed.exists() && status.read() == before);
}

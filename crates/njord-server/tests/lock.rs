//! P-CC-1 and P-CC-2 (`testdata/concurrency/lock_semantics.json`, decision D6): the lock file
//! protocol and the status file, as behavioural tests, since a race cannot be golden-filed.

use std::sync::Arc;
use std::time::Duration;

use njord_model::WsMsg;
use njord_server::ingest::IngestStatus;
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

/// D6: instances racing on one file settle on exactly one holder, and the settle re-read makes
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

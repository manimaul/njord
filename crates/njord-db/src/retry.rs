//! The retry wrapper replacing `Dao.sqlOpAsync` (P-DB-7, `db/retry.json`), with decision D5.
//!
//! Kotlin re-ran the whole block after *any* exception, up to 7 times, so a chart install that
//! failed after writing rows was replayed and its features counted on every attempt. Here only
//! getting a connection is retried: [`acquire`] retries a connection (or `BEGIN`) that could not
//! be obtained, with Kotlin's attempt count and backoff, and the caller then runs its statements
//! exactly once. A statement that fails is final. Cancellation is the future being dropped, which
//! propagates by construction (decision D12; Kotlin swallowed it into `null`).

use std::future::Future;
use std::time::Duration;

use crate::Error;

/// How hard [`acquire`] tries: `max_attempts` attempts, sleeping `backoff × attempt` after each
/// failed one but the last (Kotlin: 7 attempts, 100 ms, so 2.1 s in all).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RetryPolicy {
    pub max_attempts: u32,
    pub backoff: Duration,
}

impl RetryPolicy {
    /// `Dao.MAX_TRY_COUNT`.
    pub const MAX_TRY_COUNT: u32 = 7;

    /// Kotlin's `sqlOpAsync(tryCount = n)`.
    pub fn with_max_attempts(max_attempts: u32) -> RetryPolicy {
        RetryPolicy {
            max_attempts,
            ..RetryPolicy::default()
        }
    }
}

impl Default for RetryPolicy {
    fn default() -> RetryPolicy {
        RetryPolicy {
            max_attempts: RetryPolicy::MAX_TRY_COUNT,
            backoff: Duration::from_millis(100),
        }
    }
}

/// Whether a failure to get a connection is worth another attempt: timeouts, I/O and TLS
/// failures, a crashed worker, and server errors of SQLSTATE class `08` (connection exception)
/// or `57P0x` (shutdown, cannot connect now). A closed pool, bad configuration or a refused
/// login are not.
pub fn is_retryable(e: &sqlx::Error) -> bool {
    match e {
        sqlx::Error::PoolTimedOut
        | sqlx::Error::Io(_)
        | sqlx::Error::Tls(_)
        | sqlx::Error::Protocol(_)
        | sqlx::Error::WorkerCrashed => true,
        sqlx::Error::Database(db) => db
            .code()
            .is_some_and(|c| c.starts_with("08") || c.starts_with("57P0")),
        _ => false,
    }
}

/// Runs `attempt` until it yields a connection, at most `policy.max_attempts` times, sleeping
/// `backoff × n` after the n-th failure. A non-retryable failure, or the last one, is returned
/// as [`Error::Connection`].
pub async fn acquire<C, F, Fut>(policy: RetryPolicy, mut attempt: F) -> Result<C, Error>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Result<C, sqlx::Error>>,
{
    let mut n = 1;
    loop {
        match attempt().await {
            Ok(conn) => return Ok(conn),
            Err(e) if n < policy.max_attempts && is_retryable(&e) => {
                tracing::warn!("database connection attempt {n} failed: {e}");
                tokio::time::sleep(policy.backoff * n).await;
                n += 1;
            }
            Err(e) => return Err(Error::Connection(e)),
        }
    }
}

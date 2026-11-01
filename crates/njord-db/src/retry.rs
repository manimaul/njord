//! Retrying connection acquisition (fixture `db/retry.json`).
//!
//! Only getting a connection is retried: [`acquire`] retries a connection (or `BEGIN`) that could
//! not be obtained, and the caller then runs its statements exactly once. A statement that fails
//! is final, because re-running a block that had already written rows would replay its writes
//! (a chart install would count its features on every attempt). Cancellation is the future
//! being dropped, which propagates by construction.

use std::future::Future;
use std::time::Duration;

use crate::Error;

/// How hard [`acquire`] tries.
///
/// [`acquire`] makes `max_attempts` attempts, sleeping `backoff × attempt` after each failed one
/// but the last. The default is 7 attempts and 100 ms, so 2.1 s of sleep in all.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RetryPolicy {
    /// Attempts in all, the first included.
    pub max_attempts: u32,
    /// The sleep after the first failed attempt; the n-th failure sleeps n times as long.
    pub backoff: Duration,
}

impl RetryPolicy {
    /// The default number of attempts.
    pub const MAX_TRY_COUNT: u32 = 7;

    /// The default policy with `max_attempts` attempts.
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

/// Runs `attempt` until it yields a connection, at most `policy.max_attempts` times.
///
/// Sleeps `backoff × n` (on the tokio clock) after the n-th failure when it
/// [is retryable](is_retryable).
///
/// # Errors
///
/// A non-retryable failure, or the last one, is returned as [`Error::Connection`].
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

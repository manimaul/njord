//! P-DB-7: the retry policy against `db/retry.json`, under paused tokio time so the backoff is
//! exact. The block is never re-run (decision D5), so each case runs the connection attempts
//! through `njord_db::retry::acquire` and then the block once; the cases' `port.expected` is
//! that policy. The `cancel` cases are a block failing on its first run (cancellation of a Rust
//! future is its drop, which cannot be retried either).

use njord_db::RetryPolicy;
use njord_db::retry::acquire;
use njord_testdata::{Failures, cases, first_difference, port_expected, strip_nulls};
use serde::Deserialize;
use serde_json::{Value, json};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RetryInput {
    /// `connection()` calls failing before one succeeds (`-1`: all of them).
    null_connections: i64,
    /// block runs failing before one succeeds (`-1`: all of them).
    block_failures: i64,
    #[serde(default)]
    cancel: bool,
    try_count: Option<u32>,
}

async fn run(input: &RetryInput) -> Value {
    let policy = input
        .try_count
        .map_or_else(RetryPolicy::default, RetryPolicy::with_max_attempts);
    let start = tokio::time::Instant::now();
    let mut attempts = 0;
    let conn = acquire(policy, || {
        attempts += 1;
        let fail = input.null_connections < 0 || attempts <= input.null_connections;
        async move {
            if fail {
                Err(sqlx::Error::PoolTimedOut)
            } else {
                Ok(())
            }
        }
    })
    .await;
    let mut invocations = 0;
    let result = match conn {
        Ok(()) => {
            invocations += 1;
            let fails = input.cancel || input.block_failures != 0;
            (!fails).then(|| format!("ok after {invocations}"))
        }
        Err(e) => {
            assert!(matches!(e, njord_db::Error::Connection(_)), "{e}");
            None
        }
    };
    let mut outcome = json!({
        "result": result,
        "connectionAttempts": attempts,
        "blockInvocations": invocations,
        "totalDelayMs": start.elapsed().as_millis() as u64,
    });
    strip_nulls(&mut outcome);
    outcome
}

#[tokio::test(start_paused = true)]
async fn p_db_7_retry() {
    let cases = cases("db/retry.json");
    let mut failures = Failures::default();
    for (i, case) in cases.iter().enumerate() {
        let input: RetryInput = serde_json::from_value(case["input"].clone()).unwrap();
        let actual = run(&input).await;
        if let Some(d) = first_difference(port_expected(case), &actual, "") {
            failures.push(format!("case {i} {}: {d}", case["input"]));
        }
    }
    failures.assert_none(cases.len());
}

#[tokio::test(start_paused = true)]
async fn non_retryable_errors_fail_at_once() {
    let mut attempts = 0;
    let result: Result<(), _> = acquire(RetryPolicy::default(), || {
        attempts += 1;
        async { Err(sqlx::Error::PoolClosed) }
    })
    .await;
    assert!(matches!(
        result,
        Err(njord_db::Error::Connection(sqlx::Error::PoolClosed))
    ));
    assert_eq!(attempts, 1);
}

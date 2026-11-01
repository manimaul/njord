//! Minimal stdout logging.
//!
//! This runs as a Kubernetes CronJob, so `kubectl logs` is the only consumer and plain prefixed
//! lines are all that is needed.

/// Prints `message` with an `INFO` prefix.
pub fn info(message: impl std::fmt::Display) {
    println!("INFO  {message}");
}

/// Prints `message` with a `WARN` prefix.
pub fn warn(message: impl std::fmt::Display) {
    println!("WARN  {message}");
}

/// Prints `message` with an `ERROR` prefix.
pub fn error(message: impl std::fmt::Display) {
    println!("ERROR {message}");
}

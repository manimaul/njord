//! Minimal stdout logging (`Log.kt`). This runs as a Kubernetes CronJob, so `kubectl logs` is the
//! only consumer and plain prefixed lines are all that is needed.

pub fn info(message: impl std::fmt::Display) {
    println!("INFO  {message}");
}

pub fn warn(message: impl std::fmt::Display) {
    println!("WARN  {message}");
}

pub fn error(message: impl std::fmt::Display) {
    println!("ERROR {message}");
}

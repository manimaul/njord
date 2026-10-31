//! Build identity for `GET /v1/about/version` (Kotlin's Gradle-generated `VersionInfo`): the git
//! hash and branch of the working tree and the build time. `NJORD_GIT_HASH`, `NJORD_GIT_BRANCH`
//! and `NJORD_BUILD_DATE` override them, for builds outside a git checkout (container images).

use std::process::Command;

fn git(args: &[&str]) -> Option<String> {
    let out = Command::new("git").args(args).output().ok()?;
    out.status
        .success()
        .then(|| String::from_utf8_lossy(&out.stdout).trim().to_owned())
        .filter(|s| !s.is_empty())
}

fn main() {
    for var in ["NJORD_GIT_HASH", "NJORD_GIT_BRANCH", "NJORD_BUILD_DATE"] {
        println!("cargo:rerun-if-env-changed={var}");
    }
    if let Some(git_dir) = git(&["rev-parse", "--absolute-git-dir"]) {
        println!("cargo:rerun-if-changed={git_dir}/HEAD");
        println!("cargo:rerun-if-changed={git_dir}/refs/heads");
    }
    let hash = std::env::var("NJORD_GIT_HASH")
        .ok()
        .or_else(|| git(&["rev-parse", "--short", "HEAD"]))
        .unwrap_or_else(|| "unknown".to_owned());
    let branch = std::env::var("NJORD_GIT_BRANCH")
        .ok()
        .or_else(|| git(&["rev-parse", "--abbrev-ref", "HEAD"]))
        .unwrap_or_else(|| "unknown".to_owned());
    // Kotlin's format, local time: `2026-10-30-12:18:14`.
    let date = std::env::var("NJORD_BUILD_DATE")
        .unwrap_or_else(|_| jiff::Zoned::now().strftime("%Y-%m-%d-%H:%M:%S").to_string());
    println!("cargo:rustc-env=NJORD_GIT_HASH={hash}");
    println!("cargo:rustc-env=NJORD_GIT_BRANCH={branch}");
    println!("cargo:rustc-env=NJORD_BUILD_DATE={date}");
}

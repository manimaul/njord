//! The Njord ingest binary: the ingest and region export workers without the HTTP server.
//!
//! # Usage
//!
//! ```text
//! njord-ingest <resources dir>
//! ```
//!
//! It takes the same configuration as `njord-server` (`<resources dir>/config/application.json`
//! with `CHART_SERVER_OPTS` merged over it, see [`njord_server::ChartsConfig`]), migrates the
//! database schema, and runs the workers ([`njord_server::spawn_ingest_workers`]) until SIGINT or
//! SIGTERM. `enableIngestion` is not consulted; `enableRegionExport` still is. It runs as the
//! `njord-ingest-rs` pod beside server pods that have `enableIngestion: false`. Logging follows
//! `RUST_LOG` (default `info`).

use std::path::PathBuf;
use std::process::ExitCode;

use njord_server::{
    AppState, ChartsConfig, connect, migrate, shutdown_signal, spawn_ingest_workers,
};
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> ExitCode {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .init();

    let Some(resources) = std::env::args_os()
        .nth(1)
        .map(PathBuf::from)
        .filter(|p| p.exists())
    else {
        println!("Path to resources directory was not supplied");
        return ExitCode::FAILURE;
    };
    match ingest(resources).await {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            tracing::error!("{e}");
            ExitCode::FAILURE
        }
    }
}

async fn ingest(resources: PathBuf) -> Result<(), Box<dyn std::error::Error>> {
    let config = ChartsConfig::from_env(&resources)?;
    let db = connect(&config)?;
    let version = migrate(&db).await?;
    tracing::info!("database schema version {version}");
    let state = AppState::new(config, &resources, db)?;
    let _workers = spawn_ingest_workers(&state);
    shutdown_signal().await;
    tracing::info!("Shutting down ingest...");
    Ok(())
}

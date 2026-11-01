//! The Njord chart server binary: the HTTP API with the ingest and region export workers.
//!
//! # Usage
//!
//! ```text
//! njord-server <resources dir>
//! ```
//!
//! The server reads its configuration ([`njord_server::ChartsConfig`]) from
//! `<resources dir>/config/application.json`, with the JSON object in `CHART_SERVER_OPTS`
//! shallowly merged over it, migrates the database schema on `pgConnectionInfo`, and serves
//! [`njord_server::router`] on the configured `host` and `port` until SIGINT or SIGTERM. When
//! `enableIngestion` is set, the ingest worker runs beside it, and the region export worker too
//! unless `enableRegionExport` is off. Logging follows `RUST_LOG` (default `info`).
//!
//! The process exits with a failure status when the resources directory is missing, the
//! configuration cannot be read, or the database cannot be migrated.

use std::path::PathBuf;
use std::process::ExitCode;

use njord_server::{
    AppState, ChartsConfig, connect, migrate, router, shutdown_signal, spawn_workers,
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
    match serve(resources).await {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            tracing::error!("{e}");
            ExitCode::FAILURE
        }
    }
}

async fn serve(resources: PathBuf) -> Result<(), Box<dyn std::error::Error>> {
    let config = ChartsConfig::from_env(&resources)?;
    let addr = (config.host.clone(), config.port);
    let db = connect(&config)?;
    let version = migrate(&db).await?;
    tracing::info!("database schema version {version}");
    let state = AppState::new(config, &resources, db)?;
    let _workers = spawn_workers(&state);
    let listener = tokio::net::TcpListener::bind(addr).await?;
    tracing::info!("listening on {}", listener.local_addr()?);
    axum::serve(listener, router(state))
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    tracing::info!("Shutting down server...");
    Ok(())
}

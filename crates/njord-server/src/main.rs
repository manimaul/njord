//! `njord-server <resources dir>` (`Main.kt`): configuration from
//! `<resources dir>/config/application.json` and `CHART_SERVER_OPTS`, then the database
//! schema migration on `pgConnectionInfo`, then the HTTP server on the configured `host` and
//! `port` until SIGINT or SIGTERM.

use std::path::PathBuf;
use std::process::ExitCode;

use njord_server::{AppState, ChartsConfig, connect, migrate, router};
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
    let listener = tokio::net::TcpListener::bind(addr).await?;
    tracing::info!("listening on {}", listener.local_addr()?);
    axum::serve(listener, router(state))
        .with_graceful_shutdown(shutdown())
        .await?;
    tracing::info!("Shutting down server...");
    Ok(())
}

async fn shutdown() {
    let interrupt = tokio::signal::ctrl_c();
    #[cfg(unix)]
    {
        let mut term = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("SIGTERM handler");
        tokio::select! {
            _ = interrupt => {}
            _ = term.recv() => {}
        }
    }
    #[cfg(not(unix))]
    let _ = interrupt.await;
}

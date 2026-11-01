//! Natural Earth base map ingest: a zip of shapefiles, installed as `base_features` rows.
//!
//! Each shapefile replaces the rows of its file name and scale. Progress, abort and clean-up
//! follow [`super::chart`].

use std::path::{Path, PathBuf};

use njord_model::WsMsg;
use njord_s57::{Shapefile, normalize_layer_name, scale_from_filename};

use super::Report;
use super::chart::{file_name, unzip};
use crate::state::AppState;

/// Ingests the Natural Earth zip `<claim_dir>/<zip_file>`, which the worker claimed while it held
/// the lock.
///
/// Does nothing when this instance does not hold the lock; the outcome goes to the status file.
pub async fn ingest(state: &AppState, claim_dir: &Path, zip_file: &str) {
    if !state.lock.acquired() {
        tracing::info!("NE ingest noop {zip_file} - lock not acquired");
        return;
    }
    match ingest_internal(state, claim_dir, zip_file).await {
        Ok(mut report) => {
            tracing::info!("Natural Earth ingestion complete {zip_file}");
            cleanup(state, claim_dir).await;
            if !state.lock.acquired() {
                report.abort();
            }
            state.status.write(&report.completion());
        }
        Err(e) => {
            tracing::error!("Natural Earth ingest {zip_file} failed: {e}");
            cleanup(state, claim_dir).await;
            state.status.write(&WsMsg::Error {
                is_fatal: true,
                message: e,
            });
        }
    }
    state.lock.try_clear();
}

async fn cleanup(state: &AppState, claim_dir: &Path) {
    tracing::info!("cleaning up resources");
    state.tile_cache.clear().await;
    if let Err(e) = tokio::fs::remove_dir_all(claim_dir).await
        && e.kind() != std::io::ErrorKind::NotFound
    {
        tracing::warn!("{}: {e}", claim_dir.display());
    }
}

async fn ingest_internal(
    state: &AppState,
    claim_dir: &Path,
    zip_file: &str,
) -> Result<Report, String> {
    tracing::info!("ingesting Natural Earth upload {zip_file}");
    let files: Vec<PathBuf> = unzip(state, claim_dir, zip_file)
        .await?
        .into_iter()
        .filter(|f| f.to_string_lossy().to_ascii_lowercase().ends_with(".shp"))
        .collect();
    tracing::info!("found {} shapefile(s)", files.len());

    let mut opened = Vec::with_capacity(files.len());
    let mut total: u64 = 0;
    for path in files {
        let name = file_name(&path);
        let result = tokio::task::spawn_blocking(move || {
            Shapefile::open(&path).map(|shp| {
                let count = shp.feature_count();
                (shp, count)
            })
        })
        .await
        .map_err(|e| e.to_string())?;
        match result {
            Ok((shp, count)) => {
                total += count;
                opened.push((name, Some(shp)));
            }
            Err(e) => {
                tracing::error!("{name}: {e}");
                opened.push((name, None));
            }
        }
    }
    tracing::info!("total feature count: {total}");
    let mut report = Report::new(total as i64, opened.len() as i32);
    state.status.write(&report.progress());

    for (name, shp) in opened {
        if !state.lock.acquired() {
            report.abort();
            return Ok(report);
        }
        match shp {
            Some(shp) => process_shapefile(state, shp, &name, &mut report).await,
            None => report.fail_chart(&name, "failed to open"),
        }
        report.completed_chart();
    }
    Ok(report)
}

/// Deletes the shapefile's previous rows, then inserts every layer's repaired
/// features under its normalised layer name.
async fn process_shapefile(state: &AppState, shp: Shapefile, name: &str, report: &mut Report) {
    let scale = scale_from_filename(name);
    if let Err(e) = state.db.delete_base_features(name, scale).await {
        tracing::error!("{name}: delete: {e}");
    }
    let mut shp = shp;
    for layer in shp.layer_names() {
        if !state.lock.acquired() {
            report.abort();
            return;
        }
        let layer_name = normalize_layer_name(&layer);
        let (returned, rows) = tokio::task::spawn_blocking(move || {
            let rows: Vec<_> = shp
                .features(&layer)
                .unwrap_or_default()
                .into_iter()
                .filter_map(|f| {
                    f.geometry
                        .and_then(|g| g.make_valid())
                        .map(|g| (f.props, g.wkb()))
                })
                .collect();
            (shp, rows)
        })
        .await
        .expect("reading a shapefile layer does not panic");
        shp = returned;
        let mut count = 0i64;
        for (props, wkb) in &rows {
            if !state.lock.acquired() {
                report.abort();
                break;
            }
            match state
                .db
                .insert_base_feature(wkb, props, name, scale, &layer_name)
                .await
            {
                Ok(n) => count += n as i64,
                Err(e) => tracing::error!("{name} {layer_name}: {e}"),
            }
        }
        tracing::info!("inserted {count} feature(s) for {name} -> {layer_name}");
        report.append_features(name, count);
        state.status.write(&report.progress());
        if report.is_aborted() {
            return;
        }
    }
}

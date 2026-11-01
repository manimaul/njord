//! S-57 cell ingest: unzip the claimed upload and install every `.000` cell in it.
//!
//! Each cell is read with GDAL (`njord-s57`) and installed as a chart row plus one feature
//! transaction; progress goes through the status file. Afterwards the tile cache is cleared, the
//! lock released and the region export worker woken.
//!
//! - An unsafe zip entry aborts the extraction, and the upload is reported as a fatal `Error`.
//! - A chart's features are one transaction, never retried, and counted only once committed; an
//!   overwrite of an existing chart is one transaction in `njord-db`.
//! - An ingest stops, rolling its chart back, when the lock stops naming this instance (the
//!   operator's abort, `DELETE /v1/enc_save`).
//!
//! GDAL datasets are `Send` but not `Sync`, so a cell is read on the blocking pool one layer at a
//! time and handed back; the inserts in between are async. Up to `chartIngestWorkers` cells are
//! installed at once.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use futures_util::StreamExt;
use njord_model::{JsonObject, WsMsg};
use njord_s57::{GdalCell, S57Reader};

use super::Report;
use super::zip::{self, ZipError};
use crate::state::AppState;

/// Ingests `<claim_dir>/<zip_file>`, which the worker claimed while it held the lock.
///
/// Does nothing when this instance does not hold the lock. The outcome, a completion report or a
/// fatal error, is written to the status file; failures are not returned.
pub async fn ingest(state: &AppState, claim_dir: &Path, zip_file: &str) {
    if !state.lock.acquired() {
        tracing::info!("ingest noop {zip_file} - lock not acquired");
        return;
    }
    match ingest_internal(state, claim_dir, zip_file).await {
        Ok(mut report) => {
            tracing::info!("ingestion complete {zip_file}");
            cleanup(state, claim_dir).await;
            if !state.lock.acquired() {
                report.abort();
            }
            state.status.write(&report.completion());
        }
        Err(e) => {
            tracing::error!("ingest {zip_file} failed: {e}");
            cleanup(state, claim_dir).await;
            state.status.write(&WsMsg::Error {
                is_fatal: true,
                message: e,
            });
        }
    }
    state.lock.try_clear();
    state.wake_region_worker();
}

/// Clears the tile cache and removes the claimed work directory.
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
    tracing::info!("ingesting enc upload {zip_file}");
    let files = unzip(state, claim_dir, zip_file).await?;
    let cells: Vec<PathBuf> = files
        .into_iter()
        .filter(|f| f.to_string_lossy().ends_with(".000"))
        .collect();

    tracing::info!("counting features");
    let mut opened = Vec::with_capacity(cells.len());
    let mut total: u64 = 0;
    for path in cells {
        let name = file_name(&path);
        let result = tokio::task::spawn_blocking(move || {
            GdalCell::open(&path).map(|cell| {
                let count = cell.total_feature_count();
                (cell, count)
            })
        })
        .await
        .map_err(|e| e.to_string())?;
        match result {
            Ok((cell, count)) => {
                total += count;
                opened.push((name, Some(cell)));
            }
            Err(e) => {
                tracing::error!("{name}: {e}");
                opened.push((name, None));
            }
        }
    }
    tracing::info!("total feature count: {total}");
    let report = Arc::new(Mutex::new(Report::new(total as i64, opened.len() as i32)));
    state.status.write(&report.lock().unwrap().progress());

    tracing::info!("inserting data");
    let workers = state.config.chart_ingest_workers.max(1) as usize;
    futures_util::stream::iter(opened)
        .map(|(name, cell)| {
            let report = report.clone();
            async move {
                match cell {
                    Some(cell) => install_cell(state, cell, &name, &report).await,
                    None => report.lock().unwrap().fail_chart(&name, "failed to open"),
                }
            }
        })
        .buffer_unordered(workers)
        .collect::<Vec<()>>()
        .await;
    let report = Arc::try_unwrap(report)
        .map_err(|_| "report still shared".to_owned())?
        .into_inner()
        .map_err(|e| e.to_string())?;
    Ok(report)
}

/// Extracts the upload with `Extracting` progress per entry and returns the files written.
///
/// A zip that cannot be read, or holds an unsafe entry, is the error reported for the upload.
pub(super) async fn unzip(
    state: &AppState,
    claim_dir: &Path,
    zip_file: &str,
) -> Result<Vec<PathBuf>, String> {
    state.status.write(&WsMsg::Extracting { progress: 0.0 });
    let zip_path = claim_dir.join(zip_file);
    if !zip_path.is_file() {
        return Err(format!(
            "chart dir file does not exist {}",
            zip_path.display()
        ));
    }
    let status = state.status.clone();
    let dir = claim_dir.to_path_buf();
    let result = tokio::task::spawn_blocking(move || {
        zip::extract(&zip_path, &dir, zip::skip_macosx, |done, total| {
            let progress = if total == 0 {
                1.0
            } else {
                done as f32 / total as f32
            };
            status.write(&WsMsg::Extracting { progress });
        })
    })
    .await
    .map_err(|e| e.to_string())?;
    let files = match result {
        Ok(files) => files,
        Err(ZipError::Unsafe(name)) => {
            return Err(format!("{zip_file} rejected: unsafe zip entry {name:?}"));
        }
        Err(e) => return Err(format!("{zip_file}: {e}")),
    };
    state.status.write(&WsMsg::Extracting { progress: 1.0 });
    Ok(files)
}

/// Installs one cell: its chart row (overwriting a chart of the same name), then every layer's
/// features in one transaction. Failures are recorded on the report under the `.000` file name.
async fn install_cell(state: &AppState, cell: GdalCell, name: &str, report: &Mutex<Report>) {
    tracing::info!("inserting chart {name}");
    let (cell, insert) = tokio::task::spawn_blocking(move || {
        let insert = cell.chart_insert();
        (cell, insert)
    })
    .await
    .expect("chart_insert does not panic");
    let insert = match insert {
        Ok(insert) => insert,
        Err(e) => {
            report.lock().unwrap().fail_chart(name, &e.0);
            return;
        }
    };
    let chart = match state.db.insert_chart(&insert, true).await {
        Ok(chart) => chart,
        Err(e) => {
            tracing::error!("{name}: chart insert: {e}");
            report.lock().unwrap().fail_chart(name, "chart insert");
            return;
        }
    };
    let mut install = match state.db.begin_feature_install(&chart.name).await {
        Ok(install) => install,
        Err(e) => {
            tracing::error!("{name}: {e}");
            report.lock().unwrap().fail_chart(name, "chart insert");
            return;
        }
    };
    let mut cell = cell;
    let mut aborted = false;
    let mut failed: Option<String> = None;
    for layer in cell.inserted_layers() {
        if !state.lock.acquired() {
            report.lock().unwrap().abort();
            aborted = true;
            break;
        }
        let (returned, layer, rows) = tokio::task::spawn_blocking(move || {
            let rows = read_layer(&cell, &layer);
            (cell, layer, rows)
        })
        .await
        .expect("reading a layer does not panic");
        cell = returned;
        for (props, wkb) in &rows {
            if let Err(e) = install.insert(&layer, props, wkb).await {
                tracing::error!("{name} {layer}: {e}");
                failed = Some(e.to_string());
                break;
            }
        }
        if failed.is_some() {
            break;
        }
        let mut r = report.lock().unwrap();
        r.add_pending(&chart.name, rows.len() as i64);
        state.status.write(&r.progress());
    }
    drop(cell);
    if aborted || failed.is_some() {
        report.lock().unwrap().drop_pending(&chart.name);
        if let Err(e) = install.rollback().await {
            tracing::warn!("{name}: rollback: {e}");
        }
        if let Some(msg) = failed {
            report.lock().unwrap().fail_chart(name, &msg);
        }
    } else {
        let committed = install.commit().await;
        let mut r = report.lock().unwrap();
        match committed {
            Ok(count) => r.commit_pending(&chart.name, count as i64),
            Err(e) => {
                r.drop_pending(&chart.name);
                r.fail_chart(name, &e.to_string());
            }
        }
    }
    let mut r = report.lock().unwrap();
    r.completed_chart();
    state.status.write(&r.progress());
}

/// The rows of one layer: `(props, wkb)` of every feature with a geometry, repaired with GDAL's
/// `makeValid` (a feature whose geometry cannot be repaired is skipped).
fn read_layer(cell: &GdalCell, layer: &str) -> Vec<(JsonObject, Vec<u8>)> {
    cell.features(layer)
        .unwrap_or_default()
        .into_iter()
        .filter_map(|f| {
            f.geometry
                .and_then(|g| g.make_valid())
                .map(|g| (f.props, g.wkb()))
        })
        .collect()
}

pub(super) fn file_name(path: &Path) -> String {
    path.file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_default()
}

//! `BundleBuilder.kt`: assembles downloaded NOAA cells into bundle zips and publishes them into
//! the ingest worker's `save/` directory (P-EC-3).
//!
//! Bundling is required rather than an optimisation: the ingest worker claims exactly one zip per
//! lock cycle and polls every 5 s, so shipping cells individually would mean one full lock /
//! extract / GDAL open / insert / tile-cache-invalidate cycle per cell. Bundles stay small for the
//! opposite reason: the ingest opens every `.000` in a bundle up front.

use std::fs::File;
use std::io::{BufReader, BufWriter};
use std::path::{Path, PathBuf};

use zip::ZipArchive;
use zip::write::SimpleFileOptions;

use crate::catalog::EncCatalogEntry;
use crate::config::EncCronConfig;
use crate::log;
use crate::transport::{EncTransport, Error};

pub struct BundleBuilder<'a, T> {
    config: &'a EncCronConfig,
    http: &'a T,
}

impl<'a, T: EncTransport> BundleBuilder<'a, T> {
    pub fn new(config: &'a EncCronConfig, http: &'a T) -> Self {
        Self { config, http }
    }

    /// Downloads `cells`, stages them, writes one bundle zip and publishes it. Returns the
    /// published bundle, or `None` if nothing could be staged.
    ///
    /// Cells that fail to download or extract are logged and skipped - a single 404 must not lose
    /// the rest of the batch. Nothing is recorded locally, so a skipped cell is retried next run.
    pub async fn build_and_publish(
        &self,
        cells: &[&EncCatalogEntry],
        bundle_name: &str,
    ) -> Result<Option<PathBuf>, Error> {
        let work = self.config.work_dir();
        let staging = work.join("staging");
        let download = work.join("download");
        remove(&staging);
        remove(&download);
        std::fs::create_dir_all(&staging)?;
        std::fs::create_dir_all(&download)?;

        let mut staged = 0;
        for cell in cells {
            let cell_zip = download.join(format!("{}.zip", cell.cell));
            let result = match self.http.download(&cell.url, &cell_zip).await {
                Ok(_) => extract_cell(&cell_zip, &cell.cell, &staging),
                Err(e) => Err(e),
            };
            match result {
                Ok(()) => staged += 1,
                Err(e) => log::warn(format!("skipping {}: {e}", cell.cell)),
            }
            remove(&cell_zip);
        }

        if staged == 0 {
            log::warn(format!(
                "no cells staged for {bundle_name}, nothing to publish"
            ));
            remove(&staging);
            return Ok(None);
        }

        let bundle = work.join(bundle_name);
        let files = staged_files(&staging)?;
        if let Err(e) = write_zip(&bundle, &staging, &files) {
            remove(&bundle);
            remove(&staging);
            remove(&download);
            return Err(e);
        }
        remove(&staging);
        remove(&download);

        let published = self.config.save_dir().join(bundle_name);
        if let Err(e) = std::fs::rename(&bundle, &published) {
            log::error(format!(
                "failed to publish {bundle_name} into {}: {e}",
                self.config.save_dir().display()
            ));
            remove(&bundle);
            return Ok(None);
        }
        log::info(format!(
            "published {bundle_name} ({staged} cells, {} files)",
            files.len()
        ));
        Ok(Some(published))
    }
}

fn remove(path: &Path) {
    let _ = if path.is_dir() {
        std::fs::remove_dir_all(path)
    } else {
        std::fs::remove_file(path)
    };
}

/// Extracts one NOAA cell zip into the shared staging root, keeping only `ENC_ROOT/<CELL>/`.
///
/// Entry names pass through untouched, so the `.000`, its `.001..` updates and the sibling `.TXT`
/// files land in one directory - exactly the layout GDAL's S57 driver needs to apply updates. The
/// other entries are dropped: `ENC_ROOT/CATALOG.031` is per cell and would collide across the
/// bundle, and `README.TXT` / `USERAGREEMENT.TXT` are boilerplate.
///
/// Decision D3: every entry name is checked before anything is written, and an entry that would
/// land outside the staging root (`..`, absolute, a backslash) fails the whole cell. Kotlin
/// extracted it wherever it pointed and kept the cell.
fn extract_cell(cell_zip: &Path, cell: &str, staging: &Path) -> Result<(), Error> {
    let prefix = format!("ENC_ROOT/{cell}/");
    let mut zip = ZipArchive::new(BufReader::new(File::open(cell_zip)?))?;
    let mut wanted = Vec::new();
    for i in 0..zip.len() {
        let entry = zip.by_index_raw(i)?;
        let name = entry.name()?.into_owned();
        let safe = !name.contains('\\') && entry.enclosed_name().is_some();
        if !safe {
            return Err(format!("unsafe entry name in {cell}.zip: {name}").into());
        }
        if !entry.is_dir() && name.starts_with(&prefix) {
            wanted.push((i, name));
        }
    }
    if wanted.is_empty() {
        return Err(format!("cell zip for {cell} contained no {prefix} entries").into());
    }
    for (i, name) in wanted {
        let mut entry = zip.by_index(i)?;
        let dest = staging.join(&name);
        if let Some(parent) = dest.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let mut out = BufWriter::new(File::create(&dest)?);
        std::io::copy(&mut entry, &mut out)?;
    }
    Ok(())
}

/// Every file under `staging`, as entry names relative to it, sorted.
fn staged_files(staging: &Path) -> Result<Vec<String>, Error> {
    fn walk(dir: &Path, root: &Path, out: &mut Vec<String>) -> std::io::Result<()> {
        for entry in std::fs::read_dir(dir)? {
            let path = entry?.path();
            if path.is_dir() {
                walk(&path, root, out)?;
            } else {
                let relative = path.strip_prefix(root).expect("walked under the root");
                out.push(
                    relative
                        .to_string_lossy()
                        .replace(std::path::MAIN_SEPARATOR, "/"),
                );
            }
        }
        Ok(())
    }
    let mut files = Vec::new();
    walk(staging, staging, &mut files)?;
    files.sort();
    Ok(files)
}

fn write_zip(bundle: &Path, staging: &Path, files: &[String]) -> Result<(), Error> {
    let mut writer = zip::ZipWriter::new(BufWriter::new(File::create(bundle)?));
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    for name in files {
        writer.start_file(name.as_str(), options)?;
        std::io::copy(
            &mut BufReader::new(File::open(staging.join(name))?),
            &mut writer,
        )?;
    }
    writer.finish()?;
    Ok(())
}

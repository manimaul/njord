//! Extracting an uploaded zip, refusing unsafe entry names.
//!
//! The zip entry cases of `testdata/security/paths.json` pin the refusals.
//!
//! Every entry name is checked before anything is written: an absolute name, a `..` component, a
//! backslash or a NUL (`zip::ZipFile::enclosed_name()` answers `None`, and `\` is refused on
//! top because it is a name character on unix but a separator on Windows) aborts
//! the whole extraction with [`ZipError::Unsafe`], so a hostile or corrupt zip never yields a
//! half-extracted cell. A leading `./` is dropped. Entries the caller's `skip` predicate accepts
//! (ingest skips `__MACOSX/`) are not written.

use std::fmt;
use std::fs::File;
use std::path::{Path, PathBuf};

use zip::ZipArchive;

/// Why a zip could not be read or extracted.
#[derive(Debug)]
pub enum ZipError {
    /// Reading the zip or writing an entry failed.
    Io(std::io::Error),
    /// The zip is malformed.
    Zip(zip::result::ZipError),
    /// An entry whose name would write outside the target directory.
    Unsafe(String),
}

impl fmt::Display for ZipError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ZipError::Io(e) => write!(f, "{e}"),
            ZipError::Zip(e) => write!(f, "{e}"),
            ZipError::Unsafe(name) => write!(f, "unsafe zip entry name {name:?}"),
        }
    }
}

impl std::error::Error for ZipError {}

impl From<std::io::Error> for ZipError {
    fn from(e: std::io::Error) -> Self {
        ZipError::Io(e)
    }
}

impl From<zip::result::ZipError> for ZipError {
    fn from(e: zip::result::ZipError) -> Self {
        ZipError::Zip(e)
    }
}

/// Where an entry may be written under `dir`, or `None` when it must not be written at all.
pub fn entry_target(dir: &Path, name: &str) -> Option<PathBuf> {
    if name.contains('\\') || name.contains('\0') {
        return None;
    }
    Some(dir.join(zip_safe_relative(name)?))
}

/// The ingest's skip filter: macOS resource forks are not chart data.
pub fn skip_macosx(name: &str) -> bool {
    name.starts_with("__MACOSX")
}

/// `enclosed_name` for a bare name: relative, no `..`, no `.` prefix left over.
fn zip_safe_relative(name: &str) -> Option<PathBuf> {
    use std::path::Component;
    let path = Path::new(name);
    let mut out = PathBuf::new();
    for component in path.components() {
        match component {
            Component::Normal(part) => out.push(part),
            Component::CurDir => {}
            Component::ParentDir | Component::RootDir | Component::Prefix(_) => return None,
        }
    }
    Some(out)
}

/// The names of every entry (directories included).
///
/// # Errors
///
/// Returns [`ZipError`] when the zip cannot be opened or read.
pub fn entry_names(zip: &Path) -> Result<Vec<String>, ZipError> {
    let archive = ZipArchive::new(File::open(zip)?)?;
    archive
        .file_names()
        .map(|n| n.map(|n| n.into_owned()).map_err(ZipError::from))
        .collect()
}

/// Extracts `zip` into `dir`, leaving out the entries `skip` accepts, calling
/// `progress(done, total)` after each entry, and returns the files written (directories left
/// out), in archive order.
///
/// # Errors
///
/// Returns [`ZipError::Unsafe`], with nothing written, when any entry name is unsafe, skipped
/// or not; [`ZipError::Io`] or [`ZipError::Zip`] when the zip cannot be read or an entry
/// written.
pub fn extract(
    zip: &Path,
    dir: &Path,
    skip: impl Fn(&str) -> bool,
    mut progress: impl FnMut(usize, usize),
) -> Result<Vec<PathBuf>, ZipError> {
    let mut archive = ZipArchive::new(File::open(zip)?)?;
    let total = archive.len();
    let mut targets = Vec::with_capacity(total);
    for i in 0..total {
        let name = archive.by_index_raw(i)?.name()?.into_owned();
        match entry_target(dir, &name) {
            None => return Err(ZipError::Unsafe(name)),
            Some(target) => targets.push((!skip(&name)).then_some(target)),
        }
    }
    let mut written = Vec::new();
    for (i, target) in targets.into_iter().enumerate() {
        let mut entry = archive.by_index(i)?;
        let Some(target) = target else {
            continue;
        };
        if entry.is_dir() {
            std::fs::create_dir_all(&target)?;
        } else {
            if let Some(parent) = target.parent() {
                std::fs::create_dir_all(parent)?;
            }
            let mut out = File::create(&target)?;
            std::io::copy(&mut entry, &mut out)?;
            written.push(target);
        }
        progress(i + 1, total);
    }
    Ok(written)
}

//! Who caused an upload: the `<zip>.meta.json` sidecar beside a zip in `save/`
//! ([`njord_model::UploadMeta`]), written before the zip is renamed in so the worker never
//! claims a zip without it, and moved into the claim directory with the zip.
//!
//! The upload handlers write `admin`; `njord-enc-cron` writes `noaa_enc_cron`. A zip without a
//! sidecar (copied in by hand, or from an older cron build) is read as `noaa_enc_cron` when its
//! name starts with [`ENC_BUNDLE_PREFIX`] and as `admin` otherwise.

use std::path::{Path, PathBuf};

use njord_model::{ChangeCause, UploadMeta};

/// The prefix of the bundles `njord-enc-cron` publishes (`enc_bundle-<stamp>-NNNN.zip`).
pub const ENC_BUNDLE_PREFIX: &str = "enc_bundle-";

/// `<dir>/<zip_file>.meta.json`.
pub fn sidecar_path(dir: &Path, zip_file: &str) -> PathBuf {
    dir.join(UploadMeta::file_name(zip_file))
}

/// Writes the sidecar naming `cause` for `zip_file` into `dir`.
///
/// # Errors
///
/// The file cannot be written.
pub async fn write_sidecar(dir: &Path, zip_file: &str, cause: ChangeCause) -> std::io::Result<()> {
    let json = serde_json::to_vec(&UploadMeta { cause }).expect("UploadMeta serialises");
    tokio::fs::write(sidecar_path(dir, zip_file), json).await
}

/// The cause a zip without a sidecar is read as.
pub fn fallback_cause(zip_file: &str) -> ChangeCause {
    if zip_file.starts_with(ENC_BUNDLE_PREFIX) {
        ChangeCause::NoaaEncCron
    } else {
        ChangeCause::Admin
    }
}

/// The cause of `zip_file` in `dir`: its sidecar's, else [`fallback_cause`]. A sidecar that
/// does not decode is logged and read as the fallback too.
pub fn read_cause(dir: &Path, zip_file: &str) -> ChangeCause {
    let path = sidecar_path(dir, zip_file);
    match std::fs::read(&path) {
        Ok(bytes) => match serde_json::from_slice::<UploadMeta>(&bytes) {
            Ok(meta) => meta.cause,
            Err(e) => {
                tracing::warn!(
                    "{}: {e}; assuming {}",
                    path.display(),
                    fallback_cause(zip_file)
                );
                fallback_cause(zip_file)
            }
        },
        Err(_) => fallback_cause(zip_file),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fallback_is_the_cron_for_its_bundles_only() {
        assert_eq!(
            fallback_cause("enc_bundle-20261031-0001.zip"),
            ChangeCause::NoaaEncCron
        );
        assert_eq!(fallback_cause("US5WA22M.zip"), ChangeCause::Admin);
        assert_eq!(fallback_cause("ENC_BUNDLE-x.zip"), ChangeCause::Admin);
    }

    #[tokio::test]
    async fn sidecar_round_trip_and_fallbacks() {
        let dir = std::env::temp_dir().join(format!("njord-cause-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        write_sidecar(&dir, "a.zip", ChangeCause::NoaaEncCron)
            .await
            .unwrap();
        assert_eq!(read_cause(&dir, "a.zip"), ChangeCause::NoaaEncCron);
        assert_eq!(
            read_cause(&dir, "enc_bundle-1.zip"),
            ChangeCause::NoaaEncCron
        );
        assert_eq!(read_cause(&dir, "b.zip"), ChangeCause::Admin);
        std::fs::write(sidecar_path(&dir, "c.zip"), b"{nope").unwrap();
        assert_eq!(read_cause(&dir, "c.zip"), ChangeCause::Admin);
        let _ = std::fs::remove_dir_all(&dir);
    }
}

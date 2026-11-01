//! The cron job's configuration.
//!
//! It is read from `<resources>/config/enc_cron.json`, then the `ENC_CRON_OPTS` JSON object is
//! merged over it, then the admin credentials come from `ENC_CRON_ADMIN_USER` /
//! `ENC_CRON_ADMIN_PASS`; see [`EncCronConfig::load`].

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

/// The environment variable holding a JSON object merged over `enc_cron.json`.
pub const OVERRIDES_ENV: &str = "ENC_CRON_OPTS";
/// The environment variable holding the admin user name.
pub const ADMIN_USER_ENV: &str = "ENC_CRON_ADMIN_USER";
/// The environment variable holding the admin password.
pub const ADMIN_PASS_ENV: &str = "ENC_CRON_ADMIN_PASS";

/// The cron job's settings.
///
/// Field names are the camel-case JSON keys. Fields without a default are required, and unknown
/// keys are ignored. Defaulted fields are left out when serialised at their default, which is
/// the form the `testdata/enc_cron/` fixtures record.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EncCronConfig {
    /// NOAA's ISO-19115 product catalog. ~52 MB, ~7k cells.
    pub catalog_url: String,
    /// Njord's bulk edition listing. In cluster this is the chart service, not the ingest pod.
    pub chart_editions_url: String,
    /// Ingest worker's shared volume. Bundles are published to `<chartTempData>/save`.
    pub chart_temp_data: PathBuf,
    /// Upper bound on cells fetched per invocation, so a cold catalog converges over several runs.
    pub max_cells_per_run: usize,
    /// Cells per bundle zip. Kept small because the ingest opens every `.000` in a bundle up
    /// front and pre-counts all their features before inserting anything.
    pub bundle_size_cells: usize,
    /// Secondary bundle cap for batches that happen to be large harbour cells.
    pub max_bundle_uncompressed_bytes: i64,
    /// Stop publishing when this many bundles are already queued in `save/`.
    #[serde(
        default = "default_max_queued_bundles",
        skip_serializing_if = "is_default_max_queued_bundles"
    )]
    pub max_queued_bundles: usize,
    /// Restrict to these S-57 compilation scales. Empty means all scales.
    pub scale_filter: Vec<i32>,
    /// Per request timeout for cell downloads and the catalog fetch.
    pub request_timeout_seconds: u64,
    /// Attempts per HTTP resource before giving up on it.
    pub max_retries: u32,
    /// Delete charts Njord holds that NOAA's catalog no longer lists, rather than only reporting
    /// them. Inert without [`admin_user`](Self::admin_user) / [`admin_pass`](Self::admin_pass).
    #[serde(default = "yes", skip_serializing_if = "is_true")]
    pub delete_orphans: bool,
    /// Ceiling on charts deleted in one run. A truncated catalog looks exactly like a mass
    /// withdrawal, so past this many the whole deletion pass is abandoned.
    #[serde(
        default = "default_max_orphan_deletes",
        skip_serializing_if = "is_default_max_orphan_deletes"
    )]
    pub max_orphan_deletes: usize,
    /// Basic auth credentials for `GET /v1/admin`. Prefer the environment variables.
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub admin_user: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub admin_pass: String,
}

fn yes() -> bool {
    true
}
fn is_true(b: &bool) -> bool {
    *b
}
fn default_max_queued_bundles() -> usize {
    3
}
fn is_default_max_queued_bundles(n: &usize) -> bool {
    *n == default_max_queued_bundles()
}
fn default_max_orphan_deletes() -> usize {
    25
}
fn is_default_max_orphan_deletes(n: &usize) -> bool {
    *n == default_max_orphan_deletes()
}

/// The configuration sources, as values, so tests need not touch the process environment.
#[derive(Default)]
pub struct Sources<'a> {
    pub resources_dir: Option<&'a Path>,
    pub overrides: Option<&'a str>,
    pub admin_user: Option<&'a str>,
    pub admin_pass: Option<&'a str>,
}

impl EncCronConfig {
    /// Loads `<resourcesDir>/config/enc_cron.json` if present, overlays `overrides` as a shallow
    /// JSON object merge (ignored when blank), then the credentials when non-blank. A missing
    /// file is an empty object, so the required fields then fail the load.
    ///
    /// # Errors
    ///
    /// The file cannot be read, the file or the overrides are not a JSON object, or the merged
    /// object is missing a required field or has one of the wrong type.
    pub fn load(sources: &Sources) -> Result<Self, String> {
        let mut merged = match sources
            .resources_dir
            .map(|d| d.join("config/enc_cron.json"))
        {
            Some(path) if path.exists() => {
                let text = std::fs::read_to_string(&path)
                    .map_err(|e| format!("{}: {e}", path.display()))?;
                object(&path.display().to_string(), &text)?
            }
            _ => Map::new(),
        };
        if let Some(overrides) = sources.overrides.filter(|s| !s.trim().is_empty()) {
            merged.extend(object(OVERRIDES_ENV, overrides)?);
        }
        let mut config: EncCronConfig = serde_json::from_value(Value::Object(merged))
            .map_err(|e| format!("configuration: {e}"))?;
        if let Some(user) = sources.admin_user.filter(|s| !s.trim().is_empty()) {
            config.admin_user = user.to_owned();
        }
        if let Some(pass) = sources.admin_pass.filter(|s| !s.trim().is_empty()) {
            config.admin_pass = pass.to_owned();
        }
        Ok(config)
    }

    /// [`load`](Self::load) with the overrides and credentials read from the environment.
    ///
    /// # Errors
    ///
    /// As [`load`](Self::load).
    pub fn from_env(resources_dir: Option<&Path>) -> Result<Self, String> {
        let overrides = std::env::var(OVERRIDES_ENV).ok();
        let user = std::env::var(ADMIN_USER_ENV).ok();
        let pass = std::env::var(ADMIN_PASS_ENV).ok();
        Self::load(&Sources {
            resources_dir,
            overrides: overrides.as_deref(),
            admin_user: user.as_deref(),
            admin_pass: pass.as_deref(),
        })
    }

    /// Where the ingest worker polls for `*.zip`.
    pub fn save_dir(&self) -> PathBuf {
        self.chart_temp_data.join("save")
    }

    /// Scratch space for downloads, staging and bundle assembly. Same filesystem as `save/`, so
    /// publishing is a rename.
    pub fn work_dir(&self) -> PathBuf {
        self.chart_temp_data.join("enc_cron")
    }

    /// Njord's API root, derived from `chartEditionsUrl` so one setting names the host. `None`
    /// when that URL has no `/v1/` in it.
    pub fn njord_base_url(&self) -> Option<&str> {
        self.chart_editions_url
            .find("/v1/")
            .map(|i| &self.chart_editions_url[..i])
    }
}

fn object(what: &str, text: &str) -> Result<Map<String, Value>, String> {
    match serde_json::from_str(text).map_err(|e| format!("{what}: {e}"))? {
        Value::Object(map) => Ok(map),
        _ => Err(format!("{what} is not a JSON object")),
    }
}

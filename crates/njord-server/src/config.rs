//! Server configuration: `<resources>/config/application.json` with the `CHART_SERVER_OPTS` JSON
//! object merged over it.

use std::fmt;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::Value;

/// The environment variable holding a JSON object of configuration overrides.
pub const OVERRIDES_ENV: &str = "CHART_SERVER_OPTS";

/// One region archive the export worker renders, an entry of `regionExports`.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct RegionExportConfig {
    pub name: String,
    pub description: String,
    /// WKT polygon.
    pub coverage: String,
}

/// The server configuration, deserialised from `application.json` and the overrides.
///
/// Field names are the camel-case JSON keys; fields without a default are required, and unknown
/// keys are ignored.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartsConfig {
    pub admin_key: String,
    pub admin_user: String,
    pub admin_pass: String,
    pub admin_expiration_seconds: i64,
    pub pg_connection_info: String,
    pub host: String,
    pub port: u16,
    pub console_metrics: bool,
    pub chart_temp_data: PathBuf,
    /// The web app's build output, served for every path no route answers. Relative paths are
    /// resolved against the working directory.
    pub web_static_content: PathBuf,
    pub shallow_depth: f32,
    pub safety_depth: f32,
    pub deep_depth: f32,
    pub debug_tile: bool,
    pub chart_ingest_workers: u32,
    #[serde(default = "yes")]
    pub use_tile_cache: bool,
    #[serde(default = "yes")]
    pub enable_ingestion: bool,
    #[serde(default = "yes")]
    pub enable_region_export: bool,
    #[serde(default)]
    pub region_exports: Vec<RegionExportConfig>,
}

fn yes() -> bool {
    true
}

/// Why the configuration could not be loaded.
#[derive(Debug)]
pub enum ConfigError {
    /// `application.json` could not be read.
    Read(PathBuf, std::io::Error),
    /// A document (`application.json` or the overrides) is not a JSON object.
    NotAnObject(String),
    /// A document is not JSON, or the merged object is not a valid [`ChartsConfig`].
    Parse(String, serde_json::Error),
}

impl fmt::Display for ConfigError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ConfigError::Read(path, e) => write!(f, "{}: {e}", path.display()),
            ConfigError::NotAnObject(what) => write!(f, "{what} is not a JSON object"),
            ConfigError::Parse(what, e) => write!(f, "{what}: {e}"),
        }
    }
}

impl std::error::Error for ConfigError {}

impl ChartsConfig {
    /// Reads `<resources>/config/application.json` with `overrides` (the `CHART_SERVER_OPTS` value)
    /// merged over it.
    ///
    /// The merge is shallow: an override replaces the whole top-level value, so `regionExports`
    /// is replaced, not extended.
    ///
    /// # Errors
    ///
    /// Returns [`ConfigError`] when `application.json` cannot be read, either document is not a
    /// JSON object, or the merged object is not a valid configuration.
    pub fn load(resources: &Path, overrides: Option<&str>) -> Result<Self, ConfigError> {
        let path = resources.join("config/application.json");
        let text =
            std::fs::read_to_string(&path).map_err(|e| ConfigError::Read(path.clone(), e))?;
        let mut merged = object(&path.display().to_string(), &text)?;
        if let Some(overrides) = overrides {
            merged.extend(object(OVERRIDES_ENV, overrides)?);
        }
        serde_json::from_value(Value::Object(merged))
            .map_err(|e| ConfigError::Parse("configuration".to_owned(), e))
    }

    /// Calls [`load`](Self::load) with the overrides read from [`OVERRIDES_ENV`].
    ///
    /// # Errors
    ///
    /// As [`load`](Self::load).
    pub fn from_env(resources: &Path) -> Result<Self, ConfigError> {
        Self::load(resources, std::env::var(OVERRIDES_ENV).ok().as_deref())
    }

    pub fn symbology(&self) -> njord_symbology::SymbologyConfig {
        njord_symbology::SymbologyConfig {
            shallow_depth: self.shallow_depth,
            safety_depth: self.safety_depth,
            deep_depth: self.deep_depth,
            debug_tile: self.debug_tile,
        }
    }
}

fn object(what: &str, text: &str) -> Result<serde_json::Map<String, Value>, ConfigError> {
    match serde_json::from_str(text).map_err(|e| ConfigError::Parse(what.to_owned(), e))? {
        Value::Object(map) => Ok(map),
        _ => Err(ConfigError::NotAnObject(what.to_owned())),
    }
}

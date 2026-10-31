//! Server configuration (`ChartsConfig.kt`, `Singletons.config`): `<resources>/config/application.json`
//! with the `CHART_SERVER_OPTS` JSON object merged over it.

use std::fmt;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::Value;

/// The environment variable holding a JSON object of configuration overrides.
pub const OVERRIDES_ENV: &str = "CHART_SERVER_OPTS";

/// One region archive the export worker renders (`RegionExportConfig`).
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct RegionExportConfig {
    pub name: String,
    pub description: String,
    /// WKT polygon.
    pub coverage: String,
}

/// `ChartsConfig`. Field names are the JSON keys; fields without a default are required, as in
/// Kotlin, and unknown keys are ignored (`ignoreUnknownKeys = true`).
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
    /// resolved against the working directory, as Kotlin's `File` does.
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

#[derive(Debug)]
pub enum ConfigError {
    Read(PathBuf, std::io::Error),
    /// A document (`application.json` or the overrides) is not a JSON object.
    NotAnObject(String),
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
    /// `<resources>/config/application.json` with `overrides` (the `CHART_SERVER_OPTS` value)
    /// merged over it. The merge is shallow, as Kotlin's `JsonObject(base + overrides)`: an
    /// override replaces the whole top-level value, so `regionExports` is replaced, not extended.
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

    /// [`load`](Self::load) with the overrides read from [`OVERRIDES_ENV`].
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

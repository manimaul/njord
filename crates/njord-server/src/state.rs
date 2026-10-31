//! Process-wide state shared by every handler (Kotlin's `Singletons`), built once at startup.

use std::collections::HashSet;
use std::fmt;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use axum::body::Bytes;
use njord_db::Db;
use njord_model::{Colors, IndexMap, S57Attribute, S57ExpectedInput, S57Object};
use njord_symbology::{ColorLibrary, LayerFactory};
use serde::Serialize;
use serde::de::DeserializeOwned;

use crate::config::ChartsConfig;
use crate::ingest::IngestStatus;
use crate::lock::DistributedLock;
use crate::regions::Regions;
use crate::signature::Verifier;
use crate::sprite_sheet::SpriteSheet;
use crate::tile_cache::TileCache;
use crate::tile_encoder::Symbology;

/// Everything a handler reads. Handlers take `State<Arc<AppState>>`.
pub struct AppState {
    pub config: ChartsConfig,
    /// The resources directory given on the command line.
    pub resources: PathBuf,
    /// The chart database (`pgConnectionInfo`).
    pub db: Db,
    /// Style layers, `preTileEncode` rules and the colour library (`Singletons.layerFactory`,
    /// `Singletons.colorLibrary`), plus the enumerated S-57 attributes the tile prop filter
    /// reads; shared with the tile encoder's blocking tasks.
    pub symbology: Arc<Symbology>,
    pub sprites: SpriteSheet,
    /// `<chartTempData>/tiles`.
    pub tile_cache: TileCache,
    /// `<chartTempData>/regions`, read in the server's local time zone.
    pub regions: Regions,
    pub verifier: Verifier,
    /// The `/v1/about/*` dictionaries, serialised once.
    pub about: AboutDocuments,
    /// `<chartTempData>/lock`, shared by ingest and region export across replicas.
    pub lock: Arc<DistributedLock>,
    /// `<chartTempData>/status.json`, what `/v1/ws/enc_process` streams.
    pub status: Arc<IngestStatus>,
    /// `<chartTempData>/save`: uploaded zips waiting for the ingest worker.
    pub save_dir: PathBuf,
    /// `<chartTempData>/ingest`: the worker's claim directories.
    pub ingest_dir: PathBuf,
    /// Wakes the region export worker (`RegionExportWorker.wake()`).
    pub region_wake: tokio::sync::Notify,
    /// The client `POST /v1/enc_url` downloads with.
    pub http: reqwest::Client,
}

/// `S57ObjectLibrary` and `ColorLibrary` as the about routes send them: decoded into their wire
/// types and re-encoded, as Kotlin does, so the bodies are Ktor's encoding of the resource files.
pub struct AboutDocuments {
    pub s57objects: Bytes,
    pub s57attributes: Bytes,
    pub expected_input: Bytes,
    pub colors: Bytes,
    pub colors_custom: Bytes,
}

#[derive(Debug)]
pub struct StateError(String);

impl fmt::Display for StateError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for StateError {}

impl AppState {
    /// Reads the resource files the handlers need. Kotlin loads them lazily and fails the first
    /// request that needs a missing one; the port refuses to start instead.
    ///
    /// `db` is not contacted here; [`crate::migrate`] runs the schema migration before serving.
    pub fn new(config: ChartsConfig, resources: &Path, db: Db) -> Result<Arc<Self>, StateError> {
        let colors: Colors = read(resources, "colors.json")?;
        let attributes: IndexMap<String, S57Attribute> = read(resources, "s57attributes.json")?;
        let enumerated: HashSet<String> = attributes
            .iter()
            .filter(|(_, a)| a.attribute_type == "E")
            .map(|(k, _)| k.clone())
            .collect();
        let about = AboutDocuments {
            s57objects: reencode::<IndexMap<String, S57Object>>(
                resources,
                "s57objectclasses.json",
            )?,
            s57attributes: to_bytes(&attributes),
            expected_input: reencode::<IndexMap<String, Vec<S57ExpectedInput>>>(
                resources,
                "s57expectedinput.json",
            )?,
            colors: to_bytes(&colors.library),
            colors_custom: to_bytes(&colors.custom),
        };
        let layers = LayerFactory::new(config.symbology(), ColorLibrary::new(colors));
        let temp = &config.chart_temp_data;
        let lock = Arc::new(DistributedLock::new(temp.join("lock")));
        let status = Arc::new(IngestStatus::new(temp.join("status.json"), lock.clone()));
        Ok(Arc::new(AppState {
            lock,
            status,
            save_dir: temp.join("save"),
            ingest_dir: temp.join("ingest"),
            region_wake: tokio::sync::Notify::new(),
            http: reqwest::Client::new(),
            resources: resources.to_path_buf(),
            db,
            symbology: Arc::new(Symbology { layers, enumerated }),
            sprites: SpriteSheet::new(resources),
            tile_cache: TileCache::new(temp.join("tiles")),
            regions: Regions::new(temp.join("regions"), jiff::tz::TimeZone::system()),
            verifier: Verifier {
                admin_key: config.admin_key.clone(),
                expiration_seconds: config.admin_expiration_seconds,
            },
            about,
            config,
        }))
    }

    /// `RegionExportWorker.wake()`: a conflated signal that cuts the worker's wait short.
    pub fn wake_region_worker(&self) {
        self.region_wake.notify_one();
    }

    pub fn layers(&self) -> &LayerFactory {
        &self.symbology.layers
    }

    pub fn colors(&self) -> &ColorLibrary {
        &self.layers().cx.colors
    }
}

fn read<T: DeserializeOwned>(resources: &Path, name: &str) -> Result<T, StateError> {
    let path = resources.join(name);
    let bytes = std::fs::read(&path).map_err(|e| StateError(format!("{}: {e}", path.display())))?;
    serde_json::from_slice(&bytes).map_err(|e| StateError(format!("{}: {e}", path.display())))
}

fn reencode<T: DeserializeOwned + Serialize>(
    resources: &Path,
    name: &str,
) -> Result<Bytes, StateError> {
    read::<T>(resources, name).map(|v| to_bytes(&v))
}

fn to_bytes<T: Serialize>(value: &T) -> Bytes {
    Bytes::from(serde_json::to_vec(value).expect("wire types always serialise"))
}

//! Rendering region archives (`ingest/RegionExporter.kt` `exportNext`, `exportRegion`,
//! `writeMbtilesArchive`; P-RE-1…3 `testdata/regions/schema.sql`, `metadata.json`,
//! `archive.json`).
//!
//! An archive is an MBTiles file (SQLite, written with `rusqlite`) holding the region's tiles
//! gzip-compressed in TMS row order, plus the `charts` / `chart_tiles` catalog the mobile client
//! uses to install and uninstall charts (docs/REGION_DATA_EXPORT_MOBILE.md). The tiles are the
//! same tiles `/v1/tile` serves ([`crate::tile_encoder::archive_tile`]); the `WORLD` region is
//! the base map alone over the full quad tree to z6.
//!
//! One region renders per [`export_next`] call, under the lock shared with ingest, so a render
//! never overlaps an ingest or another render anywhere in the cluster. SQLite work runs on the
//! blocking pool with the connection handed back and forth; tile rendering is async over the
//! database and the blocking pool.

use std::collections::BTreeSet;
use std::fmt;
use std::io::Write;
use std::path::Path;

use flate2::Compression;
use flate2::write::GzEncoder;
use futures_util::StreamExt;
use geo::BoundingRect;
use geo_types::Rect;
use jiff::Timestamp;
use njord_db::RegionChart;
use njord_geo::TILE_SYSTEM;
use njord_symbology::kotlin::double_to_string;
use rusqlite::{Connection, params};
use serde_json::Value;

use crate::config::RegionExportConfig;
use crate::state::AppState;
use crate::tile_encoder::{self, TileXyz};

/// The region whose archive is the base map alone.
pub const WORLD_REGION_NAME: &str = "WORLD";

/// The deepest zoom of the `WORLD` quad tree.
const WORLD_MAX_ZOOM: u32 = 6;

/// Tiles rendered between two SQLite transactions.
const TILE_INSERT_BATCH_SIZE: usize = 200;

/// Tiles of a batch rendered at once.
const RENDER_CONCURRENCY: usize = 4;

/// Bumped when the `charts`/`chart_tiles` shape changes.
const CATALOG_SCHEMA_VERSION: i32 = 1;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ExportResult {
    Rendered,
    NothingToDo,
    LockBusy,
}

#[derive(Debug)]
pub enum ExportError {
    Db(njord_db::Error),
    Sqlite(rusqlite::Error),
    Io(std::io::Error),
    Tile(tile_encoder::TileError),
    Other(String),
}

impl fmt::Display for ExportError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ExportError::Db(e) => write!(f, "{e}"),
            ExportError::Sqlite(e) => write!(f, "sqlite: {e}"),
            ExportError::Io(e) => write!(f, "{e}"),
            ExportError::Tile(e) => write!(f, "{e}"),
            ExportError::Other(e) => f.write_str(e),
        }
    }
}

impl std::error::Error for ExportError {}

impl From<njord_db::Error> for ExportError {
    fn from(e: njord_db::Error) -> Self {
        ExportError::Db(e)
    }
}

impl From<rusqlite::Error> for ExportError {
    fn from(e: rusqlite::Error) -> Self {
        ExportError::Sqlite(e)
    }
}

impl From<std::io::Error> for ExportError {
    fn from(e: std::io::Error) -> Self {
        ExportError::Io(e)
    }
}

impl From<tile_encoder::TileError> for ExportError {
    fn from(e: tile_encoder::TileError) -> Self {
        ExportError::Tile(e)
    }
}

impl From<tokio::task::JoinError> for ExportError {
    fn from(e: tokio::task::JoinError) -> Self {
        ExportError::Other(e.to_string())
    }
}

/// XYZ ordered by `(z, x, y)`, the order tiles are rendered and written in.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct TileCoord {
    pub z: u32,
    pub x: i64,
    pub y: i64,
}

/// MBTiles row order (TMS, `y = 0` at the bottom) from XYZ.
pub fn xyz_to_tms_row(z: u32, y: i64) -> i64 {
    ((1i64 << z) - 1) - y
}

/// `exportNext`: renders the first configured region that is stale, under the lock.
pub async fn export_next(state: &AppState) -> ExportResult {
    let regions = &state.config.region_exports;
    if regions.is_empty() {
        tracing::info!("no region exports configured, skipping");
        return ExportResult::NothingToDo;
    }
    let _ = std::fs::create_dir_all(&state.regions.dir);
    let mut next = None;
    for region in regions {
        if needs_rebuild(state, region).await {
            next = Some(region);
            break;
        }
    }
    let Some(region) = next else {
        return ExportResult::NothingToDo;
    };
    if !state.lock.try_acquire().await {
        return ExportResult::LockBusy;
    }
    if let Err(e) = export_region(state, region).await {
        tracing::error!("region export failed for {}: {e}", region.name);
    }
    state.lock.try_clear();
    ExportResult::Rendered
}

/// `needsRebuild`: fail-open, a database error means rebuild.
async fn needs_rebuild(state: &AppState, region: &RegionExportConfig) -> bool {
    state
        .db
        .region_needs_rebuild(&region.coverage, &region.name)
        .await
        .unwrap_or(true)
}

/// `exportRegion`: one archive, written to `<name>.tmp` and renamed into place, then the
/// checksum sidecar, the export watermark and the prune of older archives.
pub async fn export_region(
    state: &AppState,
    region: &RegionExportConfig,
) -> Result<(), ExportError> {
    tracing::info!("exporting region {}", region.name);
    if !needs_rebuild(state, region).await {
        tracing::info!("region {} is up-to-date, skipping", region.name);
        return Ok(());
    }
    let is_world = region.name == WORLD_REGION_NAME;
    let charts = if is_world {
        Vec::new()
    } else {
        let found = state.db.find_charts_in_region(&region.coverage).await?;
        if found.is_empty() {
            tracing::info!("no charts intersect region {}, skipping", region.name);
            return Ok(());
        }
        found
    };
    let stamp = state.regions.current_timestamp();
    let archive_name = format!("{}_{stamp}.mbtiles", region.name);
    let archive = state.regions.dir.join(&archive_name);
    let tmp = state.regions.dir.join(format!("{archive_name}.tmp"));
    // `njord:generated_at` must be the instant the manifest derives from the file name, so it is
    // parsed back from the stem rather than sampled again.
    let generated_at = state
        .regions
        .parse_stem(&stamp)
        .ok_or_else(|| ExportError::Other(format!("could not parse archive stem {stamp:?}")))?;
    if is_world {
        tracing::info!("writing world base map to {}", archive.display());
    } else {
        tracing::info!("writing {} chart(s) to {}", charts.len(), archive.display());
    }
    let written = write_mbtiles(state, &tmp, region, &charts, is_world, generated_at).await;
    if let Err(e) = written {
        let _ = std::fs::remove_file(&tmp);
        return Err(e);
    }
    if let Err(e) = std::fs::rename(&tmp, &archive) {
        let _ = std::fs::remove_file(&tmp);
        return Err(e.into());
    }
    tracing::info!("region {} archive created: {archive_name}", region.name);
    state.regions.write_checksum_sidecar(&archive);
    state.db.mark_region_exported(&region.name).await?;
    state.regions.prune_old_archives(&region.name);
    Ok(())
}

/// Runs `f` with the connection on the blocking pool and hands the connection back.
async fn with_conn<T: Send + 'static>(
    conn: Connection,
    f: impl FnOnce(&Connection) -> Result<T, ExportError> + Send + 'static,
) -> Result<(Connection, T), ExportError> {
    tokio::task::spawn_blocking(move || f(&conn).map(|t| (conn, t))).await?
}

async fn write_mbtiles(
    state: &AppState,
    path: &Path,
    region: &RegionExportConfig,
    charts: &[RegionChart],
    is_world: bool,
    generated_at: Timestamp,
) -> Result<(), ExportError> {
    let _ = std::fs::remove_file(path);
    let conn = Connection::open(path)?;
    let chart_rows = charts.to_vec();
    let (conn, ()) = with_conn(conn, move |conn| {
        conn.execute_batch(CREATE_TABLES)?;
        // Charts first: chart_tiles references them.
        write_charts(conn, &chart_rows)
    })
    .await?;

    let (mut conn, coords) = if is_world {
        (conn, world_tile_coordinates())
    } else {
        compile_tile_coordinates(state, conn, charts).await?
    };
    tracing::info!("region {}: {} candidate tile(s)", region.name, coords.len());

    let mut written = 0u32;
    let mut min_z = u32::MAX;
    let mut max_z = 0u32;
    let coords: Vec<TileCoord> = coords.into_iter().collect();
    for batch in coords.chunks(TILE_INSERT_BATCH_SIZE) {
        let db = &state.db;
        let symbology = &state.symbology;
        let rendered: Vec<Option<(TileCoord, Vec<u8>)>> =
            futures_util::stream::iter(batch.iter().copied())
                .map(|coord: TileCoord| {
                    let symbology = symbology.clone();
                    async move {
                        let t = TileXyz {
                            z: coord.z,
                            x: coord.x,
                            y: coord.y,
                        };
                        tile_encoder::archive_tile(db, symbology, t, is_world)
                            .await
                            .map(|bytes| bytes.map(|b| (coord, b)))
                    }
                })
                .buffered(RENDER_CONCURRENCY)
                .collect::<Vec<Result<_, _>>>()
                .await
                .into_iter()
                .collect::<Result<_, _>>()?;
        let rendered: Vec<(TileCoord, Vec<u8>)> = rendered.into_iter().flatten().collect();
        if rendered.is_empty() {
            continue;
        }
        let (returned, n) = with_conn(conn, move |conn| {
            let tx = conn.unchecked_transaction()?;
            let mut stmt = tx.prepare(INSERT_TILE)?;
            let mut count = 0u32;
            let mut lo = u32::MAX;
            let mut hi = 0u32;
            for (coord, bytes) in &rendered {
                stmt.execute(params![
                    coord.z,
                    coord.x,
                    xyz_to_tms_row(coord.z, coord.y),
                    gzip(bytes)?
                ])?;
                count += 1;
                lo = lo.min(coord.z);
                hi = hi.max(coord.z);
            }
            drop(stmt);
            tx.commit()?;
            Ok((count, lo, hi))
        })
        .await?;
        conn = returned;
        written += n.0;
        min_z = min_z.min(n.1);
        max_z = max_z.max(n.2);
    }
    let (min_z, max_z) = if written > 0 { (min_z, max_z) } else { (0, 0) };

    let metadata = metadata_rows(
        region,
        min_z,
        max_z,
        is_world,
        generated_at,
        charts.len(),
        written,
    );
    let (conn, ()) = with_conn(conn, move |conn| {
        // Candidate edges are a superset of the tiles written; drop the dangling ones.
        conn.execute_batch(PRUNE_ORPHANED_CHART_TILES)?;
        conn.execute_batch(CREATE_CHART_TILES_INDEX)?;
        let tx = conn.unchecked_transaction()?;
        let mut stmt = tx.prepare(INSERT_METADATA)?;
        for (name, value) in &metadata {
            stmt.execute(params![name, value])?;
        }
        drop(stmt);
        tx.commit()?;
        Ok(())
    })
    .await?;
    tokio::task::spawn_blocking(move || conn.close().map_err(|(_, e)| ExportError::Sqlite(e)))
        .await??;
    tracing::info!(
        "region {}: wrote {written}/{} non-empty tile(s)",
        region.name,
        coords.len()
    );
    Ok(())
}

/// `writeCharts`: the server's `charts` rows with PostGIS and JSONB flattened to text.
fn write_charts(conn: &Connection, charts: &[RegionChart]) -> Result<(), ExportError> {
    if charts.is_empty() {
        return Ok(());
    }
    let tx = conn.unchecked_transaction()?;
    let mut stmt = tx.prepare(INSERT_CHART)?;
    for chart in charts {
        let covr = match njord_db::geometry::from_wkb(&chart.covr_wkb) {
            Ok(g) => geojson::Geometry::from(&g).to_string(),
            Err(e) => {
                tracing::warn!(
                    "chart {}: could not convert covr WKB to GeoJSON: {e}",
                    chart.name
                );
                "null".to_owned()
            }
        };
        stmt.execute(params![
            chart.name,
            chart.scale,
            chart.file_name,
            chart.updated,
            chart.issued,
            chart.zoom,
            covr,
            chart.dsid_props_json,
            chart.chart_txt_json,
            chart.ingested_at,
        ])?;
    }
    drop(stmt);
    tx.commit()?;
    Ok(())
}

/// `writeChartTileEdges`.
fn write_chart_tile_edges(
    conn: &Connection,
    chart_name: &str,
    coords: &BTreeSet<TileCoord>,
) -> Result<(), ExportError> {
    if coords.is_empty() {
        return Ok(());
    }
    let tx = conn.unchecked_transaction()?;
    let mut stmt = tx.prepare(INSERT_CHART_TILE)?;
    for coord in coords {
        stmt.execute(params![
            chart_name,
            coord.z,
            coord.x,
            xyz_to_tms_row(coord.z, coord.y)
        ])?;
    }
    drop(stmt);
    tx.commit()?;
    Ok(())
}

/// `worldTileCoordinates`: the full quad tree to [`WORLD_MAX_ZOOM`].
pub fn world_tile_coordinates() -> BTreeSet<TileCoord> {
    let mut coords = BTreeSet::new();
    for z in 0..=WORLD_MAX_ZOOM {
        let n = 1i64 << z;
        for x in 0..n {
            for y in 0..n {
                coords.insert(TileCoord { z, x, y });
            }
        }
    }
    coords
}

/// `compileTileCoordinates`: for every feature of every chart, the tiles its envelope touches at
/// each zoom of `max(MINZ, 0) ..= min(MAXZ, chart.zoom)`; each chart's own set is written as
/// `chart_tiles` edges as it is compiled, and the union is the render candidate set.
async fn compile_tile_coordinates(
    state: &AppState,
    mut conn: Connection,
    charts: &[RegionChart],
) -> Result<(Connection, BTreeSet<TileCoord>), ExportError> {
    let mut all = BTreeSet::new();
    for chart in charts {
        let features = state.db.find_features_for_chart(&chart.name).await?;
        let chart_zoom = chart.zoom;
        let name = chart.name.clone();
        let (returned, coords) = tokio::task::spawn_blocking(move || {
            let mut coords = BTreeSet::new();
            for feature in &features {
                let props: Value = serde_json::from_str(&feature.props_json).unwrap_or(Value::Null);
                let int = |key: &str| match props.get(key) {
                    Some(Value::Number(n)) => n.as_i64().and_then(|i| i32::try_from(i).ok()),
                    Some(Value::String(s)) => s.parse().ok(),
                    _ => None,
                };
                let min_z = int("MINZ").unwrap_or(0).max(0);
                let max_z = int("MAXZ").unwrap_or(32).min(chart_zoom);
                if min_z > max_z {
                    continue;
                }
                let Some(bbox) = njord_db::geometry::from_wkb(&feature.geom_wkb)
                    .ok()
                    .and_then(|g| g.bounding_rect())
                else {
                    continue;
                };
                tiles_covering(&bbox, min_z as u32, max_z as u32, &mut coords);
            }
            write_chart_tile_edges(&conn, &name, &coords).map(|()| (conn, coords))
        })
        .await??;
        conn = returned;
        all.extend(coords);
    }
    Ok((conn, all))
}

/// The tiles of zooms `min_z..=max_z` whose extent intersects `bbox`, clamped to the map.
fn tiles_covering(bbox: &Rect, min_z: u32, max_z: u32, out: &mut BTreeSet<TileCoord>) {
    let (min, max) = (bbox.min(), bbox.max());
    for z in min_z..=max_z {
        let max_tile = (1i64 << z) - 1;
        let tl = TILE_SYSTEM.lat_lng_to_tile_xy(min.x, max.y, z);
        let br = TILE_SYSTEM.lat_lng_to_tile_xy(max.x, min.y, z);
        let clamp = |v: f64| (v.trunc() as i64).clamp(0, max_tile);
        for x in clamp(tl.x)..=clamp(br.x) {
            for y in clamp(tl.y)..=clamp(br.y) {
                out.insert(TileCoord { z, x, y });
            }
        }
    }
}

/// `writeMetadata`'s rows, in insertion order (`regions/metadata.json`).
fn metadata_rows(
    region: &RegionExportConfig,
    min_z: u32,
    max_z: u32,
    is_world: bool,
    generated_at: Timestamp,
    chart_count: usize,
    tile_count: u32,
) -> Vec<(String, String)> {
    let envelope = if is_world {
        Some(Rect::new((-180.0, -90.0), (180.0, 90.0)))
    } else {
        region
            .coverage
            .parse::<wkt::Wkt<f64>>()
            .ok()
            .and_then(|w| geo_types::Geometry::try_from(w).ok())
            .and_then(|g| g.bounding_rect())
    };
    let mut rows = vec![
        ("name", region.name.clone()),
        ("description", region.description.clone()),
        ("format", "pbf".to_owned()),
        ("type", "baselayer".to_owned()),
        ("version", "1".to_owned()),
        ("minzoom", min_z.to_string()),
        ("maxzoom", max_z.to_string()),
    ];
    if let Some(e) = envelope {
        let d = double_to_string;
        rows.push((
            "bounds",
            format!(
                "{},{},{},{}",
                d(e.min().x),
                d(e.min().y),
                d(e.max().x),
                d(e.max().y)
            ),
        ));
        rows.push((
            "center",
            format!(
                "{},{},{}",
                d((e.min().x + e.max().x) / 2.0),
                d((e.min().y + e.max().y) / 2.0),
                (min_z + max_z) / 2
            ),
        ));
    }
    rows.extend([
        ("njord:schema_version", CATALOG_SCHEMA_VERSION.to_string()),
        ("njord:region", region.name.clone()),
        ("njord:chart_count", chart_count.to_string()),
        ("njord:tile_count", tile_count.to_string()),
        (
            "njord:generated_at",
            njord_model::instant::format(generated_at),
        ),
    ]);
    rows.into_iter().map(|(k, v)| (k.to_owned(), v)).collect()
}

/// `gzipCompress`: RFC 1952 gzip at zlib's default level.
fn gzip(data: &[u8]) -> Result<Vec<u8>, ExportError> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(data)?;
    Ok(encoder.finish()?)
}

const CREATE_TABLES: &str = "
CREATE TABLE IF NOT EXISTS metadata (
    name  TEXT NOT NULL,
    value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS tiles (
    zoom_level  INTEGER NOT NULL,
    tile_column INTEGER NOT NULL,
    tile_row    INTEGER NOT NULL,
    tile_data   BLOB    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS tile_index
    ON tiles (zoom_level, tile_column, tile_row);
CREATE TABLE IF NOT EXISTS charts (
    name        TEXT    PRIMARY KEY,
    scale       INTEGER NOT NULL,
    file_name   TEXT    NOT NULL,
    updated     TEXT    NOT NULL,
    issued      TEXT    NOT NULL,
    zoom        INTEGER NOT NULL,
    covr        TEXT    NOT NULL,
    dsid_props  TEXT    NOT NULL,
    chart_txt   TEXT    NOT NULL,
    ingested_at TEXT    NOT NULL
);
CREATE TABLE IF NOT EXISTS chart_tiles (
    chart_name  TEXT    NOT NULL REFERENCES charts(name) ON DELETE CASCADE,
    zoom_level  INTEGER NOT NULL,
    tile_column INTEGER NOT NULL,
    tile_row    INTEGER NOT NULL,
    PRIMARY KEY (chart_name, zoom_level, tile_column, tile_row)
) WITHOUT ROWID;
";

const CREATE_CHART_TILES_INDEX: &str = "
CREATE INDEX IF NOT EXISTS chart_tiles_tile_idx
    ON chart_tiles (zoom_level, tile_column, tile_row);
";

const PRUNE_ORPHANED_CHART_TILES: &str = "
DELETE FROM chart_tiles WHERE NOT EXISTS (
    SELECT 1 FROM tiles t
    WHERE t.zoom_level  = chart_tiles.zoom_level
      AND t.tile_column = chart_tiles.tile_column
      AND t.tile_row    = chart_tiles.tile_row);
";

const INSERT_METADATA: &str = "INSERT INTO metadata (name, value) VALUES (?, ?);";

const INSERT_TILE: &str = "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?);";

const INSERT_CHART: &str = "INSERT OR REPLACE INTO charts
    (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt, ingested_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

const INSERT_CHART_TILE: &str = "INSERT OR IGNORE INTO chart_tiles (chart_name, zoom_level, tile_column, tile_row) VALUES (?, ?, ?, ?);";

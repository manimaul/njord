//! Tile assembly (`geo/TileEncoder.kt` `addCharts`, `renderBaseMap`, `encode`, `infoJson`; P-TE-5
//! `testdata/geo/tiles/`), over `njord-geo` geometry and MVT encoding, `njord-symbology` rules
//! and `njord-db` queries.
//!
//! For tile `z`/`x`/`y`, with `include` starting as the tile grown by 15 pixels:
//! 1. The charts whose coverage intersects the (unexpanded) tile, smallest scale first, and the
//!    features of those with `0 ≤ zoom ≤ z`, clipped by the database to the box of `include`.
//! 2. Per chart: if it is eligible and `include` is not yet empty, each feature gets the
//!    symbology props (`preTileEncode`), the prop filter and `NAME` = the chart, and its geometry
//!    intersected with `include` (LIGHTS are not intersected, so a light keeps its full sector
//!    geometry); a valid non-empty result is encoded. Then `include` loses the chart's coverage,
//!    so a smaller-scale chart drawn later never paints over a larger-scale one (TE-1). Every
//!    intersecting chart, eligible or not, contributes its coverage boundary to layer `PLY`.
//! 3. Where `include` is still not empty, the Natural Earth base features of the zoom's scale
//!    inside it (not in `info` mode).
//!
//! `info` mode returns the chart features instead, as WGS-84 GeoJSON before the pixel transform.
//! Geometry runs on the blocking pool; the database queries in between are async.
//!
//! Differences from Kotlin come from `geo` standing in for GEOS (RUST_PORT_PLAN.md Phase 2
//! notes): boolean operations snap to a grid (about 1e-9 of the extent), rings may start at
//! another vertex, and repair keeps only polygons.

use std::collections::HashSet;
use std::fmt;
use std::sync::Arc;

use geo::Validation;
use geo_types::{Geometry, MultiPolygon};
use njord_db::{ChartFeature as DbFeature, ChartInfo, Db};
use njord_geo::geometry::{boundary, difference, intersection, is_empty, to_tile_pixels};
use njord_geo::{TILE_SYSTEM, VectorTileEncoder, filter_props, find_base_map_scale};
use njord_model::{ChartFeatureInfo, IndexMap, JsonObject};
use njord_symbology::{ChartFeature, LayerFactory};
use serde_json::Value;

/// What the encoder reads besides the database.
pub struct Symbology {
    pub layers: LayerFactory,
    /// S-57 attribute acronyms of type `E` (enumerated), for the prop filter.
    pub enumerated: HashSet<String>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct TileXyz {
    pub z: u32,
    pub x: i64,
    pub y: i64,
}

#[derive(Debug)]
pub enum TileError {
    Db(njord_db::Error),
    /// A feature prop held a JSON object (Kotlin throws from `filteredProps`).
    Props(njord_geo::UnexpectedObject),
    Join(tokio::task::JoinError),
}

impl fmt::Display for TileError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            TileError::Db(e) => write!(f, "{e}"),
            TileError::Props(e) => write!(f, "{e}"),
            TileError::Join(e) => write!(f, "{e}"),
        }
    }
}

impl std::error::Error for TileError {}

impl From<njord_db::Error> for TileError {
    fn from(e: njord_db::Error) -> Self {
        TileError::Db(e)
    }
}

impl From<tokio::task::JoinError> for TileError {
    fn from(e: tokio::task::JoinError) -> Self {
        TileError::Join(e)
    }
}

/// The encoded tile (`addCharts(false)`, base map, optional debug overlay, `encode()`).
pub async fn tile(
    db: &Db,
    symbology: Arc<Symbology>,
    t: TileXyz,
    debug: bool,
) -> Result<Vec<u8>, TileError> {
    let encoder = assemble(db, symbology, t, debug, false).await?;
    Ok(tokio::task::spawn_blocking(move || encoder.encode()).await?)
}

/// A region archive tile (`RegionExporter.renderAndWriteTiles`): the live tile for a chart
/// region, the base map alone (`addBaseMapOnly`) for the `WORLD` region; `None` when the tile
/// has no content beyond the `PLY` coverage outline (`hasContent`), which is what keeps an
/// archive sparse.
pub async fn archive_tile(
    db: &Db,
    symbology: Arc<Symbology>,
    t: TileXyz,
    base_map_only: bool,
) -> Result<Option<Vec<u8>>, TileError> {
    let encoder = assemble(db, symbology, t, false, base_map_only).await?;
    Ok(tokio::task::spawn_blocking(move || {
        encoder.has_features(&["PLY"]).then(|| encoder.encode())
    })
    .await?)
}

/// Charts (unless `base_map_only`), then the base map where no chart covers the tile, then the
/// debug overlay.
async fn assemble(
    db: &Db,
    symbology: Arc<Symbology>,
    t: TileXyz,
    debug: bool,
    base_map_only: bool,
) -> Result<VectorTileEncoder, TileError> {
    let (mut encoder, include) = if base_map_only {
        let include = MultiPolygon(vec![
            TILE_SYSTEM.create_tile_clip_polygon(t.x, t.y, t.z, 15),
        ]);
        (VectorTileEncoder::new(), include)
    } else {
        let charts = chart_inputs(db, t).await?;
        let sym = symbology.clone();
        let (encoder, include, _) =
            tokio::task::spawn_blocking(move || add_charts(&sym, t, charts, false)).await??;
        (encoder, include)
    };
    let base = if is_empty(&Geometry::MultiPolygon(include.clone())) {
        Vec::new()
    } else {
        let include = Geometry::MultiPolygon(include);
        db.find_base_features(find_base_map_scale(t.z), &include)
            .await?
    };
    tokio::task::spawn_blocking(move || {
        render_base_map(&symbology, t, &mut encoder, base)?;
        if debug {
            let mut props = JsonObject::new();
            props.insert("DMSG".into(), format!("{}, {}, {}", t.z, t.x, t.y).into());
            encoder.add_debug_envelope("DEBUG", &props);
        }
        Ok::<_, TileError>(encoder)
    })
    .await?
}

/// `?info=true`: the chart features the tile would draw, in WGS-84, without base map or `PLY`.
pub async fn info(
    db: &Db,
    symbology: Arc<Symbology>,
    t: TileXyz,
) -> Result<Vec<ChartFeatureInfo>, TileError> {
    let charts = chart_inputs(db, t).await?;
    let (_, _, infos) =
        tokio::task::spawn_blocking(move || add_charts(&symbology, t, charts, true)).await??;
    Ok(infos)
}

struct ChartInputs {
    charts: Vec<ChartInfo>,
    features: IndexMap<String, Vec<DbFeature>>,
}

async fn chart_inputs(db: &Db, t: TileXyz) -> Result<ChartInputs, TileError> {
    let envelope = Geometry::Polygon(TILE_SYSTEM.create_tile_clip_polygon(t.x, t.y, t.z, 0));
    let include = Geometry::Polygon(TILE_SYSTEM.create_tile_clip_polygon(t.x, t.y, t.z, 15));
    let charts = db.find_chart_info(&envelope).await?;
    let eligible: Vec<String> = charts
        .iter()
        .filter(|c| eligible(c, t.z))
        .map(|c| c.name.clone())
        .collect();
    let features = db
        .find_all_chart_features_4326(&include, &eligible, t.z as i32)
        .await?;
    Ok(ChartInputs { charts, features })
}

/// `chart.zoom in 0..z`.
fn eligible(chart: &ChartInfo, z: u32) -> bool {
    chart.zoom >= 0 && chart.zoom as i64 <= z as i64
}

type Assembled = (VectorTileEncoder, MultiPolygon, Vec<ChartFeatureInfo>);

fn add_charts(
    symbology: &Symbology,
    t: TileXyz,
    mut inputs: ChartInputs,
    info: bool,
) -> Result<Assembled, TileError> {
    let mut encoder = VectorTileEncoder::new();
    let mut include = MultiPolygon(vec![
        TILE_SYSTEM.create_tile_clip_polygon(t.x, t.y, t.z, 15),
    ]);
    let mut infos = Vec::new();
    for chart in &inputs.charts {
        if !is_empty(&Geometry::MultiPolygon(include.clone())) && eligible(chart, t.z) {
            for feature in inputs.features.swap_remove(&chart.name).unwrap_or_default() {
                let mut props = symbolized(symbology, &feature)?;
                props.insert("NAME".into(), Value::String(chart.name.clone()));
                let Some(geometry) = feature.geometry else {
                    continue;
                };
                let clipped = if feature.layer == "LIGHTS" {
                    geometry
                } else {
                    intersection(&geometry, &include)
                };
                if !clipped.is_valid() || is_empty(&clipped) {
                    continue;
                }
                if info {
                    infos.push(ChartFeatureInfo {
                        layer: feature.layer.clone(),
                        props: props.clone(),
                        geom: Some(geojson::Geometry::from(&clipped)),
                    });
                }
                let pixels = to_tile_pixels(&clipped, t.x, t.y, t.z, &TILE_SYSTEM);
                encoder.add_feature(&feature.layer, &props, &pixels);
            }
            if let Some(covr) = multi_polygon(&chart.covr)
                && let Some(rest) = difference(&include, &covr)
            {
                include = rest;
            }
        }
        if let Some(edge) = boundary(&chart.covr) {
            let pixels = to_tile_pixels(&edge, t.x, t.y, t.z, &TILE_SYSTEM);
            encoder.add_feature("PLY", &JsonObject::new(), &pixels);
        }
    }
    Ok((encoder, include, infos))
}

fn render_base_map(
    symbology: &Symbology,
    t: TileXyz,
    encoder: &mut VectorTileEncoder,
    features: Vec<DbFeature>,
) -> Result<(), TileError> {
    for feature in features {
        let props = symbolized(symbology, &feature)?;
        let Some(geometry) = feature.geometry else {
            continue;
        };
        if geometry.is_valid() && !is_empty(&geometry) {
            let pixels = to_tile_pixels(&geometry, t.x, t.y, t.z, &TILE_SYSTEM);
            encoder.add_feature(&feature.layer, &props, &pixels);
        }
    }
    Ok(())
}

/// `layerFactory.preTileEncode(feature).props.filtered()`.
fn symbolized(symbology: &Symbology, feature: &DbFeature) -> Result<JsonObject, TileError> {
    let mut f = ChartFeature {
        layer: feature.layer.clone(),
        geometry_type: None,
        props: feature.props.clone(),
        associated_layer_names: feature.associated_layer_names.clone(),
    };
    symbology.layers.pre_tile_encode(&mut f);
    filter_props(&mut f.props, |key| symbology.enumerated.contains(key))
        .map_err(TileError::Props)?;
    Ok(f.props)
}

fn multi_polygon(g: &Geometry) -> Option<MultiPolygon> {
    match g {
        Geometry::Polygon(p) => Some(MultiPolygon(vec![p.clone()])),
        Geometry::MultiPolygon(mp) => Some(mp.clone()),
        _ => None,
    }
}

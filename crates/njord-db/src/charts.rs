//! `ChartDao.kt`: chart rows, the catalog and editions (P-DB-5), the tile path's feature queries
//! (P-DB-3, `db/feature_queries.json`) with the TOPMAR association (P-DB-4), and chart insert /
//! delete (P-DB-2, decision D5).

use std::collections::HashMap;

use geo_types::Geometry;
use indexmap::IndexMap;
use njord_model::{Chart, ChartCatalog, ChartEditions, ChartInsert, ChartItem, JsonObject};
use serde_json::Value;
use sqlx::Row;
use sqlx::postgres::{PgConnection, PgRow};

use crate::geometry::{from_wkb, to_wkb};
use crate::{Db, Result};

/// `ChartDao.PAGE_SIZE`: charts per catalog page.
pub const PAGE_SIZE: usize = 100;

/// A feature as the tile path reads it (Kotlin `ChartFeature`): the geometry is already clipped
/// by the query, and `None` when PostGIS returned no geometry or bytes that are not WKB (Kotlin's
/// `OgrGeometry.fromWkb4326` returning null).
#[derive(Clone, Debug, PartialEq)]
pub struct ChartFeature {
    pub layer: String,
    pub props: JsonObject,
    pub geometry: Option<Geometry>,
    /// For a TOPMAR: the distinct layers, sorted, of the features whose `lnam_refs` hold its
    /// `LNAM` (its platform). Empty for every other layer.
    pub associated_layer_names: Vec<String>,
}

/// A chart whose coverage intersects a tile (Kotlin `ChartInfo`), for `addCharts`.
#[derive(Clone, Debug, PartialEq)]
pub struct ChartInfo {
    pub name: String,
    pub scale: i32,
    pub zoom: i32,
    pub covr: Geometry,
}

const CHART_COLUMNS: &str = "name, scale, file_name, updated, issued, zoom, \
    st_asgeojson(covr), st_asbinary(covr), dsid_props, chart_txt";

/// A row of [`CHART_COLUMNS`]. `covr` is PostGIS GeoJSON (9 decimal places, as Kotlin serves
/// it); `bounds` is the envelope of the exact coverage, `[west, south, east, north]`.
fn chart_from_row(row: &PgRow, layers: Vec<String>, feature_count: i32) -> Result<Chart> {
    let covr_json: String = row.try_get(6)?;
    let geometry: geojson::Geometry = serde_json::from_str(&covr_json)?;
    let covr_wkb: Vec<u8> = row.try_get(7)?;
    let bounds = njord_geo::geometry::envelope(&from_wkb(&covr_wkb)?)
        .map(|r| vec![r.min().x, r.min().y, r.max().x, r.max().y]);
    let dsid_props: Value = row.try_get(8)?;
    let chart_txt: Value = row.try_get(9)?;
    Ok(Chart {
        name: row.try_get(0)?,
        scale: row.try_get(1)?,
        file_name: row.try_get(2)?,
        updated: row.try_get(3)?,
        issued: row.try_get(4)?,
        zoom: row.try_get(5)?,
        covr: geojson::Feature {
            geometry: Some(geometry),
            properties: Some(JsonObject::new()),
            ..Default::default()
        },
        bounds,
        layers,
        dsid_props: serde_json::from_value(dsid_props)?,
        chart_txt: serde_json::from_value(chart_txt)?,
        feature_count,
    })
}

fn chart_feature(row: &PgRow) -> Result<ChartFeature> {
    let wkb: Option<Vec<u8>> = row.try_get(0)?;
    let props: Value = row.try_get(1)?;
    Ok(ChartFeature {
        geometry: wkb.and_then(|w| from_wkb(&w).ok()),
        props: serde_json::from_value(props)?,
        layer: row.try_get(2)?,
        associated_layer_names: Vec::new(),
    })
}

/// Kotlin's `(props["LNAM"] as? JsonPrimitive)?.content`: a string's text, any other scalar's
/// JSON form, nothing for an array or object.
pub(crate) fn lnam(props: &JsonObject) -> Option<String> {
    match props.get("LNAM")? {
        Value::String(s) => Some(s.clone()),
        Value::Array(_) | Value::Object(_) => None,
        other => Some(other.to_string()),
    }
}

/// `topmarAssocByLnam`: for the TOPMARs among `features`, LNAM → the distinct (sorted) layers
/// of the features referencing it through `lnam_refs`, in one GIN scan. Empty, without a query,
/// when there is no TOPMAR.
async fn topmar_assoc<'a>(
    conn: &mut PgConnection,
    features: impl Iterator<Item = &'a ChartFeature>,
) -> Result<HashMap<String, Vec<String>>> {
    let mut lnams: Vec<String> = features
        .filter(|f| f.layer == "TOPMAR")
        .filter_map(|f| lnam(&f.props))
        .collect();
    lnams.sort();
    lnams.dedup();
    let mut by_lnam = HashMap::new();
    if lnams.is_empty() {
        return Ok(by_lnam);
    }
    let rows = sqlx::query(
        "WITH input_lnams AS (SELECT unnest($1::varchar[]) AS lnam)
         SELECT il.lnam::text, array_agg(DISTINCT f.layer)::text[]
         FROM input_lnams il
         JOIN features f ON f.lnam_refs @> ARRAY[il.lnam]
         GROUP BY il.lnam;",
    )
    .bind(&lnams)
    .fetch_all(&mut *conn)
    .await?;
    for row in rows {
        let lnam: String = row.try_get(0)?;
        let layers: Vec<String> = row.try_get(1)?;
        by_lnam.insert(lnam, layers);
    }
    Ok(by_lnam)
}

/// `withAssoc`: a TOPMAR's platform layers from [`topmar_assoc`]; other layers are untouched.
fn with_assoc(feature: &mut ChartFeature, by_lnam: &HashMap<String, Vec<String>>) {
    if feature.layer == "TOPMAR"
        && let Some(layers) = lnam(&feature.props).and_then(|l| by_lnam.get(&l))
    {
        feature.associated_layer_names = layers.clone();
    }
}

/// `ChartDao.delete`: the chart's features, then the chart; whether a chart row was deleted.
async fn delete_chart(conn: &mut PgConnection, name: &str) -> Result<bool> {
    sqlx::query("DELETE FROM features WHERE chart_name = $1;")
        .bind(name)
        .execute(&mut *conn)
        .await?;
    let deleted = sqlx::query("DELETE FROM charts WHERE name = $1;")
        .bind(name)
        .execute(&mut *conn)
        .await?
        .rows_affected();
    Ok(deleted > 0)
}

impl Db {
    /// `findAsync` (`GET /v1/chart?name=`): the chart with its layers (sorted; Kotlin's `SELECT
    /// DISTINCT` left the order to the plan) and feature count; `None` for an unknown name.
    pub async fn find_chart(&self, name: &str) -> Result<Option<Chart>> {
        let mut conn = self.conn().await?;
        let layers: Vec<String> = sqlx::query_scalar(
            "SELECT DISTINCT layer FROM features WHERE chart_name = $1 ORDER BY layer;",
        )
        .bind(name)
        .fetch_all(&mut *conn)
        .await?;
        let count = feature_count(&mut conn, name).await?;
        let row = sqlx::query(sqlx::AssertSqlSafe(format!(
            "SELECT {CHART_COLUMNS} FROM charts WHERE name = $1;"
        )))
        .bind(name)
        .fetch_optional(&mut *conn)
        .await?;
        row.map(|r| chart_from_row(&r, layers, count)).transpose()
    }

    /// `findInfoAsync`: the charts whose coverage intersects `envelope` (the tile envelope),
    /// smallest scale first (ties by name; Kotlin left them to the plan).
    pub async fn find_chart_info(&self, envelope: &Geometry) -> Result<Vec<ChartInfo>> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "SELECT name, scale, zoom, st_asbinary(covr)
             FROM charts
             WHERE st_intersects(st_geomfromwkb($1, 4326), covr)
             ORDER BY scale ASC, name;",
        )
        .bind(to_wkb(envelope)?)
        .fetch_all(&mut *conn)
        .await?;
        rows.iter()
            .map(|row| {
                let covr: Vec<u8> = row.try_get(3)?;
                Ok(ChartInfo {
                    name: row.try_get(0)?,
                    scale: row.try_get(1)?,
                    zoom: row.try_get(2)?,
                    covr: from_wkb(&covr)?,
                })
            })
            .collect()
    }

    /// `findChartFeaturesAsync4326`: the features of one chart intersecting `include` with
    /// `z_min <= zoom <= z_max`, clipped to the box of `include` when it is a single ring
    /// (`ST_ClipByBox2D`) and intersected with it otherwise. Rows in id order.
    pub async fn find_chart_features_4326(
        &self,
        include: &Geometry,
        chart_name: &str,
        zoom: i32,
    ) -> Result<Vec<ChartFeature>> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "WITH include AS (VALUES (st_geomfromwkb($1, 4326)))
             SELECT st_asbinary(
                 CASE WHEN ST_NRings((table include)) = 1
                      THEN ST_ClipByBox2D(f.geom, (table include)::box2d)
                      ELSE ST_Intersection(f.geom, (table include))
                 END
             ), f.props, f.layer
             FROM features f
             WHERE f.chart_name = $2
               AND $3 >= f.z_min AND $3 <= f.z_max
               AND st_intersects(f.geom, (table include))
             ORDER BY f.id;",
        )
        .bind(to_wkb(include)?)
        .bind(chart_name)
        .bind(zoom)
        .fetch_all(&mut *conn)
        .await?;
        let mut features = rows.iter().map(chart_feature).collect::<Result<Vec<_>>>()?;
        let by_lnam = topmar_assoc(&mut conn, features.iter()).await?;
        features.iter_mut().for_each(|f| with_assoc(f, &by_lnam));
        Ok(features)
    }

    /// `findAllChartFeaturesAsync4326` (`addCharts`): every feature of `chart_names`
    /// intersecting `tile` with `z_min <= zoom <= z_max`, clipped to the box of `tile`, grouped
    /// by chart name in name order, rows in id order (Kotlin ordered by name only). An empty
    /// `chart_names` is an empty map without a query.
    pub async fn find_all_chart_features_4326(
        &self,
        tile: &Geometry,
        chart_names: &[String],
        zoom: i32,
    ) -> Result<IndexMap<String, Vec<ChartFeature>>> {
        let mut result: IndexMap<String, Vec<ChartFeature>> = IndexMap::new();
        if chart_names.is_empty() {
            return Ok(result);
        }
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "WITH tile AS (VALUES (st_geomfromwkb($1, 4326)))
             SELECT st_asbinary(ST_ClipByBox2D(f.geom, (table tile)::box2d)),
                    f.props, f.layer, f.chart_name
             FROM features f
             WHERE f.chart_name = ANY($2)
               AND $3 >= f.z_min AND $3 <= f.z_max
               AND st_intersects(f.geom, (table tile))
             ORDER BY f.chart_name, f.id;",
        )
        .bind(to_wkb(tile)?)
        .bind(chart_names)
        .bind(zoom)
        .fetch_all(&mut *conn)
        .await?;
        for row in &rows {
            let chart: String = row.try_get(3)?;
            result.entry(chart).or_default().push(chart_feature(row)?);
        }
        let by_lnam = topmar_assoc(&mut conn, result.values().flatten()).await?;
        result
            .values_mut()
            .flatten()
            .for_each(|f| with_assoc(f, &by_lnam));
        Ok(result)
    }

    /// `listAsync` (`GET /v1/chart_catalog`): one page of names `>= next_page_name` (from the
    /// start when `None`), at most [`PAGE_SIZE`]; the next name after the page, if any, is the
    /// cursor of the following page. `total_chart_count` counts the whole table.
    pub async fn list_charts(&self, next_page_name: Option<&str>) -> Result<ChartCatalog> {
        let mut conn = self.conn().await?;
        let total: i64 = sqlx::query_scalar("SELECT COUNT(name) FROM charts;")
            .fetch_one(&mut *conn)
            .await?;
        let mut names: Vec<String> =
            sqlx::query_scalar("SELECT name FROM charts WHERE name >= $1 ORDER BY name LIMIT $2;")
                .bind(next_page_name.unwrap_or(""))
                .bind(PAGE_SIZE as i64 + 1)
                .fetch_all(&mut *conn)
                .await?;
        let next_name = (names.len() > PAGE_SIZE).then(|| names.remove(PAGE_SIZE));
        Ok(ChartCatalog {
            total_chart_count: total as i32,
            next_name,
            page: names.into_iter().map(|name| ChartItem { name }).collect(),
        })
    }

    /// `editionsAsync` (`GET /v1/chart_editions`): `"<DSID_UPDN>:<updated>:<issued>"` per chart,
    /// in name order (Kotlin: table order). A chart with any of the parts missing or blank is
    /// left out. `DSID_UPDN` is read with `->>`, so a number reads like a string.
    pub async fn chart_editions(&self) -> Result<ChartEditions> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "SELECT name, dsid_props->>'DSID_UPDN', updated, issued FROM charts ORDER BY name;",
        )
        .fetch_all(&mut *conn)
        .await?;
        let mut editions = IndexMap::new();
        for row in rows {
            let name: String = row.try_get(0)?;
            let updn: Option<String> = row.try_get(1)?;
            let updated: String = row.try_get(2)?;
            let issued: String = row.try_get(3)?;
            let blank = |s: &str| s.trim().is_empty();
            if let Some(updn) = updn.filter(|u| !blank(u))
                && !blank(&name)
                && !blank(&updated)
                && !blank(&issued)
            {
                editions.insert(name, format!("{updn}:{updated}:{issued}"));
            }
        }
        Ok(ChartEditions { editions })
    }

    /// `insertAsync`: writes the chart row and returns it (no layers, zero features). With
    /// `overwrite`, the chart of the same name and its features are deleted first.
    ///
    /// Decision D5: delete and insert are one transaction, so a failing insert (e.g. a `covr`
    /// without geometry, which violates `NOT NULL`) leaves the previous chart and its features in
    /// place. Kotlin deleted outside the insert's transaction and lost them.
    pub async fn insert_chart(&self, insert: &ChartInsert, overwrite: bool) -> Result<Chart> {
        let mut tx = self.begin().await?;
        if overwrite {
            delete_chart(&mut tx, &insert.name).await?;
        }
        let covr = insert
            .covr
            .geometry
            .as_ref()
            .map(serde_json::to_string)
            .transpose()?;
        let row = sqlx::query(sqlx::AssertSqlSafe(format!(
            "INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt)
             VALUES ($1, $2, $3, $4, $5, $6, st_setsrid(st_geomfromgeojson($7), 4326), $8, $9)
             RETURNING {CHART_COLUMNS};"
        )))
        .bind(&insert.name)
        .bind(insert.scale)
        .bind(&insert.file_name)
        .bind(&insert.updated)
        .bind(&insert.issued)
        .bind(insert.zoom)
        .bind(covr)
        .bind(sqlx::types::Json(&insert.dsid_props))
        .bind(sqlx::types::Json(&insert.chart_txt))
        .fetch_one(&mut *tx)
        .await?;
        let chart = chart_from_row(&row, Vec::new(), 0)?;
        tx.commit().await?;
        Ok(chart)
    }

    /// `deleteByNameAsync`: deletes the chart named `name` (`DSID_DSNM`) and its features, in one
    /// transaction; `false` when no chart had that name.
    pub async fn delete_chart(&self, name: &str) -> Result<bool> {
        let mut tx = self.begin().await?;
        let deleted = delete_chart(&mut tx, name).await?;
        tx.commit().await?;
        Ok(deleted)
    }

    /// `FeatureDao.featureCount`: the features stored for a chart.
    pub async fn feature_count(&self, chart_name: &str) -> Result<i32> {
        let mut conn = self.conn().await?;
        feature_count(&mut conn, chart_name).await
    }
}

pub(crate) async fn feature_count(conn: &mut PgConnection, chart_name: &str) -> Result<i32> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(id) FROM features WHERE chart_name = $1;")
        .bind(chart_name)
        .fetch_one(&mut *conn)
        .await?;
    Ok(count as i32)
}

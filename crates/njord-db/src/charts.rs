//! Chart rows: lookup, the catalog and editions, the tile path's feature queries, and insert and
//! delete.
//!
//! The tile feature queries ([`Db::find_chart_features_4326`],
//! [`Db::find_all_chart_features_4326`]) also attach each TOPMAR's platform layers, see
//! [`ChartFeature::associated_layer_names`]. Fixtures: `db/feature_queries.json`,
//! `db/lnam_refs.json`, `db/catalog_paging.json` and `db/overwrite.json`.

use std::collections::HashMap;

use geo_types::Geometry;
use indexmap::IndexMap;
use njord_model::{Chart, ChartCatalog, ChartEditions, ChartInsert, ChartItem, JsonObject};
use serde_json::Value;
use sqlx::Row;
use sqlx::postgres::{PgConnection, PgRow};

use crate::geometry::{from_wkb, to_wkb};
use crate::{Db, Result};

/// Charts per catalog page.
pub const PAGE_SIZE: usize = 100;

/// A feature as the tile path reads it.
#[derive(Clone, Debug, PartialEq)]
pub struct ChartFeature {
    /// The S-57 object class, e.g. `BOYLAT`.
    pub layer: String,
    /// The S-57 attributes and symbology props.
    pub props: JsonObject,
    /// The geometry, already clipped by the query; `None` when PostGIS returned no geometry or
    /// bytes that are not WKB.
    pub geometry: Option<Geometry>,
    /// For a TOPMAR: the distinct layers, sorted, of the features whose `lnam_refs` hold its
    /// `LNAM` (its platform). Empty for every other layer.
    pub associated_layer_names: Vec<String>,
}

/// A chart whose coverage intersects a tile, from [`Db::find_chart_info`].
#[derive(Clone, Debug, PartialEq)]
pub struct ChartInfo {
    /// The chart name (`DSID_DSNM`).
    pub name: String,
    /// The compilation scale denominator.
    pub scale: i32,
    /// The zoom level the chart is drawn from.
    pub zoom: i32,
    /// The chart coverage polygon.
    pub covr: Geometry,
}

const CHART_COLUMNS: &str = "name, scale, file_name, updated, issued, zoom, \
    st_asgeojson(covr), st_asbinary(covr), dsid_props, chart_txt";

/// A row of [`CHART_COLUMNS`].
///
/// `covr` is PostGIS GeoJSON with 9 decimal places, the precision the API serves and the goldens
/// record; `bounds` is the envelope of the exact coverage, `[west, south, east, north]`.
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

/// The `LNAM` prop as text: a string's text, any other scalar's JSON form, nothing for an array
/// or object.
pub(crate) fn lnam(props: &JsonObject) -> Option<String> {
    match props.get("LNAM")? {
        Value::String(s) => Some(s.clone()),
        Value::Array(_) | Value::Object(_) => None,
        other => Some(other.to_string()),
    }
}

/// For the TOPMARs among `features`, LNAM → the distinct (sorted) layers
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

/// Sets a TOPMAR's platform layers from [`topmar_assoc`]; other layers are untouched.
fn with_assoc(feature: &mut ChartFeature, by_lnam: &HashMap<String, Vec<String>>) {
    if feature.layer == "TOPMAR"
        && let Some(layers) = lnam(&feature.props).and_then(|l| by_lnam.get(&l))
    {
        feature.associated_layer_names = layers.clone();
    }
}

/// Deletes the chart's features, then the chart: the number of features deleted when a chart
/// row was deleted, `None` when none had that name.
async fn delete_chart(conn: &mut PgConnection, name: &str) -> Result<Option<i64>> {
    let features = sqlx::query("DELETE FROM features WHERE chart_name = $1;")
        .bind(name)
        .execute(&mut *conn)
        .await?
        .rows_affected();
    let deleted = sqlx::query("DELETE FROM charts WHERE name = $1;")
        .bind(name)
        .execute(&mut *conn)
        .await?
        .rows_affected();
    Ok((deleted > 0).then_some(features as i64))
}

impl Db {
    /// The chart named `name` (`GET /v1/chart?name=`), or `None` for an unknown name.
    ///
    /// The chart carries its layers, sorted, and its feature count.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails,
    /// and [`Error::Geometry`](crate::Error::Geometry) or [`Error::Json`](crate::Error::Json) when a
    /// row does not decode.
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

    /// The charts whose coverage intersects `envelope` (the tile envelope).
    ///
    /// Smallest scale first, ties by name.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails,
    /// and [`Error::Geometry`](crate::Error::Geometry) or [`Error::Json`](crate::Error::Json) when a
    /// row does not decode.
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

    /// The features of one chart intersecting `include` and drawn at `zoom`.
    ///
    /// A feature is drawn at `zoom` when `z_min <= zoom <= z_max`. Geometries are clipped to the
    /// box of `include` when it is a single ring (`ST_ClipByBox2D`) and intersected with it
    /// otherwise. Rows in id order.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails,
    /// and [`Error::Geometry`](crate::Error::Geometry) or [`Error::Json`](crate::Error::Json) when
    /// `include` cannot be encoded or a row does not decode.
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

    /// Every feature of `chart_names` intersecting `tile` and drawn at `zoom`, by chart.
    ///
    /// Geometries are clipped to the box of `tile`. Charts are in name order and rows in id
    /// order. An empty `chart_names` is an empty map without a query.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails,
    /// and [`Error::Geometry`](crate::Error::Geometry) or [`Error::Json`](crate::Error::Json) when
    /// `tile` cannot be encoded or a row does not decode.
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

    /// One page of the chart catalog (`GET /v1/chart_catalog`).
    ///
    /// The page holds at most [`PAGE_SIZE`] names `>= next_page_name` (from the start when
    /// `None`); the next name after the page, if any, is the cursor of the following page.
    /// `total_chart_count` counts the whole table.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
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

    /// The edition key `"<DSID_UPDN>:<updated>:<issued>"` of every chart (`GET /v1/chart_editions`).
    ///
    /// Charts are in name order. A chart with any of the parts missing or blank is left out.
    /// `DSID_UPDN` is read with `->>`, so a number reads like a string.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
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

    /// Writes the chart row and returns it, with no layers and zero features.
    ///
    /// With `overwrite`, the chart of the same name and its features are deleted first. Delete
    /// and insert are one transaction, so a failing insert (e.g. a `covr` without geometry, which
    /// violates `NOT NULL`) leaves the previous chart and its features in place.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails (including a duplicate name
    /// without `overwrite`), and [`Error::Json`](crate::Error::Json) or
    /// [`Error::Geometry`](crate::Error::Geometry) when the row does not encode or decode.
    pub async fn insert_chart(&self, insert: &ChartInsert, overwrite: bool) -> Result<Chart> {
        self.insert_chart_tracked(insert, overwrite)
            .await
            .map(|(chart, _)| chart)
    }

    /// [`insert_chart`](Db::insert_chart), also returning the feature count of the chart it
    /// replaced: `None` when no chart of that name existed, which is what decides whether the
    /// change is recorded as `new` or `update` and gives its `before`.
    ///
    /// # Errors
    ///
    /// As [`insert_chart`](Db::insert_chart).
    pub async fn insert_chart_tracked(
        &self,
        insert: &ChartInsert,
        overwrite: bool,
    ) -> Result<(Chart, Option<i64>)> {
        let mut tx = self.begin().await?;
        let before = if overwrite {
            delete_chart(&mut tx, &insert.name).await?
        } else {
            None
        };
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
        Ok((chart, before))
    }

    /// Deletes the chart named `name` (`DSID_DSNM`) and its features, in one transaction, and
    /// with `record` writes the `delete` change event of `cause` in it.
    ///
    /// Returns the number of features deleted, or `None` when no chart had that name, in which
    /// case nothing is recorded either.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
    pub async fn delete_chart(
        &self,
        name: &str,
        record: Option<njord_model::ChangeCause>,
    ) -> Result<Option<i64>> {
        let mut tx = self.begin().await?;
        let deleted = delete_chart(&mut tx, name).await?;
        if let (Some(before), Some(cause)) = (deleted, record) {
            let event = crate::NewChangeEvent::deleted(name, before, cause);
            crate::record_change_event(&mut tx, &event).await?;
        }
        tx.commit().await?;
        Ok(deleted)
    }

    /// The number of features stored for a chart.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
    pub async fn feature_count(&self, chart_name: &str) -> Result<i32> {
        let mut conn = self.conn().await?;
        feature_count(&mut conn, chart_name).await
    }

    /// The feature count of the chart named `chart_name`, or `None` when no chart has that
    /// name: the `before` of a change event when the change has not happened yet.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
    pub async fn chart_feature_count(&self, chart_name: &str) -> Result<Option<i64>> {
        let mut conn = self.conn().await?;
        Ok(sqlx::query_scalar(
            "SELECT (SELECT count(*) FROM features WHERE chart_name = $1)
             WHERE EXISTS (SELECT 1 FROM charts WHERE name = $1);",
        )
        .bind(chart_name)
        .fetch_optional(&mut *conn)
        .await?)
    }
}

pub(crate) async fn feature_count(conn: &mut PgConnection, chart_name: &str) -> Result<i32> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(id) FROM features WHERE chart_name = $1;")
        .bind(chart_name)
        .fetch_one(&mut *conn)
        .await?;
    Ok(count as i32)
}

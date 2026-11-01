//! The GeoJSON routes: `GET /v1/geojson?chart_name=&layer_name=` and `POST /v1/geojson`.

use geojson::{Feature, FeatureCollection, GeoJson};
use njord_model::{FeatureInsert, JsonObject};
use serde_json::Value;
use sqlx::postgres::PgConnection;

use crate::features::{lnam_refs, max_z, min_z};
use crate::{Db, Result};

impl Db {
    /// The features of one layer of one chart as GeoJSON, in id order.
    ///
    /// Each feature is PostgreSQL's `row_to_json` of `(id, layer, type, geometry, properties)`,
    /// so it carries a numeric `id` and a foreign member `layer`
    /// (golden `api_04_charts/geojson_existing_layer`).
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails,
    /// and [`Error::Json`](crate::Error::Json) when a row is not a GeoJSON feature.
    pub async fn geojson_layer(&self, chart_name: &str, layer: &str) -> Result<FeatureCollection> {
        let mut conn = self.conn().await?;
        let rows: Vec<Value> = sqlx::query_scalar(
            "SELECT row_to_json(f)::jsonb
             FROM (
                 SELECT id AS id,
                        layer AS layer,
                        'Feature' AS type,
                        ST_AsGeoJSON(geom)::json AS geometry,
                        props AS properties
                 FROM features
                 WHERE layer = $1 AND chart_name = $2
                 ORDER BY id
             ) f;",
        )
        .bind(layer)
        .bind(chart_name)
        .fetch_all(&mut *conn)
        .await?;
        let features = rows
            .into_iter()
            .map(serde_json::from_value)
            .collect::<Result<Vec<Feature>, _>>()?;
        Ok(FeatureCollection {
            bbox: None,
            features,
            foreign_members: None,
        })
    }

    /// Inserts `insert.geo` into `insert.chart` as `insert.layer_name` and returns the rows
    /// written.
    ///
    /// A bare geometry gets `{}` props and zooms 0–32; a feature its props with
    /// `MINZ`/`MAXZ`/`LNAM_REFS` mapped as ingest maps them, and is skipped when it has no
    /// geometry (`C_AGGR`, `C_ASSO`); a collection inserts each feature. Geometries are made 2D
    /// and valid. Each insert stands alone: one that fails is logged and counts 0.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired.
    /// Failed inserts are not errors.
    pub async fn geojson_insert(&self, insert: &FeatureInsert) -> Result<u64> {
        let mut conn = self.conn().await?;
        let chart = insert.chart.name.as_str();
        let layer = insert.layer_name.as_str();
        let mut count = 0;
        match &insert.geo {
            GeoJson::Geometry(g) => {
                count += insert_geojson(&mut conn, chart, layer, g, &JsonObject::new()).await;
            }
            GeoJson::Feature(f) => {
                count += insert_geojson_feature(&mut conn, chart, layer, f).await;
            }
            GeoJson::FeatureCollection(fc) => {
                for f in &fc.features {
                    count += insert_geojson_feature(&mut conn, chart, layer, f).await;
                }
            }
        }
        Ok(count)
    }
}

async fn insert_geojson_feature(
    conn: &mut PgConnection,
    chart: &str,
    layer: &str,
    feature: &Feature,
) -> u64 {
    let Some(geometry) = &feature.geometry else {
        return 0;
    };
    let props = feature.properties.clone().unwrap_or_default();
    insert_geojson(conn, chart, layer, geometry, &props).await
}

async fn insert_geojson(
    conn: &mut PgConnection,
    chart: &str,
    layer: &str,
    geometry: &geojson::Geometry,
    props: &JsonObject,
) -> u64 {
    let json = match serde_json::to_string(geometry) {
        Ok(j) => j,
        Err(e) => {
            tracing::error!("error encoding geometry layer {layer} chart {chart}: {e}");
            return 0;
        }
    };
    let inserted = sqlx::query(
        "INSERT INTO features (layer, geom, props, chart_name, z_min, z_max, lnam_refs)
         VALUES ($1, ST_MakeValid(ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON($2), 4326))),
                 $3, $4, $5, $6, $7);",
    )
    .bind(layer)
    .bind(&json)
    .bind(sqlx::types::Json(props))
    .bind(chart)
    .bind(min_z(props))
    .bind(max_z(props))
    .bind(lnam_refs(props))
    .execute(&mut *conn)
    .await;
    match inserted {
        Ok(r) => r.rows_affected(),
        Err(e) => {
            tracing::error!("error inserting json {json} layer {layer} chart {chart}: {e}");
            0
        }
    }
}

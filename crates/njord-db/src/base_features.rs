//! The Natural Earth base map (`base_features`).
//!
//! Written by the shapefile ingest and read by the tile encoder below chart coverage.

use geo_types::Geometry;
use njord_model::JsonObject;
use serde_json::Value;
use sqlx::Row;

use crate::charts::ChartFeature;
use crate::geometry::{from_wkb, to_wkb};
use crate::{Db, Result};

impl Db {
    /// Removes one shapefile's features; whether any existed.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
    pub async fn delete_base_features(&self, name: &str, scale: i32) -> Result<bool> {
        let mut conn = self.conn().await?;
        let deleted = sqlx::query("DELETE FROM base_features WHERE name = $1 AND scale = $2;")
            .bind(name)
            .bind(scale)
            .execute(&mut *conn)
            .await?
            .rows_affected();
        Ok(deleted > 0)
    }

    /// Inserts one base feature, geometry forced 2D in EPSG:4326, and returns the rows written.
    ///
    /// `name` is the shapefile (`ne_10m_land.shp`), `scale` its Natural Earth scale, `layer` the
    /// S-57 layer it maps to.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails.
    pub async fn insert_base_feature(
        &self,
        wkb: &[u8],
        props: &JsonObject,
        name: &str,
        scale: i32,
        layer: &str,
    ) -> Result<u64> {
        let mut conn = self.conn().await?;
        Ok(sqlx::query(
            "INSERT INTO base_features (geom, props, name, scale, layer)
             VALUES (ST_Force2D(ST_SetSRID(ST_GeomFromWKB($1), 4326)), $2, $3, $4, $5);",
        )
        .bind(wkb)
        .bind(sqlx::types::Json(props))
        .bind(name)
        .bind(scale)
        .bind(layer)
        .execute(&mut *conn)
        .await?
        .rows_affected())
    }

    /// The base features of `scale` intersecting `include`, intersected with it, in id order.
    ///
    /// # Errors
    ///
    /// [`Error::Connection`](crate::Error::Connection) when no connection could be acquired,
    /// [`Error::Sql`](crate::Error::Sql) when a statement fails,
    /// and [`Error::Geometry`](crate::Error::Geometry) or [`Error::Json`](crate::Error::Json) when
    /// `include` cannot be encoded or a row does not decode.
    pub async fn find_base_features(
        &self,
        scale: i32,
        include: &Geometry,
    ) -> Result<Vec<ChartFeature>> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "WITH include AS (VALUES (st_geomfromwkb($1, 4326)))
             SELECT st_asbinary(st_intersection(geom, (table include))), props, layer
             FROM base_features
             WHERE scale = $2
               AND st_intersects(geom, (table include))
             ORDER BY id;",
        )
        .bind(to_wkb(include)?)
        .bind(scale)
        .fetch_all(&mut *conn)
        .await?;
        rows.iter()
            .map(|row| {
                let wkb: Option<Vec<u8>> = row.try_get(0)?;
                let props: Value = row.try_get(1)?;
                Ok(ChartFeature {
                    geometry: wkb.and_then(|w| from_wkb(&w).ok()),
                    props: serde_json::from_value(props)?,
                    layer: row.try_get(2)?,
                    associated_layer_names: Vec::new(),
                })
            })
            .collect()
    }
}

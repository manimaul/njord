//! `FeatureDao.kt`: the feature routes' queries (`/v1/feature/layer`, `/v1/feature/lnam`), the
//! TOPMAR association lookup (P-DB-4, `db/lnam_refs.json`), and the feature insert ingest uses
//! (`insertFeatureSync`, pinned after ingest by P-IN-5 `s57/*/db_state.json`).
//!
//! Ingest installs a chart as [`Db::insert_chart`] (overwrite) followed by one
//! [`FeatureInstall`] transaction for its features (decision D5: not retried, counted only once
//! committed); [`Db::install_chart`] does both for a chart whose features are already read.

use njord_model::{
    Chart, ChartInsert, FeatureRecord, JsonObject, LayerQueryResult, LayerQueryResultPage,
};
use serde_json::Value;
use sqlx::postgres::{PgConnection, Postgres};
use sqlx::{Row, Transaction};

use crate::charts::lnam;
use crate::{Db, Error, Result};

/// `MINZ` when it reads as an `Int` (Kotlin `jsonPrimitive.intOrNull`: an integer number, or a
/// string holding one), else 0.
pub fn min_z(props: &JsonObject) -> i32 {
    int_prop(props, "MINZ").unwrap_or(0)
}

/// `MAXZ` read like [`min_z`], else 32 (the column default, 22, is never used).
pub fn max_z(props: &JsonObject) -> i32 {
    int_prop(props, "MAXZ").unwrap_or(32)
}

fn int_prop(props: &JsonObject, key: &str) -> Option<i32> {
    match props.get(key)? {
        Value::Number(n) => n.as_i64().and_then(|i| i32::try_from(i).ok()),
        Value::String(s) => s.parse().ok(),
        _ => None,
    }
}

/// `lnamRefs()`: the `LNAM_REFS` array mirrored into `features.lnam_refs`, which the GIN index
/// behind the TOPMAR association covers. Strings as their text, other scalars as their JSON
/// form (`null` included, as Kotlin's `JsonPrimitive.content`), nested arrays and objects
/// dropped. Empty (stored as `{}`, not NULL) when `LNAM_REFS` is missing or not an array.
pub fn lnam_refs(props: &JsonObject) -> Vec<String> {
    match props.get("LNAM_REFS") {
        Some(Value::Array(items)) => items
            .iter()
            .filter_map(|v| match v {
                Value::String(s) => Some(s.clone()),
                Value::Array(_) | Value::Object(_) => None,
                other => Some(other.to_string()),
            })
            .collect(),
        _ => Vec::new(),
    }
}

/// `insertFeatureSync`: one feature of `chart_name`, geometry forced 2D in EPSG:4326,
/// `z_min`/`z_max` from [`min_z`]/[`max_z`], `lnam_refs` from [`lnam_refs`]. The rows written.
async fn insert_feature(
    conn: &mut PgConnection,
    layer: &str,
    chart_name: &str,
    wkb: &[u8],
    props: &JsonObject,
) -> Result<u64> {
    Ok(sqlx::query(
        "INSERT INTO features (layer, geom, props, chart_name, z_min, z_max, lnam_refs)
         VALUES ($1, st_force2d(st_setsrid(st_geomfromwkb($2), 4326)), $3, $4, $5, $6, $7);",
    )
    .bind(layer)
    .bind(wkb)
    .bind(sqlx::types::Json(props))
    .bind(chart_name)
    .bind(min_z(props))
    .bind(max_z(props))
    .bind(lnam_refs(props))
    .execute(&mut *conn)
    .await?
    .rows_affected())
}

/// The feature insert of one chart, in one transaction (decision D5 (b)). Dropping it without
/// [`commit`](FeatureInstall::commit) rolls every insert back, which is how an aborted ingest
/// leaves nothing behind.
pub struct FeatureInstall {
    tx: Transaction<'static, Postgres>,
    chart_name: String,
    inserted: u64,
    failed: bool,
}

impl FeatureInstall {
    pub fn chart_name(&self) -> &str {
        &self.chart_name
    }

    /// Features inserted so far; nothing is visible to others until [`commit`](Self::commit).
    pub fn inserted(&self) -> u64 {
        self.inserted
    }

    /// Inserts one feature: `wkb` is the repaired geometry (`geometry.make_valid()?.wkb()`),
    /// `props` its GDAL-mapped properties. A failure is final: PostgreSQL aborts the
    /// transaction, and [`commit`](Self::commit) then rolls back and fails.
    pub async fn insert(&mut self, layer: &str, props: &JsonObject, wkb: &[u8]) -> Result<u64> {
        let inserted = insert_feature(&mut self.tx, layer, &self.chart_name, wkb, props).await;
        let n = inserted.inspect_err(|_| self.failed = true)?;
        self.inserted += n;
        Ok(n)
    }

    /// Commits and returns the number of features installed: the count ingest reports. After a
    /// failed [`insert`](Self::insert) nothing is committed and this is [`Error::Aborted`]
    /// (PostgreSQL answers `COMMIT` of an aborted transaction with a rollback, not an error).
    pub async fn commit(self) -> Result<u64> {
        if self.failed {
            self.tx.rollback().await?;
            return Err(Error::Aborted(self.chart_name));
        }
        self.tx.commit().await?;
        Ok(self.inserted)
    }

    pub async fn rollback(self) -> Result<()> {
        Ok(self.tx.rollback().await?)
    }
}

/// A chart written by [`Db::install_chart`].
#[derive(Clone, Debug, PartialEq)]
pub struct InstalledChart {
    pub chart: Chart,
    pub feature_count: u64,
}

impl Db {
    /// Starts the feature install of `chart_name`, a chart row already written by
    /// [`insert_chart`](Db::insert_chart).
    pub async fn begin_feature_install(&self, chart_name: &str) -> Result<FeatureInstall> {
        Ok(FeatureInstall {
            tx: self.begin().await?,
            chart_name: chart_name.to_owned(),
            inserted: 0,
            failed: false,
        })
    }

    /// `ChartIngest` for one read cell: [`insert_chart`](Db::insert_chart) with overwrite, then
    /// every `(layer, props, wkb)` in one [`FeatureInstall`]. Features without a geometry are
    /// the caller's to skip (Kotlin inserts only `geometry?.makeValid()?.wkb`).
    pub async fn install_chart<'a>(
        &self,
        insert: &ChartInsert,
        features: impl IntoIterator<Item = (&'a str, &'a JsonObject, &'a [u8])>,
    ) -> Result<InstalledChart> {
        let chart = self.insert_chart(insert, true).await?;
        let mut install = self.begin_feature_install(&chart.name).await?;
        for (layer, props, wkb) in features {
            install.insert(layer, props, wkb).await?;
        }
        let feature_count = install.commit().await?;
        Ok(InstalledChart {
            chart,
            feature_count,
        })
    }

    /// `insertFeature`: one feature on its own (autocommitted).
    pub async fn insert_feature(
        &self,
        layer: &str,
        chart_name: &str,
        wkb: &[u8],
        props: &JsonObject,
    ) -> Result<u64> {
        let mut conn = self.conn().await?;
        insert_feature(&mut conn, layer, chart_name, wkb, props).await
    }

    /// `findLayerPositionsPage` (`GET /v1/feature/layer/{layer}?start_id=`): the next 5 features
    /// of `layer` with `id > start_id`, in id order, at their centroid, with their chart's zoom.
    /// `last_id` is the largest id served (0 for an empty page). A TOPMAR gets `_PTFM`
    /// (`FLOATING` when anything references it, else `RIGID`) and `_ASOC` (the referencing
    /// layers) appended to its props, as `TopmarData.addTo` does.
    pub async fn find_layer_positions_page(
        &self,
        layer: &str,
        start_id: i64,
    ) -> Result<LayerQueryResultPage> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "SELECT features.id, ST_X(ST_Centroid(geom)), ST_Y(ST_Centroid(geom)),
                    ST_GeometryType(geom), props, charts.name, charts.zoom
             FROM features JOIN charts ON features.chart_name = charts.name
             WHERE features.id > $1 AND features.layer = $2
             ORDER BY features.id LIMIT 5;",
        )
        .bind(start_id)
        .bind(layer)
        .fetch_all(&mut *conn)
        .await?;
        let mut last_id = 0;
        let mut items = Vec::with_capacity(rows.len());
        for row in rows {
            let id: i64 = row.try_get(0)?;
            last_id = last_id.max(id);
            let x: Option<f64> = row.try_get(1)?;
            let y: Option<f64> = row.try_get(2)?;
            let geom_type: String = row.try_get(3)?;
            let props: Value = row.try_get(4)?;
            let mut props: JsonObject = serde_json::from_value(props)?;
            if layer == "TOPMAR" {
                let assoc = match lnam(&props) {
                    Some(l) => associated_layer_names(&mut conn, &l).await?,
                    None => Vec::new(),
                };
                let platform = if assoc.is_empty() {
                    "RIGID"
                } else {
                    "FLOATING"
                };
                props.insert("_PTFM".into(), platform.into());
                props.insert("_ASOC".into(), assoc.into());
            }
            let zoom: i32 = row.try_get(6)?;
            items.push(LayerQueryResult {
                id,
                lat: y.unwrap_or(0.0),
                lng: x.unwrap_or(0.0),
                zoom: zoom as f32,
                props,
                chart_name: row.try_get(5)?,
                geom_type: geom_type.replace("ST_", ""),
            });
        }
        Ok(LayerQueryResultPage { last_id, items })
    }

    /// `findAssociatedLayerNames`: the distinct layers (sorted) of the features whose
    /// `lnam_refs` contain `lnam`, through the GIN index (`@>`, not `= ANY`).
    pub async fn find_associated_layer_names(&self, lnam: &str) -> Result<Vec<String>> {
        let mut conn = self.conn().await?;
        associated_layer_names(&mut conn, lnam).await
    }

    /// `findFeature` (`GET /v1/feature/lnam/{lnam}`): the feature with `props.LNAM = lnam`, the
    /// lowest id when several charts carry it (Kotlin took whichever came first).
    ///
    /// Pinned Kotlin quirk: `zoom_max` holds `z_min` and `zoom_min` holds `z_max` (the golden
    /// `api_04_charts/feature_lnam` serves `"zoomMin": 32, "zoomMax": 13`).
    pub async fn find_feature(&self, lnam: &str) -> Result<Option<FeatureRecord>> {
        let mut conn = self.conn().await?;
        let row = sqlx::query(
            "SELECT id, layer, ST_AsGeoJSON(geom), props, chart_name, z_min, z_max
             FROM features WHERE props->>'LNAM' = $1 ORDER BY id LIMIT 1;",
        )
        .bind(lnam)
        .fetch_optional(&mut *conn)
        .await?;
        let Some(row) = row else { return Ok(None) };
        let geom: String = row.try_get(2)?;
        let props: Value = row.try_get(3)?;
        Ok(Some(FeatureRecord {
            id: row.try_get(0)?,
            layer: row.try_get(1)?,
            geom: serde_json::from_str(&geom)?,
            props: serde_json::from_value(props)?,
            chart_name: row.try_get(4)?,
            zoom_max: row.try_get(5)?,
            zoom_min: row.try_get(6)?,
        }))
    }
}

async fn associated_layer_names(conn: &mut PgConnection, lnam: &str) -> Result<Vec<String>> {
    Ok(sqlx::query_scalar(
        "SELECT DISTINCT layer FROM features WHERE lnam_refs @> ARRAY[$1::varchar] ORDER BY layer;",
    )
    .bind(lnam)
    .fetch_all(&mut *conn)
    .await?)
}

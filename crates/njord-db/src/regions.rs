//! `RegionDao.kt`: what the region exporter reads, and the export flags (P-DB-6) the export
//! worker and the chart delete route rely on. Region coverages are WKT, as configured.
//!
//! Rows for the exporter keep geometry as WKB and JSON as text: they are copied into the
//! region's SQLite archive unchanged.

use sqlx::Row;

use crate::{Db, Result};

/// A chart row for a region archive.
#[derive(Clone, Debug, PartialEq)]
pub struct RegionChart {
    pub name: String,
    pub scale: i32,
    pub file_name: String,
    pub updated: String,
    pub issued: String,
    pub zoom: i32,
    /// `st_asbinary(covr)`.
    pub covr_wkb: Vec<u8>,
    /// `dsid_props::text`.
    pub dsid_props_json: String,
    /// `chart_txt::text`.
    pub chart_txt_json: String,
    /// `ingested_at::text`, PostgreSQL's rendering in the session time zone.
    pub ingested_at: String,
}

/// A feature row for a region archive.
#[derive(Clone, Debug, PartialEq)]
pub struct RegionFeature {
    pub id: i64,
    pub layer: String,
    /// `st_asbinary(geom)`.
    pub geom_wkb: Vec<u8>,
    /// `props::text`.
    pub props_json: String,
    pub chart_name: String,
    /// `props.LNAM_REFS` as text (empty when missing).
    pub lnam_refs: Vec<String>,
}

impl Db {
    /// `findChartsInRegion`: the charts whose coverage intersects `coverage_wkt`, by name.
    pub async fn find_charts_in_region(&self, coverage_wkt: &str) -> Result<Vec<RegionChart>> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "SELECT name, scale, file_name, updated, issued, zoom, st_asbinary(covr),
                    dsid_props::text, chart_txt::text, ingested_at::text
             FROM charts
             WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326))
             ORDER BY name;",
        )
        .bind(coverage_wkt)
        .fetch_all(&mut *conn)
        .await?;
        rows.iter()
            .map(|row| {
                Ok(RegionChart {
                    name: row.try_get(0)?,
                    scale: row.try_get(1)?,
                    file_name: row.try_get(2)?,
                    updated: row.try_get(3)?,
                    issued: row.try_get(4)?,
                    zoom: row.try_get(5)?,
                    covr_wkb: row.try_get(6)?,
                    dsid_props_json: row.try_get(7)?,
                    chart_txt_json: row.try_get(8)?,
                    ingested_at: row.try_get(9)?,
                })
            })
            .collect()
    }

    /// `findFeaturesForChart`: every feature of one chart, in id order.
    pub async fn find_features_for_chart(&self, chart_name: &str) -> Result<Vec<RegionFeature>> {
        let mut conn = self.conn().await?;
        let rows = sqlx::query(
            "SELECT id, layer, st_asbinary(geom), props::text, chart_name,
                    ARRAY(SELECT jsonb_array_elements_text(props->'LNAM_REFS')) AS lnam_refs
             FROM features
             WHERE chart_name = $1
             ORDER BY id;",
        )
        .bind(chart_name)
        .fetch_all(&mut *conn)
        .await?;
        rows.iter()
            .map(|row| {
                Ok(RegionFeature {
                    id: row.try_get(0)?,
                    layer: row.try_get(1)?,
                    geom_wkb: row.try_get(2)?,
                    props_json: row.try_get(3)?,
                    chart_name: row.try_get(4)?,
                    lnam_refs: row.try_get(5)?,
                })
            })
            .collect()
    }

    /// `latestChartUpdateInRegion`: the greatest `updated` (`DSID_UADT`) among the charts
    /// intersecting the region; `None` when there are none (Kotlin read SQL NULL as `""`).
    pub async fn latest_chart_update_in_region(
        &self,
        coverage_wkt: &str,
    ) -> Result<Option<String>> {
        let mut conn = self.conn().await?;
        Ok(sqlx::query_scalar(
            "SELECT MAX(updated) FROM charts WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326));",
        )
        .bind(coverage_wkt)
        .fetch_one(&mut *conn)
        .await?)
    }

    /// `regionNeedsRebuild`: whether a chart intersecting the region was written
    /// (`ingested_at`) after the region's last recorded export, or ever when it has none. A
    /// deleted chart is not noticed; see [`regions_containing_chart`](Db::regions_containing_chart).
    pub async fn region_needs_rebuild(
        &self,
        coverage_wkt: &str,
        region_name: &str,
    ) -> Result<bool> {
        let mut conn = self.conn().await?;
        Ok(sqlx::query_scalar(
            "SELECT EXISTS (
                 SELECT 1 FROM charts
                 WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326))
                   AND ingested_at > COALESCE(
                       (SELECT exported_at FROM region_export_state WHERE region_name = $2),
                       '-infinity'::timestamptz
                   )
             );",
        )
        .bind(coverage_wkt)
        .bind(region_name)
        .fetch_one(&mut *conn)
        .await?)
    }

    /// `regionsContainingChart`: the names of the `(name, coverage_wkt)` regions whose coverage
    /// the chart intersects (a shared boundary counts), in input order; empty for an unknown
    /// chart. Ask before deleting a chart, then clear those regions' export state.
    pub async fn regions_containing_chart(
        &self,
        chart_name: &str,
        regions: &[(String, String)],
    ) -> Result<Vec<String>> {
        let mut conn = self.conn().await?;
        let mut containing = Vec::new();
        for (name, coverage_wkt) in regions {
            let hit: bool = sqlx::query_scalar(
                "SELECT EXISTS (
                     SELECT 1 FROM charts
                     WHERE name = $1 AND ST_Intersects(covr, ST_GeomFromText($2, 4326))
                 );",
            )
            .bind(chart_name)
            .bind(coverage_wkt)
            .fetch_one(&mut *conn)
            .await?;
            if hit {
                containing.push(name.clone());
            }
        }
        Ok(containing)
    }

    /// `clearRegionExportState`: forgets the region's last export so it reads stale again;
    /// whether it had one.
    pub async fn clear_region_export_state(&self, region_name: &str) -> Result<bool> {
        let mut conn = self.conn().await?;
        let deleted = sqlx::query("DELETE FROM region_export_state WHERE region_name = $1;")
            .bind(region_name)
            .execute(&mut *conn)
            .await?
            .rows_affected();
        Ok(deleted > 0)
    }

    /// `markRegionExported`: records an export of the region at `now()`.
    pub async fn mark_region_exported(&self, region_name: &str) -> Result<()> {
        let mut conn = self.conn().await?;
        sqlx::query(
            "INSERT INTO region_export_state (region_name, exported_at) VALUES ($1, now())
             ON CONFLICT (region_name) DO UPDATE SET exported_at = EXCLUDED.exported_at;",
        )
        .bind(region_name)
        .execute(&mut *conn)
        .await?;
        Ok(())
    }
}

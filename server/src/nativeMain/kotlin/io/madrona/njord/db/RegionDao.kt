package io.madrona.njord.db

import DataSource
import io.madrona.njord.Singletons

/**
 * A single chart row returned from the region query.
 */
data class RegionChart(
    val name: String,
    val scale: Int,
    val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val covrWkb: ByteArray,     // WKB of the coverage polygon
    val dsidPropsJson: String,  // raw JSONB string
    val chartTxtJson: String,   // raw JSONB string
    val ingestedAt: String,     // ISO-8601, when this row was written to our DB
)

/**
 * A single feature row returned from the region query.
 */
data class RegionFeature(
    val id: Long,
    val layer: String,
    val geomWkb: ByteArray,   // WKB of the geometry
    val propsJson: String,    // raw JSONB string
    val chartName: String,
    val lnamRefs: List<String>, // may be empty
)

class RegionDao(
    ds: DataSource = Singletons.ds,
) : Dao(ds) {

    /**
     * Returns all charts whose coverage polygon intersects the given region [coverageWkt].
     * Uses PostGIS ST_Intersects against the charts.covr geometry.
     */
    suspend fun findChartsInRegion(coverageWkt: String): List<RegionChart>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            SELECT
                name,
                scale,
                file_name,
                updated,
                issued,
                zoom,
                st_asbinary(covr),
                dsid_props::text,
                chart_txt::text,
                ingested_at::text
            FROM charts
            WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326))
            ORDER BY name;
            """.trimIndent()
        ).apply {
            setString(1, coverageWkt)
        }.executeQuery().use { rs ->
            val result = mutableListOf<RegionChart>()
            while (rs.next()) {
                result.add(
                    RegionChart(
                        name = rs.getString(1),
                        scale = rs.getInt(2),
                        fileName = rs.getString(3),
                        updated = rs.getString(4),
                        issued = rs.getString(5),
                        zoom = rs.getInt(6),
                        covrWkb = rs.getBytes(7),
                        dsidPropsJson = rs.getString(8),
                        chartTxtJson = rs.getString(9),
                        ingestedAt = rs.getString(10),
                    )
                )
            }
            result
        }
    }

    /**
     * Returns all features for the chart named [chartName], excluding base features.
     * Processes one chart at a time to bound memory usage.
     */
    suspend fun findFeaturesForChart(chartName: String): List<RegionFeature>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            SELECT
                id,
                layer,
                st_asbinary(geom),
                props::text,
                chart_name,
                ARRAY(SELECT jsonb_array_elements_text(props->'LNAM_REFS')) AS lnam_refs
            FROM features
            WHERE chart_name = $1
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setString(1, chartName)
        }.executeQuery().use { rs ->
            val result = mutableListOf<RegionFeature>()
            while (rs.next()) {
                val lnamRefs: List<String> = rs.getArray(6).filterNotNull()
                result.add(
                    RegionFeature(
                        id = rs.getLong(1),
                        layer = rs.getString(2),
                        geomWkb = rs.getBytes(3),
                        propsJson = rs.getString(4),
                        chartName = rs.getString(5),
                        lnamRefs = lnamRefs,
                    )
                )
            }
            result
        }
    }

    /**
     * Returns the most recent updatedAt timestamp among charts intersecting the region,
     * used to detect whether a region rebuild is needed.
     */
    suspend fun latestChartUpdateInRegion(coverageWkt: String): String? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            SELECT MAX(updated)
            FROM charts
            WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326));
            """.trimIndent()
        ).apply {
            setString(1, coverageWkt)
        }.executeQuery().use { rs ->
            if (rs.next()) rs.getString(1) else null
        }
    }

    /**
     * True if any chart intersecting the region was written to the DB (`ingested_at`) more
     * recently than the region's last recorded export. Unlike [latestChartUpdateInRegion], this
     * compares against a real DB-assigned insert timestamp rather than the S-57-authored edition
     * date, so a freshly re-ingested chart is always detected regardless of its DSID_UADT value.
     */
    suspend fun regionNeedsRebuild(coverageWkt: String, regionName: String): Boolean? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1 FROM charts
                WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326))
                  AND ingested_at > COALESCE(
                      (SELECT exported_at FROM region_export_state WHERE region_name = $2),
                      '-infinity'::timestamptz
                  )
            );
            """.trimIndent()
        ).apply {
            setString(1, coverageWkt)
            setString(2, regionName)
        }.executeQuery().use { rs ->
            if (rs.next()) rs.getBoolean(1) else false
        }
    }

    /**
     * Which of [regions] currently hold the chart named [chartName], as `name to coverageWkt`.
     *
     * Call this *before* deleting a chart: [regionNeedsRebuild] only notices charts ingested since
     * the last export, so a deletion is invisible to it and the region archive would keep shipping
     * the withdrawn chart until something else in that region happened to be re-ingested. The
     * caller clears the export state of whatever comes back.
     */
    suspend fun regionsContainingChart(
        chartName: String,
        regions: List<Pair<String, String>>,
    ): List<String>? = sqlOpAsync { conn ->
        regions.filter { (_, coverageWkt) ->
            conn.prepareStatement(
                """
                SELECT EXISTS (
                    SELECT 1 FROM charts
                    WHERE name = $1 AND ST_Intersects(covr, ST_GeomFromText($2, 4326))
                );
                """.trimIndent()
            ).apply {
                setString(1, chartName)
                setString(2, coverageWkt)
            }.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
        }.map { it.first }
    }

    /**
     * Deletes [regionName]'s row from region_export_state so [regionNeedsRebuild] reports it
     * stale again, forcing the export worker to regenerate its archive on its next pass.
     * Returns true if a row was deleted, false if the region had no recorded export.
     */
    suspend fun clearRegionExportState(regionName: String): Boolean? = sqlOpAsync { conn ->
        conn.prepareStatement(
            "DELETE FROM region_export_state WHERE region_name = \$1;"
        ).apply {
            setString(1, regionName)
        }.execute() > 0
    }

    /**
     * Records that [regionName] was just successfully exported, so subsequent [regionNeedsRebuild]
     * checks only report charts ingested after this point.
     */
    suspend fun markRegionExported(regionName: String): Unit? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            INSERT INTO region_export_state (region_name, exported_at) VALUES ($1, now())
            ON CONFLICT (region_name) DO UPDATE SET exported_at = EXCLUDED.exported_at;
            """.trimIndent()
        ).apply {
            setString(1, regionName)
        }.execute()
        Unit
    }
}

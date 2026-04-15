package io.madrona.njord.db

import DataSource
import io.madrona.njord.Singletons

/**
 * A single chart row returned from the region query.
 */
data class RegionChart(
    val id: Long,
    val name: String,
    val scale: Int,
    val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val covrWkb: ByteArray,     // WKB of the coverage polygon
    val dsidPropsJson: String,  // raw JSONB string
    val chartTxtJson: String,   // raw JSONB string
)

/**
 * A single feature row returned from the region query.
 */
data class RegionFeature(
    val id: Long,
    val layer: String,
    val geomWkb: ByteArray,   // WKB of the geometry
    val propsJson: String,    // raw JSONB string
    val chartId: Long,
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
                id,
                name,
                scale,
                file_name,
                updated,
                issued,
                zoom,
                st_asbinary(covr),
                dsid_props::text,
                chart_txt::text
            FROM charts
            WHERE ST_Intersects(covr, ST_GeomFromText($1, 4326))
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setString(1, coverageWkt)
        }.executeQuery().use { rs ->
            val result = mutableListOf<RegionChart>()
            while (rs.next()) {
                result.add(
                    RegionChart(
                        id = rs.getLong(1),
                        name = rs.getString(2),
                        scale = rs.getInt(3),
                        fileName = rs.getString(4),
                        updated = rs.getString(5),
                        issued = rs.getString(6),
                        zoom = rs.getInt(7),
                        covrWkb = rs.getBytes(8),
                        dsidPropsJson = rs.getString(9),
                        chartTxtJson = rs.getString(10),
                    )
                )
            }
            result
        }
    }

    /**
     * Returns all features for the given [chartIds], excluding base features.
     * Processes one chart at a time to bound memory usage.
     */
    suspend fun findFeaturesForChart(chartId: Long): List<RegionFeature>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            SELECT
                id,
                layer,
                st_asbinary(geom),
                props::text,
                chart_id,
                lnam_refs
            FROM features
            WHERE chart_id = $1
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setLong(1, chartId)
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
                        chartId = rs.getLong(5),
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
}

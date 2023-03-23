package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.model.FeatureRecord
import io.madrona.njord.model.LayerQueryResult
import kotlinx.coroutines.Deferred
import org.locationtech.jts.io.WKTReader
import java.sql.ResultSet

class FeatureDao : Dao() {

    suspend fun findLayerPositions(layer: String): List<LayerQueryResult>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT ST_AsText(ST_Centroid(geom)), props, charts.name, charts.zoom
                FROM features JOIN charts ON features.chart_id = charts.id WHERE layer = ?; 
            """.trimIndent()
        ).apply { setString(1, layer) }.executeQuery().use {
            generateSequence {
                if (it.next()) {
                    val wkt = it.getString(1)
                    val coord = WKTReader().read(wkt).coordinate
                    LayerQueryResult(
                        lat = coord.y,
                        lng = coord.x,
                        zoom = it.getFloat(4),
                        props = objectMapper.readValue(it.getString(2)),
                        chartName = it.getString(3)
                    )
                } else null
            }.toList()
        }
    }

    /**
     * Find feature using its LNAM .
     *
     * LNAM Long name.  An encoding of AGEN, FIDN and FIDS used to uniquely identify this features within an S-57 file.
     */
    suspend fun findFeature(lnam: String): FeatureRecord? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """ SELECT id, layer, ST_AsGeoJSON(geom)::JSON as geo, props, chart_id, lower(z_range), upper(z_range)
                FROM features WHERE props->'LNAM' = to_jsonb(?::text);""".trimIndent()
        ).apply {
            setString(1, lnam)
        }.executeQuery().use { it.featureRecord().firstOrNull() }
    }

    suspend fun featureCount(chartId: Long): Int = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT COUNT(id) FROM features WHERE chart_id = ?;").apply {
            setLong(1, chartId)
        }.executeQuery().use {
            if (it.next()) it.getInt(1) else 0
        }
    } ?: 0

    private fun ResultSet.featureRecord(): Sequence<FeatureRecord> {
        return generateSequence {
            if (next()) {
                var i = 0
                FeatureRecord(
                    id = getLong(++i),
                    layer = getString(++i),
                    geom = objectMapper.readValue(getString(++i)),
                    props = objectMapper.readValue(getString(++i)),
                    chartId = getLong(++i),
                    zoomMax = getInt(++i),
                    zoomMin = getInt(++i),
                )
            } else null
        }
    }
}



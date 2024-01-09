package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.layers.TopmarData
import io.madrona.njord.model.FeatureRecord
import io.madrona.njord.model.LayerQueryResult
import org.locationtech.jts.io.WKTReader
import java.sql.Connection
import java.sql.ResultSet
import kotlin.math.ln

class FeatureDao : Dao() {

    suspend fun findLayerPositions(layer: String): List<LayerQueryResult>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT ST_AsText(ST_Centroid(geom)), ST_GeometryType(geom), props, charts.name, charts.zoom
                FROM features JOIN charts ON features.chart_id = charts.id WHERE layer = ?; 
            """.trimIndent()
        ).apply { setString(1, layer) }.executeQuery().use {
            val result = mutableListOf<LayerQueryResult>()
            while (it.next()) {
                val wkt = it.getString(1)
                val coord = WKTReader().read(wkt).coordinate
                val props: Map<String, Any?> = if (layer == "TOPMAR") {
                    objectMapper.readValue<Map<String, Any?>>(it.getString(3)).toMutableMap().apply {
                        val assoc = findAssociatedLayerNames(this["LNAM"].toString())
                        TopmarData.fromAssoc(assoc).addTo(this)
                    }
                } else {
                    objectMapper.readValue(it.getString(3))
                }
                result.add(
                    LayerQueryResult(
                        lat = coord.y,
                        lng = coord.x,
                        zoom = it.getFloat(5),
                        props = props,
                        chartName = it.getString(4),
                        geomType = it.getString(2).replace("ST_", ""),
                    )
                )
            }
            result
        }
    }

    suspend fun findAssociatedLayerNames(lnam: String): List<String> = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT DISTINCT layer FROM features WHERE (props->'LNAM_REFS')::jsonb ?? ?;").apply {
            setString(1, lnam)
        }.executeQuery().use {
            generateSequence {
                if (it.next()) {
                    it.getString(1)
                } else null
            }.toList()
        }
    } ?: emptyList()

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

    fun featureCount(conn: Connection, chartId: Long): Int {
        return conn.prepareStatement("SELECT COUNT(id) FROM features WHERE chart_id = ?;").apply {
            setLong(1, chartId)
        }.executeQuery().use {
            if (it.next()) it.getInt(1) else 0
        }
    }

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



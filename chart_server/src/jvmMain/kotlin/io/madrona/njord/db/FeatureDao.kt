package io.madrona.njord.db

import io.madrona.njord.layers.TopmarData
import io.madrona.njord.model.FeatureRecord
import io.madrona.njord.model.LayerQueryResult
import io.madrona.njord.model.LayerQueryResultPage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.locationtech.jts.io.WKTReader
import java.sql.Connection
import java.sql.ResultSet
import kotlin.math.max

class FeatureDao : Dao() {

    suspend fun findLayerPositionsPage(layer: String, startId: Long): LayerQueryResultPage? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT features.id, ST_AsText(ST_Centroid(geom)), ST_GeometryType(geom), props, charts.name, charts.zoom
                FROM features JOIN charts ON features.chart_id = charts.id WHERE features.id > ? AND features.layer = ? ORDER BY features.id LIMIT 5; 
            """.trimIndent()
        ).apply {
            setLong(1, startId)
            setString(2, layer)
        }.executeQuery().use {
            val result = mutableListOf<LayerQueryResult>()
            var lastId = 0L
            while (it.next()) {
                val id = it.getLong(1)
                lastId = max(lastId, id)
                val wkt = it.getString(2)
                val coord = WKTReader().read(wkt).coordinate
                val props: Map<String, JsonElement> = if (layer == "TOPMAR") {
                    decodeFromString<Map<String, JsonElement>>(it.getString(4)).toMutableMap().apply {
                        val assoc = findAssociatedLayerNames(this["LNAM"].toString())
                        TopmarData.fromAssoc(assoc).addTo(this)
                    }
                    decodeFromString<Map<String, JsonElement>>(it.getString(4))
                } else {
                    decodeFromString<Map<String, JsonElement>>(it.getString(4))
                }
                result.add(
                    LayerQueryResult(
                        id = id,
                        lat = coord.y,
                        lng = coord.x,
                        zoom = it.getFloat(6),
                        props = props,
                        chartName = it.getString(5),
                        geomType = it.getString(3).replace("ST_", ""),
                    )
                )
            }
            LayerQueryResultPage(
                lastId = lastId,
                items = result
            )
        }
    }

    suspend fun findAssociatedLayerNames(lnam: String): List<String> = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT DISTINCT layer FROM features WHERE ?=ANY(lnam_refs);").apply {
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
                    geom = decodeFromString(getString(++i)),
                    props = decodeFromString(getString(++i)),
                    chartId = getLong(++i),
                    zoomMax = getInt(++i),
                    zoomMin = getInt(++i),
                )
            } else null
        }
    }
}



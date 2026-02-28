package io.madrona.njord.db

import Connection
import DataSource
import ResultSet
import io.madrona.njord.Singletons
import io.madrona.njord.geojson.Feature
import io.madrona.njord.layers.TopmarData
import io.madrona.njord.model.Chart
import io.madrona.njord.model.FeatureRecord
import io.madrona.njord.model.LayerQueryResult
import io.madrona.njord.model.LayerQueryResultPage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

class FeatureDao(
    ds: DataSource = Singletons.ds,
) : Dao(ds) {
    suspend fun findLayerPositionsPage(layer: String, startId: Long): LayerQueryResultPage? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT features.id, ST_AsText(ST_Centroid(geom)), ST_GeometryType(geom), props, charts.name, charts.zoom
                FROM features JOIN charts ON features.chart_id = charts.id WHERE features.id > $1 AND features.layer = $2 ORDER BY features.id LIMIT 5; 
            """.trimIndent()
        ).let{ statement ->
            statement.setLong(1, startId)
            statement.setString(2, layer)
            statement.executeQuery().use {
                val result = mutableListOf<LayerQueryResult>()
                var lastId = 0L
                while (it.next()) {
                    val id = it.getLong(1)
                    lastId = max(lastId, id)
                    val wkt = it.getString(2)
//                    val coord = WKTReader().read(wkt).coordinate
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
                            lat = 0.0, //coord.y,
                            lng = 0.0, //coord.x,
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
    }

    suspend fun findAssociatedLayerNames(lnam: String): List<String> = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT DISTINCT layer FROM features WHERE $1=ANY(lnam_refs);").apply {
            setString(1, lnam)
        }.let {
            it.executeQuery().use {
                generateSequence {
                    if (it.next()) {
                        it.getString(1)
                    } else null
                }.toList()
            }
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
                FROM features WHERE props->'LNAM' = to_jsonb($1::text);""".trimIndent()
        ).let {
            it.setString(1, lnam)
            it.executeQuery().use { it.featureRecord().firstOrNull() }
        }
    }

    suspend fun insertFeature(
        layerName: String,
        chart: Chart,
        wkb: ByteArray,
        properties: JsonObject
    ) = sqlOpAsync { conn ->
        conn.statement("""
                INSERT INTO features (layer, geom, props, chart_id, z_range)
                VALUES (
                    $1,
                    st_force2d(st_setsrid(st_geomfromwkb($2), 4326)),
                    $3::json,
                    $4,
                    int4range($5, $6)
                );
        """.trimIndent())
            .setString(1, layerName)
            .setBytes(2, wkb)
            .setString(3, properties.propertyJson())
            .setLong(4, chart.id)
            .setInt(5, properties.minZ())
            .setInt(6, properties.maxZ()).execute()
    }

    private fun JsonObject.propertyJson(): String {
        return encodeToString(JsonObject.serializer(), this)
    }

    private fun JsonObject.minZ(): Int {
        return this["MINZ"]?.jsonPrimitive?.intOrNull ?: 0
    }

    private fun JsonObject.maxZ(): Int {
        return this["MAXZ"]?.jsonPrimitive?.intOrNull ?: 32
    }
    fun featureCount(conn: Connection, chartId: Long): Int {
        return conn.prepareStatement("SELECT COUNT(id) FROM features WHERE chart_id = $1;").let {
            it.setLong(1, chartId)
            it.executeQuery().use {
                if (it.next()) it.getInt(1) else 0
            }
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



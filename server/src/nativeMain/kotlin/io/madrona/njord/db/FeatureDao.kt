package io.madrona.njord.db

import Connection
import DataSource
import ResultSet
import io.madrona.njord.Singletons
import io.madrona.njord.layers.TopmarData
import io.madrona.njord.model.Chart
import io.madrona.njord.model.FeatureRecord
import io.madrona.njord.model.LayerQueryResult
import io.madrona.njord.model.LayerQueryResultPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

class FeatureDao(
    ds: DataSource = Singletons.ds,
) : Dao(ds) {
    suspend fun findLayerPositionsPage(layer: String, startId: Long): LayerQueryResultPage? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT features.id, ST_AsBinary(ST_Centroid(geom)), ST_GeometryType(geom), props, charts.name, charts.zoom
                FROM features JOIN charts ON features.chart_name = charts.name WHERE features.id > $1 AND features.layer = $2 ORDER BY features.id LIMIT 5;
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
                    val wkb = it.getBytes(2)
                    val geom = OgrGeometry.fromWkb4326(wkb)
                    val props: Map<String, JsonElement> = if (layer == "TOPMAR") {
                        decodeFromString<Map<String, JsonElement>>(it.getString(4)).toMutableMap().apply {
                            // .content, not .toString(): the latter renders the JSON form, quotes
                            // included, which never matches a stored LNAM.
                            val lnam = (this["LNAM"] as? JsonPrimitive)?.content
                            val assoc = lnam?.let { findAssociatedLayerNames(it) } ?: emptyList()
                            TopmarData.fromAssoc(assoc).addTo(this)
                        }
                    } else {
                        decodeFromString<Map<String, JsonElement>>(it.getString(4))
                    }
                    result.add(
                        LayerQueryResult(
                            id = id,
                            lat = geom?.pointY ?: 0.0,
                            lng = geom?.pointX ?: 0.0,
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
        // `@> ARRAY[..]` not `= ANY(..)`: GIN's array opclass indexes containment, so `= ANY`
        // seq-scans the whole table even with features_lnam_idx in place.
        conn.prepareStatement("SELECT DISTINCT layer FROM features WHERE lnam_refs @> ARRAY[$1::varchar];").apply {
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
            """ SELECT id, layer, ST_AsGeoJSON(geom)::JSON as geo, props, chart_name, z_min, z_max
                FROM features WHERE props->>'LNAM' = $1;""".trimIndent()
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
        insertFeatureSync(conn, layerName, chart, wkb, properties)
    }

    fun insertFeatureSync(
        conn: Connection,
        layerName: String,
        chart: Chart,
        wkb: ByteArray,
        properties: JsonObject
    ): Long {
        return conn.statement("""
                INSERT INTO features (layer, geom, props, chart_name, z_min, z_max, lnam_refs)
                VALUES (
                    $1,
                    st_force2d(st_setsrid(st_geomfromwkb($2), 4326)),
                    $3::json,
                    $4,
                    $5,
                    $6,
                    $7
                );
        """.trimIndent())
            .setString(1, layerName)
            .setBytes(2, wkb)
            .setString(3, properties.propertyJson())
            .setString(4, chart.name)
            .setInt(5, properties.minZ())
            .setInt(6, properties.maxZ())
            .setArray(7, properties.lnamRefs().toTypedArray()).execute()
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
    fun featureCount(conn: Connection, chartName: String): Int {
        return conn.prepareStatement("SELECT COUNT(id) FROM features WHERE chart_name = $1;").let {
            it.setString(1, chartName)
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
                    chartName = getString(++i),
                    zoomMax = getInt(++i),
                    zoomMin = getInt(++i),
                )
            } else null
        }
    }
}

/**
 * The `LNAM_REFS` of a feature's properties, for the dedicated `lnam_refs` column.
 *
 * GDAL emits LNAM_REFS as an OFTStringList (see the `LNAM_REFS=ON` open option in `Gdal.kt`),
 * which `OgrFeature` turns into a JSON array of strings, so it is already in `props`. It is
 * mirrored into its own column on insert because that is the only form the `features_lnam_idx`
 * GIN index - and therefore the TOPMAR association lookups in `ChartDao.topmarAssocByLnam` and
 * [FeatureDao.findAssociatedLayerNames] - can use.
 *
 * Returns `Array<Any>` because that is what `Statement.setArray` takes.
 */
internal fun JsonObject.lnamRefs(): List<String> =
    (this["LNAM_REFS"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?: emptyList()



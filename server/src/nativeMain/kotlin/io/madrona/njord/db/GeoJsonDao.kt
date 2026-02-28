package io.madrona.njord.db

import Connection
import ResultSet
import SQLException
import io.madrona.njord.ext.jsonStr
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.model.FeatureInsert
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class GeoJsonDao : Dao() {

    private fun ResultSet.featureRecord() = if (next()) {
        decodeFromString<Feature>(getString(1))
    } else {
        null
    }

    private fun ResultSet.featureRecords(): Sequence<Feature> {
        return generateSequence {
            featureRecord()
        }
    }

    suspend fun fetchTileAsync(z: Int, x: Int, y: Int): ByteArray? = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT concat_mvt(?,?,?);").let { stmt ->
            stmt.setInt(1, z)
            stmt.setInt(2, x)
            stmt.setInt(3, y)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getBytes(1) else null
            }
        }
    }

    suspend fun fetchAsync(chartId: Long, layerName: String): FeatureCollection? =
        sqlOpAsync("error fetching feature") { conn ->
            conn.prepareStatement(
                """SELECT
                row_to_json(f)::JSON AS feature
            FROM (
             SELECT
                 id AS id,
                 layer AS layer,
                 'Feature' AS type,
                 ST_AsGeoJSON(geom)::JSON AS geometry,
                 props AS properties
             FROM features
             WHERE layer=? AND chart_id=?
            ) f;"""
            ).let {
                it.setString(1, layerName)
                it.setLong(2, chartId)
                it.let {
                    it.executeQuery().use {
                        FeatureCollection(features = it.featureRecords().toList())
                    }
                }
            }
        }

    suspend fun featureInsertAsync(featureInsert: FeatureInsert): Int? = sqlOpAsync {
        featureInsert(featureInsert, it)
    }

    private fun featureInsert(featureInsert: FeatureInsert, conn: Connection): Int {
        return when (val geoJson = featureInsert.geo) {
            is Geometry -> FeatureRecord(
                chartId = featureInsert.chart.id,
                layerName = featureInsert.layerName,
                geoJson = geoJson.jsonStr()
            ).insert(conn)

            //https://iho.int/iho_pubs/standard/S-57Ed3.1/S-57%20Appendix%20B.1%20Annex%20A%20UOC%20Edition%204.1.0_Jan18_EN.pdf
            //C_AGGR, C_ASSO do not have geometry / primitive is N/A

            is Feature -> {
                val jsonProps = geoJson.propertyJson()
                geoJson.geometry?.let { geometry ->
                    FeatureRecord(
                        chartId = featureInsert.chart.id,
                        layerName = featureInsert.layerName,
                        geoJson = geometry.jsonStr(),
                        jsonProps = jsonProps,
                        zoomRange = geoJson.minZ()..geoJson.maxZ(),
                    ).insert(conn)
                } ?: run {
                    //log.warn("skipping inserting layer $layerName chart id ${chart.id} props $jsonProps")
                    0
                }
            }

            is FeatureCollection -> {
                geoJson.features.fold(0) { acc, feature ->
                    acc + featureInsert.copy(
                        geo = feature
                    ).let {
                        featureInsert(it, conn)
                    }
                }
            }
        }
    }

    private fun Feature.minZ(): Int {
        return properties["MINZ"]?.jsonPrimitive?.intOrNull ?: 0
    }

    private fun Feature.maxZ(): Int {
        return properties["MAXZ"]?.jsonPrimitive?.intOrNull ?: 32
    }

    private fun Feature.propertyJson(): String {
        return encodeToString(JsonObject.serializer(), properties)
    }

    private fun FeatureRecord.insert(conn: Connection): Int {
        try {
            return conn.prepareStatement(
                """
                INSERT INTO features (layer, geom, props, chart_id, z_range)
                VALUES (
                    $1,
                    ST_MakeValid(ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON($2), 4326))),
                    $3::json,
                    $4,
                    int4range($5, $6)
                );
            """.trimIndent()
            ).let {
                it.setString(1, layerName)
                it.setString(2, geoJson)
                it.setString(3, jsonProps)
                it.setLong(4, chartId)
                it.setInt(5, zoomRange.first)
                it.setInt(6, zoomRange.last)
                it.execute().toInt()
            }
        } catch (e: SQLException) {
            log.error("error inserting json $geoJson layer $layerName chart id $chartId props $jsonProps", e)
            return 0
        }
    }
}

private data class FeatureRecord(
    val chartId: Long,
    val layerName: String,
    val geoJson: String,
    val jsonProps: String = "{}",
    val zoomRange: IntRange = 0..32,
)

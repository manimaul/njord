package io.madrona.njord.db

import io.madrona.njord.ext.jsonStr
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.model.FeatureInsert
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonObject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

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

    suspend fun fetchTileAsync(z: Int, x: Int, y: Int) = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT concat_mvt(?,?,?);").apply {
            setInt(1, z)
            setInt(2, x)
            setInt(3, y)
        }.executeQuery().takeIf { it.next() }?.getBytes(1)
    }

    suspend fun fetchAsync(chartId: Long, layerName: String) = sqlOpAsync("error fetching feature") { conn ->
        val stmt = conn.prepareStatement(
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
        ).apply {
            setString(1, layerName)
            setLong(2, chartId)
        }
        stmt.executeQuery().use {
            FeatureCollection(features = it.featureRecords().toList())
        }
    }

    suspend fun insertAsync(featureInsert: FeatureInsert): Int? = sqlOpAsync("error inserting feature") {
        featureInsert.insert(it)
    }

    private fun FeatureInsert.insert(conn: Connection): Int {
        return when (val geoJson = geo) {
            is Geometry -> FeatureRecord(
                chartId = chart.id,
                layerName = layerName,
                geoJson = geoJson.jsonStr()
            ).insert(conn)

            //https://iho.int/iho_pubs/standard/S-57Ed3.1/S-57%20Appendix%20B.1%20Annex%20A%20UOC%20Edition%204.1.0_Jan18_EN.pdf
            //C_AGGR, C_ASSO do not have geometry / primitive is N/A

            is Feature -> {
                val jsonProps = geoJson.propertyJson()
                geoJson.geometry?.let { geometry ->
                   FeatureRecord(
                       chartId = chart.id,
                       layerName = layerName,
                       geoJson = geometry.jsonStr(),
                       jsonProps = jsonProps,
                       zoomRange = geoJson.minZ()..geoJson.maxZ(),
                   ).insert(conn)
               } ?: run {
                   log.warn("skipping inserting layer $layerName chart id ${chart.id} props $jsonProps")
                   0
               }
            }

            is FeatureCollection -> {
                geoJson.features.fold(0) { acc, feature ->
                    acc + copy(
                        geo = feature
                    ).insert(conn)
                }
            }

            else -> 0
        }
    }

    private fun Feature.minZ(): Int {
        return properties["MINZ"]?.toString()?.toIntOrNull() ?: 0
    }

    private fun Feature.maxZ(): Int {
        return properties["MAXZ"]?.toString()?.toIntOrNull() ?: 32
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
                    ?,
                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4326),
                    ?::json,
                    ?,
                    int4range(?,?)
                );
            """.trimIndent()
            ).apply {
                setString(1, layerName)
                setString(2, geoJson)
                setString(3, jsonProps)
                setLong(4, chartId)
                setInt(5, zoomRange.first)
                setInt(6, zoomRange.last)
            }.executeUpdate()
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

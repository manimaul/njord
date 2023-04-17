package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.model.FeatureInsert
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.Geometry
import java.sql.Connection
import java.sql.ResultSet

class GeoJsonDao : Dao() {

    private fun ResultSet.featureRecord() = if (next()) {
        objectMapper.readValue<Feature>(getString(1))
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
            it.featureRecords().fold(FeatureCollection()) { acc, feature ->
                acc.apply {
                    addFeature(feature)
                }
            }
        }
    }

    suspend fun withConnectionDo(block: suspend (conn: Connection) -> Unit) = sqlOpAsync(block = block)
    suspend fun <T> withConnection(block: suspend (conn: Connection) -> T): T? = sqlOpAsync(block = block)

    suspend fun insertAsync(featureInsert: FeatureInsert): Int? = sqlOpAsync("error inserting feature") {
        featureInsert.insert(it)
    }

    fun insert(conn: Connection, insert: FeatureInsert): Int {
        return insert.insert(conn)
    }

    private fun FeatureInsert.insert(conn: Connection): Int {
        return when (geo) {
            is Geometry -> FeatureRecord(
                chartId = chart.id,
                layerName = layerName,
                geoJson = objectMapper.writeValueAsString(geo)
            ).takeIf { geo.geometry != null }?.insert(conn) ?: 0
            //https://iho.int/iho_pubs/standard/S-57Ed3.1/S-57%20Appendix%20B.1%20Annex%20A%20UOC%20Edition%204.1.0_Jan18_EN.pdf
            //C_AGGR, C_ASSO do not have geometry / primitive is N/A

            is Feature -> FeatureRecord(
                chartId = chart.id,
                layerName = layerName,
                geoJson = objectMapper.writeValueAsString(geo.geometry),
                jsonProps = geo.propertyJson(),
                zoomRange = geo.minZ()..geo.maxZ(),
            ).takeIf { geo.geometry != null }?.insert(conn) ?: 0

            is FeatureCollection -> {
                geo.features.fold(0) { acc, feature ->
                    acc + copy(
                        geo = feature
                    ).insert(conn)
                }
            }

            else -> 0
        }
    }

    private fun Feature.minZ(): Int {
        return properties?.get("MINZ")?.toString()?.toIntOrNull() ?: 0
    }

    private fun Feature.maxZ(): Int {
        return properties?.get("MAXZ")?.toString()?.toIntOrNull() ?: 32
    }

    private fun Feature.propertyJson(): String {
        return properties?.let { objectMapper.writeValueAsString(it) } ?: "{}"
    }

    private fun FeatureRecord.insert(conn: Connection): Int {
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
        }.executeUpdate() //todo: invalid geojson representation error inserting feature
        //org.postgresql.util.PSQLException: ERROR: invalid GeoJSON representation
    }
}

private data class FeatureRecord(
    val chartId: Long,
    val layerName: String,
    val geoJson: String = "{}",
    val jsonProps: String = "{}",
    val zoomRange: IntRange = 0..32,
)

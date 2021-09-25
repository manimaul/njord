package io.madrona.njord.db

import io.madrona.njord.model.FeatureInsert
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.Geometry
import java.sql.Connection

class GeoJsonDao : Dao() {

    fun insertAsync(featureInsert: FeatureInsert) = sqlOpAsync("error inserting feature") {
        featureInsert.insert(it)
    }

    private fun FeatureInsert.insert(conn: Connection) : Int {
        return when (geo) {
            is Geometry -> InsertionParams(
                chartId = chart.id,
                layerName = layerName,
                geoJson = objectMapper.writeValueAsString(geo)
            ).insert(conn)
            is Feature -> InsertionParams(
                chartId = chart.id,
                layerName = layerName,
                geoJson = objectMapper.writeValueAsString(geo.geometry),
                jsonProps = geo.propertyJson(),
                minZ = geo.minZ(),
                maxZ = geo.maxZ()
            ).insert(conn)
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

    private fun Feature.minZ() : Int {
        return properties?.get("MINZ")?.toString()?.toIntOrNull() ?: 0
    }

    private fun Feature.maxZ() : Int {
        return properties?.get("MAXZ")?.toString()?.toIntOrNull() ?: 32
    }

    private fun Feature.propertyJson() : String {
        return properties?.let { objectMapper.writeValueAsString(it) } ?: "{}"
    }

    private fun InsertionParams.insert(conn: Connection) : Int {
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
            setInt(5, minZ)
            setInt(6, maxZ)
        }.executeUpdate()
    }
}

private data class InsertionParams(
    val chartId: Long,
    val layerName: String,
    val geoJson: String = "{}",
    val jsonProps: String = "{}",
    val minZ: Int = 0,
    val maxZ: Int = 32,
)

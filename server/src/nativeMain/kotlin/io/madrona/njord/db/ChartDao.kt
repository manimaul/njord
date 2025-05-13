package io.madrona.njord.db

import Connection
import ResultSet
import geos.Geos
import geos.GeosGeometry
import io.madrona.njord.Singletons
import io.madrona.njord.ext.jsonStr
import io.madrona.njord.geojson.FeatureBuilder
import io.madrona.njord.model.*
import kotlinx.serialization.json.Json.Default.decodeFromString

class ChartDao(
//    private val findInfoAsyncTimer: Timer = Singletons.metrics.timer("findInfoAsync"),
//    private val findChartFeaturesAsyncTimer: Timer = Singletons.metrics.timer("findChartFeaturesAsync"),
    private val featureDao: FeatureDao = Singletons.featureDao,
) : Dao() {

    private fun ResultSet.chart(layers: List<String>, featureCount: Int) = generateSequence {
        if (next()) {
            var i = 0
            Chart(
                id = getLong(++i),
                name = getString(++i),
                scale = getInt(++i),
                fileName = getString(++i),
                updated = getString(++i),
                issued = getString(++i),
                zoom = getInt(++i),
                covr = FeatureBuilder(geometryJson = getString(++i)).build(),
                bounds = getBytes(++i).let {
//                    val env = WKBReader().read(it).envelopeInternal
//                    Bounds(leftLng = env.minX, topLat = env.maxY, rightLng = env.maxX, bottomLat = env.minY)
                    Bounds(leftLng = 0.0, topLat = 0.0, rightLng = 0.0, bottomLat = 0.0)
                },
                layers = layers,
                dsidProps = decodeFromString(getString(++i)),
                chartTxt = decodeFromString(getString(++i)),
                featureCount = featureCount,
            )
        } else null
    }

    suspend fun findChartsWithLayerAsync(layer: String): List<Chart>? = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT chart_id FROM features WHERE layer = ?;").use {
            it.setString(1, layer)
            it.executeQuery().use {
                val result = mutableListOf<Long>()
                while (it.next()) {
                    result.add(it.getLong(1))
                }
                result.mapNotNull { findAsync(it) }
            }
        }
    }

    private fun findLayers(id: Long, conn: Connection): List<String> {
        return conn.prepareStatement(
            "SELECT DISTINCT layer FROM features where chart_id=?;"
        ).use {
            it.setLong(1, id)
            it.executeQuery().use {
                generateSequence {
                    if (it.next()) {
                        it.getString(1)
                    } else {
                        null
                    }
                }.toList()
            }
        }
    }

    /**
     * https://postgis.net/docs/reference.html
     */
    suspend fun findChartFeaturesAsync(
        covered: GeosGeometry,
        x: Int,
        y: Int,
        z: Int,
        chartId: Long
    ): List<ChartFeature>? =
        sqlOpAsync { conn ->
//            val tCtx = findChartFeaturesAsyncTimer.time()
            val result = conn.prepareStatement(
                """
                WITH exclude AS (VALUES (st_transform(st_geomfromwkb(?,4326), 3857))),
                    tile AS (VALUES (st_tileenvelope(?,?,?)))
                SELECT st_asbinary(st_asmvtgeom(st_difference(st_transform(geom, 3857), (table exclude)), (table tile))), 
                    props, 
                    layer
                FROM features
                WHERE chart_id=?
                    AND ? <@ z_range
                    AND st_intersects(geom, st_transform((table tile), 4326));
          """.trimIndent()
            ).apply {
                var i = 0
                setBytes(++i, Geos.wkbWriter.write(covered))
                setInt(++i, z)
                setInt(++i, x)
                setInt(++i, y)
                setLong(++i, chartId)
                setInt(++i, z)
            }.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) {
                        ChartFeature(
                            geomWKB = rs.getBytes(1),
                            props = decodeFromString(rs.getString(2)),
                            layer = rs.getString(3)
                        )
                    } else null
                }.toList()
            }
//            tCtx.stop()
            result
        }

    suspend fun findInfoAsync(polygon: GeosGeometry): List<ChartInfo>? = sqlOpAsync { conn ->
//        val tCtx = findInfoAsyncTimer.time()
        conn.prepareStatement(
            """
                SELECT 
                    id,
                    scale, 
                    zoom,
                    st_asbinary(covr) as covrWKB
                FROM charts 
                WHERE st_intersects(st_geomfromwkb(?, 4326), covr)
                ORDER BY scale;
            """.trimIndent()
        ).use {
            it.setBytes(1, Geos.wkbWriter.write(polygon))
            it.executeQuery().use { rs ->
                val result =
                    generateSequence {
                        if (rs.next()) {
                            val id = rs.getLong(1)
                            ChartInfo(
                                id = id,
                                scale = rs.getInt(2),
                                zoom = rs.getInt(3),
                                covrWKB = rs.getBytes(4)
                            )
                        } else null
                    }.toList()
//                tCtx.stop()
                result
            }
        }
    }

    suspend fun findAsync(id: Long): Chart? = sqlOpAsync { conn ->
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
                st_asgeojson(covr)::JSON,
                st_asbinary(covr),
                dsid_props,
                chart_txt
            FROM charts
            WHERE id=?;
            """.trimIndent()
        ).use {
            it.setLong(1, id)
            it.executeQuery().use { it.chart(findLayers(id, conn), featureDao.featureCount(conn, id)).firstOrNull() }
        }
    }

    private fun chartCount(conn: Connection): Int {
        return conn.prepareStatement("SELECT COUNT(id) FROM charts;").executeQuery().use {
            if (it.next()) it.getInt(1) else 0
        }
    }

    suspend fun listAsync(nextPageId: Long? = null): ChartCatalog? = sqlOpAsync { conn ->
        val totalCount = chartCount(conn)
        conn.prepareStatement(
            """SELECT id, name FROM charts WHERE id >= ? ORDER BY id LIMIT ${PAGE_SIZE + 1};
            """.trimIndent()
        ).use {
            it.setLong(1, nextPageId ?: 0L)
            it.executeQuery().use {
                val page = mutableListOf<ChartItem>()
                var num = 0
                var nextId: Long? = null
                while (it.next() && num <= PAGE_SIZE) {
                    val id = it.getLong(1)
                    if (++num > PAGE_SIZE) {
                        nextId = id
                    } else {
                        page.add(
                            ChartItem(
                                id = id,
                                name = it.getString(2),
                            )
                        )
                    }
                }
                ChartCatalog(
                    totalChartCount = totalCount,
                    nextId = nextId,
                    page = page
                )
            }
        }
    }

    suspend fun insertAsync(chartInsert: ChartInsert, overwrite: Boolean): Chart? = sqlOpAsync(tryCount = 2) {
        insertAsync(chartInsert, overwrite, it)
    }

    private fun insertAsync(chartInsert: ChartInsert, overwrite: Boolean, conn: Connection): Chart? {
        if (overwrite) {
            delete(name = chartInsert.name, conn)
        }
        return conn.prepareStatement(
            """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) 
                VALUES (?,?,?,?,?,?,st_setsrid(st_geomfromgeojson(?), 4326),?::json,?::json)
                RETURNING id, name, scale, file_name, updated, issued, zoom, st_asgeojson(covr)::JSON, st_asbinary(covr), dsid_props, chart_txt""".trimIndent()
        ).use { stmt ->
            stmt.setString(1, chartInsert.name)
            stmt.setInt(2, chartInsert.scale)
            stmt.setString(3, chartInsert.fileName)
            stmt.setString(4, chartInsert.updated)
            stmt.setString(5, chartInsert.issued)
            stmt.setInt(6, chartInsert.zoom)
            stmt.setString(7, chartInsert.covr.geometry?.jsonStr())
            stmt.setObject(8, chartInsert.dsidProps.jsonStr())
            stmt.setObject(9, chartInsert.chartTxt.jsonStr())
            stmt.executeUpdateGeneratedKeys{ updated, result ->
                if (updated == 1) {
                   result.chart(layers = emptyList(), 0) .firstOrNull()
                } else {
                    null
                }
            }
        }
    }

    private fun delete(name: String, conn: Connection): Boolean {
        return conn.prepareStatement(
            """
                DELETE from features WHERE features.chart_id IN
                (SELECT id from charts where name = ?);
                DELETE FROM charts WHERE name=?;
                """.trimIndent()
        ).use {
            it.setString(1, name)
            it.setString(2, name)
            it.executeUpdate() > 0
        }
    }

    suspend fun deleteAsync(id: Long): Boolean? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
                DELETE FROM features WHERE chart_id=?;
                DELETE FROM charts WHERE id=?;
                """.trimIndent()
        ).use {
            it.setLong(1, id)
            it.setLong(2, id)
            it.executeUpdate() > 0
        }
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}

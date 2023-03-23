package io.madrona.njord.db

import com.codahale.metrics.Timer
import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.ChartCatalog
import io.madrona.njord.ChartItem
import io.madrona.njord.Singletons
import io.madrona.njord.model.*
import mil.nga.sf.geojson.Feature
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

class ChartDao(
    private val findInfoAsyncTimer: Timer = Singletons.metrics.timer("findInfoAsync"),
    private val findChartFeaturesAsyncTimer: Timer = Singletons.metrics.timer("findChartFeaturesAsync"),
    private val featureDao: FeatureDao = FeatureDao(),
) : Dao() {

    private fun ResultSet.chart(layers: List<String>) = generateSequence {
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
                covr = Feature().apply { geometry = objectMapper.readValue(getString(++i)) },
                bounds = getBytes(++i).let {
                    val env = WKBReader().read(it).envelopeInternal
                    Bounds(leftLng = env.minX, topLat = env.maxY, rightLng = env.maxX, bottomLat = env.minY)
                },
                layers = layers,
                dsidProps = objectMapper.readValue(getString(++i)),
                chartTxt = objectMapper.readValue(getString(++i)),
            )
        } else null
    }

    suspend fun findChartsWithLayerAsync(layer: String): List<Chart>? = sqlOpAsync { conn ->
        val statement = conn.prepareStatement("SELECT chart_id FROM features WHERE layer = ?;").apply {
            setString(1, layer)
        }
        val result = mutableListOf<Long>()
        statement.executeQuery().let {
            while (it.next()) {
                result.add(it.getLong(1))
            }
        }
        result.mapNotNull { findAsync(it) }
    }

    private fun findLayers(id: Long, conn: Connection): List<String> {
        return conn.prepareStatement(
            "SELECT DISTINCT layer FROM features where chart_id=?;"
        ).apply {
            setLong(1, id)
        }.executeQuery().let {
            it.use {
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
        covered: Geometry,
        x: Int,
        y: Int,
        z: Int,
        chartId: Long
    ): List<ChartFeature>? =
        sqlOpAsync { conn ->
            val tCtx = findChartFeaturesAsyncTimer.time()
            val result = conn.prepareStatement(
                """
              WITH exclude AS (VALUES (st_geomfromwkb(?, 4326))),
                   tile AS (VALUES (st_transform(st_tileenvelope(?,?,?), 4326)))
              SELECT st_asbinary(st_asmvtgeom(st_difference(geom, (table exclude)), (table tile))), 
                     props, 
                     layer
              FROM features
              WHERE chart_id=?
                AND ? <@ z_range
                AND st_intersects(geom, (table tile));
          """.trimIndent()
            ).apply {
                var i = 0
                setBytes(++i, WKBWriter().write(covered))
                setInt(++i, z)
                setInt(++i, x)
                setInt(++i, y)
                setLong(++i, chartId)
                setInt(++i, z)
            }.executeQuery().let { rs ->
                rs.use {
                    generateSequence {
                        if (rs.next()) {
                            ChartFeature(
                                geomWKB = rs.getBytes(1),
                                props = objectMapper.readValue(rs.getString(2)),
                                layer = rs.getString(3)
                            )
                        } else null
                    }.toList()
                }
            }
            tCtx.stop()
            result
        }

    suspend fun findInfoAsync(polygon: Polygon): List<ChartInfo>? = sqlOpAsync { conn ->
        val tCtx = findInfoAsyncTimer.time()
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
        ).apply {
            setBytes(1, WKBWriter().write(polygon))
        }.executeQuery()?.let { rs ->
            val result = rs.use {
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
            }
            tCtx.stop()
            result
        }
    }

    suspend fun findAsync(id: Long): Chart? = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
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
        ).apply {
            setLong(1, id)
        }
        stmt.executeQuery().use { it.chart(findLayers(id, conn)).firstOrNull() }
    }

    private fun chartCount(conn: Connection): Int {
        return conn.prepareStatement("SELECT COUNT(id) FROM charts;").executeQuery().use {
            if (it.next()) it.getInt(1) else 0
        }
    }

    suspend fun listAsync(nextPageId: Long? = null): ChartCatalog? = sqlOpAsync { conn ->
        val totalCount = chartCount(conn)
        val stmt = conn.prepareStatement(
            """SELECT id, name FROM charts WHERE id >= ? ORDER BY id LIMIT ${PAGE_SIZE + 1};
            """.trimIndent()
        ).apply {
            setLong(1, nextPageId ?: 0L)
        }
        stmt.executeQuery().use {
            val page = mutableListOf<ChartItem>()
            var num = 0
            var nextId: Long? = null
            while (it.next() && num <= PAGE_SIZE) {
                val id = it.getLong(1)
                if (++num > PAGE_SIZE) {
                    nextId = id
                } else {
                    val count = featureDao.featureCount(id)
                    page.add(
                        ChartItem(
                            id = id,
                            name = it.getString(2),
                            featureCount = count,
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

    suspend fun insertAsync(chartInsert: ChartInsert, overwrite: Boolean): Chart? = sqlOpAsync { conn ->
        if (overwrite) {
            delete(name = chartInsert.name, conn)
        }
        val stmt = conn.prepareStatement(
            """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) 
                VALUES (?,?,?,?,?,?,st_setsrid(st_geomfromgeojson(?), 4326),?::json,?::json)
                RETURNING id, name, scale, file_name, updated, issued, zoom, st_asgeojson(covr)::JSON, st_asbinary(covr), dsid_props, chart_txt""".trimIndent(),
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setString(1, chartInsert.name)
            setInt(2, chartInsert.scale)
            setString(3, chartInsert.fileName)
            setString(4, chartInsert.updated)
            setString(5, chartInsert.issued)
            setInt(6, chartInsert.zoom)
            setString(7, objectMapper.writeValueAsString(chartInsert.covr.geometry))
            setObject(8, objectMapper.writeValueAsString(chartInsert.dsidProps))
            setObject(9, objectMapper.writeValueAsString(chartInsert.chartTxt))
        }

        stmt.executeUpdate().takeIf { it == 1 }?.let {
            stmt.generatedKeys?.use { rs ->
                rs.chart(layers = emptyList()).firstOrNull()
            }
        }
    }

    private fun delete(name: String, conn: Connection): Boolean {
        return conn.prepareStatement(
            """
                DELETE from features WHERE features.chart_id IN
                (SELECT id from charts where name = ?);
                DELETE FROM charts WHERE name=?;
                """.trimIndent(),
            Statement.NO_GENERATED_KEYS
        ).apply {
            setString(1, name)
            setString(2, name)
        }.executeUpdate() > 0
    }

    suspend fun deleteAsync(id: Long): Boolean? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
                DELETE FROM features WHERE chart_id=?;
                DELETE FROM charts WHERE id=?;
                """.trimIndent(),
            Statement.NO_GENERATED_KEYS
        ).apply {
            setLong(1, id)
            setLong(2, id)
        }.executeUpdate() > 0
    }

    companion object {
        const val PAGE_SIZE = 10
    }
}

package io.madrona.njord.db

import com.codahale.metrics.Timer
import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.ChartItem
import io.madrona.njord.Singletons
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.ChartInfo
import io.madrona.njord.model.ChartInsert
import kotlinx.coroutines.Deferred
import mil.nga.sf.geojson.Feature
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKBWriter
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

class ChartDao(
    private val findInfoAsyncTimer: Timer = Singletons.metrics.timer("findInfoAsync"),
    private val findChartFeaturesAsyncTimer: Timer = Singletons.metrics.timer("findChartFeaturesAsync"),
) : Dao() {

    private fun ResultSet.chart(layers: List<String>) = sequence {
        while (next()) {
            yield(
                Chart(
                    id = getLong(1),
                    name = getString(2),
                    scale = getInt(3),
                    fileName = getString(4),
                    updated = getString(5),
                    issued = getString(6),
                    zoom = getInt(7),
                    covr = Feature().apply {
                        geometry = objectMapper.readValue(getString(8))
                    },
                    layers = layers,
                    dsidProps = objectMapper.readValue(getString(9)),
                    chartTxt = objectMapper.readValue(getString(10)),
                )
            )
        }
    }

    fun findChartsWithLayerAsync(layer: String): Deferred<List<Chart>?> = sqlOpAsync { conn ->
        val statement = conn.prepareStatement("SELECT chart_id FROM features WHERE layer = ?;").apply {
            setString(1, layer)
        }
        val result = mutableListOf<Long>()
        statement.executeQuery().let {
            while (it.next()) {
                result.add(it.getLong(1))
            }
        }
        result.mapNotNull { findAsync(it).await() }
    }

    private fun findLayers(id: Long, conn: Connection): List<String> {
        return conn.prepareStatement(
            "SELECT DISTINCT layer FROM features where chart_id=?;"
        ).apply {
            setLong(1, id)
        }.executeQuery().let {
            sequence {
                while (it.next()) {
                    yield(it.getString(1))
                }
            }
        }.toList()
    }

    /**
     * https://postgis.net/docs/reference.html
     */
    fun findChartFeaturesAsync(
        covered: Geometry,
        x: Int,
        y: Int,
        z: Int,
        chartId: Long
    ): Deferred<List<ChartFeature>?> =
        sqlOpAsync { conn ->
            val tCtx = findChartFeaturesAsyncTimer.time()
            val result = conn.prepareStatement(
                """
              WITH exclude AS (VALUES (ST_GeomFromWKB(?, 4326))),
                   tile AS (VALUES (ST_Transform(ST_TileEnvelope(?,?,?), 4326)))
              SELECT ST_AsBinary(ST_AsMVTGeom(ST_Difference(geom, (table exclude)), (table tile))), 
                     props, 
                     layer
              FROM features
              WHERE chart_id=?
                AND ? <@ z_range
                AND ST_Intersects(geom, (table tile));
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
                sequence {
                    while (rs.next()) {
                        yield(
                            ChartFeature(
                                geomWKB = rs.getBytes(1),
                                props = objectMapper.readValue(rs.getString(2)),
                                layer = rs.getString(3)
                            )
                        )
                    }
                }.toList()
            }
            tCtx.stop()
            result
        }

    fun findInfoAsync(polygon: Polygon): Deferred<List<ChartInfo>?> = sqlOpAsync { conn ->
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
            val result = sequence {
                while (rs.next()) {
                    val id = rs.getLong(1)
                    yield(
                        ChartInfo(
                            id = id,
                            scale = rs.getInt(2),
                            zoom = rs.getInt(3),
                            covrWKB = rs.getBytes(4)
                        )
                    )
                }
            }.toList()
            tCtx.stop()
            result
        }
    }

    fun findAsync(id: Long): Deferred<Chart?> = sqlOpAsync { conn ->
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
                ST_AsGeoJSON(covr)::JSON as covr,
                dsid_props,
                chart_txt
            FROM charts
            WHERE id=?;
            """.trimIndent()
        ).apply {
            setLong(1, id)
        }
        stmt.executeQuery().chart(findLayers(id, conn)).firstOrNull()
    }

    fun listAsync(): Deferred<List<ChartItem>?> = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
            """
            SELECT
                id, name
            FROM charts;
            """.trimIndent()
        )
        stmt.executeQuery().let {
            val result = mutableListOf<ChartItem>()
            while (it.next()) {
                result.add(
                    ChartItem(
                        id = it.getLong(1),
                        name = it.getString(2)
                    )
                )
            }
            result
        }
    }

    fun insertAsync(chartInsert: ChartInsert): Deferred<Chart?> = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
            """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) 
                VALUES (?,?,?,?,?,?,ST_SetSRID(ST_GeomFromGeoJSON(?), 4326),?::json,?::json)
                RETURNING id, name, scale, file_name, updated, issued, zoom, ST_AsGeoJSON(covr)::JSON as covr, dsid_props, chart_txt""".trimIndent(),
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
            stmt.generatedKeys.use { rs ->
                rs.chart(emptyList())
            }
        }?.firstOrNull()
    }


    fun deleteAsync(id: Long): Deferred<Boolean?> = sqlOpAsync { conn ->
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
}


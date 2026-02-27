package io.madrona.njord.db

import Connection
import DataSource
import ResultSet
import io.madrona.njord.Singletons
import io.madrona.njord.ext.jsonStr
import io.madrona.njord.geojson.FeatureBuilder
import io.madrona.njord.model.*
import kotlinx.serialization.json.Json.Default.decodeFromString

class ChartDao(
    ds: DataSource = Singletons.ds,
    private val featureDao: FeatureDao = Singletons.featureDao,
) : Dao(ds) {

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
                bounds = getBytes(++i).let { wkb ->
                    OgrGeometry.fromWkb4326(wkb)?.envelope()
                },
                layers = layers,
                dsidProps = decodeFromString(getString(++i)),
                chartTxt = decodeFromString(getString(++i)),
                featureCount = featureCount,
            )
        } else null
    }

    suspend fun findChartsWithLayerAsync(layer: String): List<Chart>? = sqlOpAsync { conn ->
        conn.prepareStatement("SELECT chart_id FROM features WHERE layer = $1;").let {
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
            "SELECT DISTINCT layer FROM features where chart_id=$1;"
        ).let {
            it.setLong(1, id)
            it.executeQuery().use {
                generateSequence {
                    if (it.next()) {
                        it.getString("layer")
                    } else {
                        null
                    }
                }.toList()
            }
        }
    }

    suspend fun findChartFeaturesAsync4326(
        x: Int,
        y: Int,
        z: Int,
        chartId: Long
    ): List<ChartFeature>? =
        sqlOpAsync { conn ->
            val result = conn.prepareStatement(
                """
WITH tile AS (VALUES (st_transform(
        st_tileenvelope($1, $2, $3),
        4326)))
SELECT st_asbinary(
               st_intersection(
                       geom,
                       (table tile)
               )
       ),
       props,
       layer
FROM features
WHERE chart_id = $4
  AND $5 <@ z_range
  AND st_intersects(geom, (table tile));
          """.trimIndent()
            ).apply {
                setInt(1, z)
                setInt(2, x)
                setInt(3, y)
                setLong(4, chartId)
                setInt( 5, z)
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
            result
        }

    suspend fun findChartFeaturesAsync4326(
        exclusionMask: ByteArray,
        tile: ByteArray,
        chartId: Long,
        zoom: Int,
    ): List<ChartFeature>? =
        sqlOpAsync { conn ->
            val result = conn.prepareStatement(
                """
WITH exclude AS (VALUES (st_geomfromwkb($1, 4326))),
     tile AS (VALUES (st_geomfromwkb($2, 4326)))
SELECT st_asbinary(
               st_intersection(
                       st_difference(
                               geom, (table exclude)
                       ),
                       (table tile)
               )
       ),
       props,
       layer
FROM features
WHERE chart_id = $3
  AND $4 <@ z_range
  AND st_intersects(geom, (table tile));
          """.trimIndent()
            ).apply {
                setBytes(1, exclusionMask)
                setBytes(2, tile)
                setLong(3, chartId)
                setInt( 4, zoom)
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
            result
        }

    suspend fun findChartFeaturesAsync4326(
        inclusionMask: ByteArray,
        chartId: Long,
        zoom: Int,
    ): List<ChartFeature>? =
        sqlOpAsync { conn ->
            val result = conn.prepareStatement(
                """
WITH include AS (VALUES (st_geomfromwkb($1, 4326)))
SELECT st_asbinary(
               st_intersection(
                       geom,
                       (table include)
               )
       ),
       props,
       layer
FROM features
WHERE chart_id = $2
  AND $3 <@ z_range
  AND st_intersects(geom, (table include));
          """.trimIndent()
            ).apply {
                setBytes(1, inclusionMask)
                setLong(2, chartId)
                setInt( 3, zoom)
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
            result
        }

    suspend fun findBaseInfoAsync(scale: Int): List<BaseInfo>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
                SELECT
                    id,
                    scale,
                    name
                FROM charts
                WHERE base_map=true
                AND scale=$1
                ORDER BY scale ASC;
            """.trimIndent()
        ).let {
            it.setInt(1, scale)
            it.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) {
                        val id = rs.getLong(1)
                        BaseInfo(
                            id = id,
                            scale = rs.getInt(2),
                            name = rs.getString(3),
                        )
                    } else null
                }.toList()
            }
        }
    }

    suspend fun findInfoAsync(wkb: ByteArray): List<ChartInfo>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
                SELECT
                    id,
                    scale,
                    zoom,
                    st_asbinary(covr) as wkb
                FROM charts
                WHERE st_intersects(st_geomfromwkb($1, 4326), covr)
                AND base_map=false
                ORDER BY scale ASC;
            """.trimIndent()
        ).let {
            it.setBytes(1, wkb)
            it.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) {
                        val id = rs.getLong(1)
                        ChartInfo(
                            id = id,
                            scale = rs.getInt(2),
                            zoom = rs.getInt(3),
                            covrWKB = rs.getBytes(4),
                        )
                    } else null
                }.toList()
            }
        }
    }

    suspend fun findAsync(id: Long): Chart? = sqlOpAsync { conn ->
        val layers = findLayers(id, conn)
        val count = featureDao.featureCount(conn, id)
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
            WHERE id=$1;
            """.trimIndent()
        ).let { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { result ->
                result.chart(layers, count).firstOrNull()
            }
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
            """SELECT id, name FROM charts WHERE id >= $1 ORDER BY id LIMIT ${PAGE_SIZE + 1};
            """.trimIndent()
        ).let {
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
        return conn.statement(
            """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt, base_map)
                VALUES ($1, $2, $3, $4, $5, $6, st_setsrid(st_geomfromgeojson($7), 4326), $8::json, $9::json, $10)
                RETURNING id, name, scale, file_name, updated, issued, zoom, st_asgeojson(covr)::JSON, st_asbinary(covr), dsid_props, chart_txt""".trimIndent()
        ).let { stmt ->
            stmt.setString(1, chartInsert.name)
            stmt.setInt(2, chartInsert.scale)
            stmt.setString(3, chartInsert.fileName)
            stmt.setString(4, chartInsert.updated)
            stmt.setString(5, chartInsert.issued)
            stmt.setInt(6, chartInsert.zoom)
            stmt.setString(7, chartInsert.covr.geometry?.jsonStr())
            stmt.setAuto(8, chartInsert.dsidProps.jsonStr())
            stmt.setAuto(9, chartInsert.chartTxt.jsonStr())
            stmt.setBool(10, chartInsert.isBasemap)
            stmt.executeReturning().use { result ->
                result.chart(layers = emptyList(), 0).firstOrNull()
            }
        }
    }

    private fun delete(name: String, conn: Connection): Boolean {
        conn.prepareStatement(
            "DELETE FROM features WHERE features.chart_id IN (SELECT id FROM charts WHERE name=\$1);"
        ).let {
            it.setString(1, name)
            it.execute()
        }
        return conn.prepareStatement(
            "DELETE FROM charts WHERE name=\$1;"
        ).let {
            it.setString(1, name)
            it.execute() > 0
        }
    }

    suspend fun deleteAsync(id: Long): Boolean? = sqlOpAsync { conn ->
        conn.prepareStatement(
            "DELETE FROM features WHERE chart_id=\$1;"
        ).let {
            it.setLong(1, id)
            it.execute()
        }
        conn.prepareStatement(
            "DELETE FROM charts WHERE id=\$1;"
        ).let {
            it.setLong(1, id)
            it.execute() > 0
        }
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}

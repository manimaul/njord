package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartInsert
import kotlinx.coroutines.Deferred
import mil.nga.sf.geojson.Feature
import java.sql.*

class ChartDao : Dao() {

    private fun ResultSet.chart(layers: List<String>): Chart? = if (next()) {
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
    } else {
        null
    }

    fun findLayers(id: Long, conn: Connection): List<String> {
        return conn.prepareStatement(
            "SELECT DISTINCT  layer FROM features where chart_id=?;",
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setLong(1, id)
        }.executeQuery().let {
            generateSequence {
                if (it.next()) {
                    it.getString(1)
                } else {
                    null
                }
            }
        }.toList()
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
            WHERE id = 1;
            """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setLong(1, id)
        }
        stmt.executeQuery().chart(findLayers(id, conn))
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
        }
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


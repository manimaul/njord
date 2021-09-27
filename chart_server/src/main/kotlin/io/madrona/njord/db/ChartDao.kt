package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartInsert
import java.sql.*

class ChartDao : Dao() {

    private fun ResultSet.chart(layers: List<String>) = if (next()) {
        Chart(
            id = getLong(1),
            name = getString(2),
            scale = getInt(3),
            fileName = getString(4),
            updated = getString(5),
            issued = getString(6),
            zoom = getInt(7),
            layers = layers,
            dsidProps = objectMapper.readValue(getString(8)),
            chartTxt = objectMapper.readValue(getString(9)),
        )
    } else {
        null
    }

    fun findLayers(id: Long, conn: Connection) : List<String> {
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

    fun findAsync(id: Long) = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
            "SELECT * from charts WHERE id=?",
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setLong(1, id)
        }
        stmt.executeQuery().chart(findLayers(id, conn))
    }

    fun insertAsync(chartInsert: ChartInsert) = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
            """
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, dsid_props, chart_txt) 
                VALUES (?,?,?,?,?,?,?::json,?::json)
                """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setString(1, chartInsert.name)
            setInt(2, chartInsert.scale)
            setString(3, chartInsert.fileName)
            setString(4, chartInsert.updated)
            setString(5, chartInsert.issued)
            setInt(6, chartInsert.zoom)
            setObject(7, objectMapper.writeValueAsString(chartInsert.dsidProps))
            setObject(8, objectMapper.writeValueAsString(chartInsert.chartTxt))
        }

        stmt.executeUpdate().takeIf { it == 1 }?.let {
            stmt.generatedKeys.use { rs ->
                rs.chart(emptyList())
            }
        }
    }


    fun deleteAsync(id: Long) = sqlOpAsync { conn ->
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


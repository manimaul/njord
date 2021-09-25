package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.Singletons
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartInsert
import java.sql.*

class ChartDao : Dao() {

    private fun ResultSet.chart() = if (next()) {
        Chart(
            id = getLong(1),
            name = getString(2),
            scale = getInt(3),
            fileName = getString(4),
            updated = getString(5),
            issued = getString(6),
            zoom = getInt(7),
            dsidProps = Singletons.objectMapper.readValue(getString(8)),
            chartTxt = Singletons.objectMapper.readValue(getString(9))
        )
    } else {
        null
    }

    private fun ResultSet.charts() : Sequence<Chart> {
        return generateSequence {
            chart()
        }
    }

    fun findAsync(id: Long) = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
            "SELECT * from charts WHERE id=?",
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setLong(1, id)
        }
        stmt.executeQuery().chart()
    }

    fun insertAsync(chartInsert: ChartInsert) = sqlOpAsync { conn ->
        val stmt = conn.prepareStatement(
            "INSERT INTO charts (name, scale, file_name, updated, issued, zoom, dsid_props, chart_txt) VALUES (?,?,?,?,?,?,?::json,?::json)",
            Statement.RETURN_GENERATED_KEYS
        ).apply {
            setString(1, chartInsert.name)
            setInt(2, chartInsert.scale)
            setString(3, chartInsert.fileName)
            setString(4, chartInsert.updated)
            setString(5, chartInsert.issued)
            setInt(6, chartInsert.zoom)
            setObject(7, Singletons.objectMapper.writeValueAsString(chartInsert.dsidProps))
            setObject(8, Singletons.objectMapper.writeValueAsString(chartInsert.chartTxt))
        }

        stmt.executeUpdate().takeIf { it == 1 }?.let {
            stmt.generatedKeys.use { rs ->
                rs.chart()
            }
        }
    }


    fun deleteAsync(id: Long) = sqlOpAsync { conn ->
        conn.prepareStatement(
            "DELETE FROM charts WHERE id=?",
            Statement.NO_GENERATED_KEYS
        ).apply {
            setLong(1, id)
        }.executeUpdate() > 0
    }
}


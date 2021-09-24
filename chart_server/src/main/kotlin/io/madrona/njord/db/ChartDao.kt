package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.Singletons
import io.madrona.njord.logger
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartInsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource

class ChartDao : Dao() {

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
                if (rs.next()) {
                    Chart(
                        id = rs.getLong(1),
                        name = rs.getString(2),
                        scale = rs.getInt(3),
                        fileName = rs.getString(4),
                        updated = rs.getString(5),
                        issued = rs.getString(6),
                        zoom = rs.getInt(7),
                        dsidProps = Singletons.objectMapper.readValue(rs.getString(8)),
                        chartTxt = Singletons.objectMapper.readValue(rs.getString(9))
                    )
                } else {
                    null
                }
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


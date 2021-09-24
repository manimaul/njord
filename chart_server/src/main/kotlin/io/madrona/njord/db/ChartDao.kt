package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.Singletons
import io.madrona.njord.logger
import io.madrona.njord.model.Chart
import io.madrona.njord.model.ChartInsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource

class ChartDao(
    private val ds: DataSource = Singletons.ds,
    private val scope: CoroutineScope = Singletons.ioScope
) {
    val log = logger()

    fun insertAsync(chartInsert: ChartInsert) = scope.async {
        ds.connection.use {
            chartInsert.insert(it)
        }
    }

    private fun ChartInsert.insert(conn: Connection): Chart? {
        val stmt = conn.prepareStatement(
            """INSERT INTO charts (name, scale, file_name, updated, issued, zoom, dsid_props, chart_txt) VALUES (?,?,?,?,?,?,?::json,?::json)"""
            , Statement.RETURN_GENERATED_KEYS).apply {
            setString(1, name)
            setInt(2, scale)
            setString(3, fileName)
            setString(4, updated)
            setString(5, issued)
            setInt(6, zoom)
            setObject(7, Singletons.objectMapper.writeValueAsString(dsidProps))
            setObject(8, Singletons.objectMapper.writeValueAsString(chartTxt))
        }

        var chart: Chart? = null

        try {
            val affectedRows: Int = stmt.executeUpdate()
            if (affectedRows > 0) {
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) {
                        chart = Chart(
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
                    }
                }
            }
        } catch (e: SQLException) {
            log.error("error inserting chart", e)
        }
        return chart
    }
}


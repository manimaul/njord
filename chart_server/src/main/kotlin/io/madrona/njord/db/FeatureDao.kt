package io.madrona.njord.db

import com.fasterxml.jackson.module.kotlin.readValue
import io.madrona.njord.model.FeatureRecord
import io.madrona.njord.model.LayerQueryResult
import kotlinx.coroutines.Deferred
import org.locationtech.jts.io.WKTReader
import java.sql.ResultSet

class FeatureDao : Dao() {

    fun findLayerPositions(layer: String): Deferred<List<LayerQueryResult>?> = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT ST_AsText(ST_Centroid(geom)), props, charts.name, charts.zoom
                FROM features JOIN charts ON features.chart_id = charts.id WHERE layer = ?; 
            """.trimIndent()
        ).apply { setString(1, layer) }.executeQuery().let {
            sequence {
                while (it.next()) {
                    val wkt = it.getString(1)
                    val coord = WKTReader().read(wkt).coordinate
                    yield(
                        LayerQueryResult(
                            lat = coord.y,
                            lng = coord.x,
                            zoom = it.getFloat(4),
                            props = objectMapper.readValue(it.getString(2)),
                            chartName = it.getString(3)
                        )
                    )
                }
            }
        }.toList()
    }

    /**
     * Find feature using its LNAM .
     *
     * LNAM Long name.  An encoding of AGEN, FIDN and FIDS used to uniquely identify this features within an S-57 file.
     */
    fun findFeature(lnam: String): Deferred<FeatureRecord?> = sqlOpAsync { conn ->
        conn.prepareStatement(
            """ SELECT id, layer, ST_AsGeoJSON(geom)::JSON as geo, props, chart_id, lower(z_range), upper(z_range)
                FROM features WHERE props->'LNAM' = to_jsonb(?::text);""".trimIndent()
        ).apply {
            setString(1, lnam)
        }.executeQuery().featureRecord().firstOrNull()
    }

    private fun ResultSet.featureRecord(): Sequence<FeatureRecord> {
        return sequence {
            while (next()) {
                var i = 0
                yield(
                    FeatureRecord(
                        id = getLong(++i),
                        layer = getString(++i),
                        geom = objectMapper.readValue(getString(++i)),
                        props = objectMapper.readValue(getString(++i)),
                        chartId = getLong(++i),
                        zoomMax = getInt(++i),
                        zoomMin = getInt(++i),
                    )
                )
            }
        }
    }
}



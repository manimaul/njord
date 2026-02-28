package io.madrona.njord.db

import DataSource
import io.madrona.njord.Singletons
import io.madrona.njord.model.ChartFeature
import kotlinx.serialization.json.Json.Default.decodeFromString

class BaseFeatureDao(ds: DataSource = Singletons.ds) : Dao(ds) {

    suspend fun deleteByNameAndScaleAsync(name: String, scale: Int): Boolean? = sqlOpAsync { conn ->
        conn.prepareStatement(
            "DELETE FROM base_features WHERE name = \$1 AND scale = \$2;"
        ).let {
            it.setString(1, name)
            it.setInt(2, scale)
            it.execute() > 0
        }
    }

    suspend fun insertAsync(
        geomJson: String,
        propsJson: String,
        name: String,
        scale: Int,
        layer: String,
    ): Int? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
            INSERT INTO base_features (geom, props, name, scale, layer)
            VALUES (ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON($1), 4326)), $2::jsonb, $3, $4, $5);
            """.trimIndent()
        ).let {
            it.setString(1, geomJson)
            it.setString(2, propsJson)
            it.setString(3, name)
            it.setInt(4, scale)
            it.setString(5, layer)
            it.execute().toInt()
        }
    }

    suspend fun findFeaturesAsync(scale: Int, inclusionMaskWkb: ByteArray): List<ChartFeature>? =
        sqlOpAsync { conn ->
            conn.prepareStatement(
                """
                WITH include AS (VALUES (st_geomfromwkb($1, 4326)))
                SELECT st_asbinary(st_intersection(geom, (table include))),
                       props,
                       layer
                FROM base_features
                WHERE scale = $2
                  AND st_intersects(geom, (table include));
                """.trimIndent()
            ).let {
                it.setBytes(1, inclusionMaskWkb)
                it.setInt(2, scale)
                it.executeQuery().use { rs ->
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
            }
        }
}

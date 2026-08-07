package io.madrona.njord.db

import Connection
import DataSource
import ResultSet
import io.madrona.njord.Singletons
import io.madrona.njord.ext.jsonStr
import io.madrona.njord.geojson.FeatureBuilder
import io.madrona.njord.model.*
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.JsonPrimitive

class ChartDao(
    ds: DataSource = Singletons.ds,
    private val featureDao: FeatureDao = Singletons.featureDao,
) : Dao(ds) {

    private fun ResultSet.chart(layers: List<String>, featureCount: Int) = generateSequence {
        if (next()) {
            var i = 0
            Chart(
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

    /**
     * For each TOPMAR feature in [features], looks up the distinct layer names of all features
     * that reference its LNAM via lnam_refs. Uses a single &&-based GIN scan for the whole batch.
     * Returns a map of LNAM → associated layer names; empty if no TOPMAR features are present.
     */
    private fun topmarAssocByLnam(conn: Connection, features: List<ChartFeature>): Map<String, List<String>> {
        val lnams = features
            .filter { it.layer == "TOPMAR" }
            .mapNotNull { (it.props["LNAM"] as? JsonPrimitive)?.content }
            .distinct()
        if (lnams.isEmpty()) return emptyMap()
        return conn.prepareStatement(
            """
WITH input_lnams AS (SELECT unnest($1::varchar[]) AS lnam)
SELECT il.lnam, array_agg(DISTINCT f.layer)
FROM input_lnams il
JOIN features f ON f.lnam_refs @> ARRAY[il.lnam]
GROUP BY il.lnam;
            """.trimIndent()
        ).apply {
            setArray(1, lnams.map { it as Any }.toTypedArray())
        }.executeQuery().use { rs ->
            val result = mutableMapOf<String, List<String>>()
            while (rs.next()) result[rs.getString(1)] = rs.getArray(2).filterNotNull()
            result
        }
    }

    private fun ChartFeature.withAssoc(assocByLnam: Map<String, List<String>>): ChartFeature {
        if (layer != "TOPMAR") return this
        val lnam = (props["LNAM"] as? JsonPrimitive)?.content ?: return this
        return ChartFeature(
            geomWKB = geomWKB,
            props = props,
            layer = layer,
            associatedLayerNames = assocByLnam[lnam] ?: emptyList(),
        )
    }

    private fun findLayers(name: String, conn: Connection): List<String> {
        return conn.prepareStatement(
            "SELECT DISTINCT layer FROM features where chart_name=$1;"
        ).let {
            it.setString(1, name)
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
        inclusionMask: ByteArray,
        chartName: String,
        zoom: Int,
    ): List<ChartFeature>? =
        sqlOpAsync { conn ->
            val features = conn.prepareStatement(
                """
WITH include AS (VALUES (st_geomfromwkb($1, 4326)))
SELECT st_asbinary(
    CASE WHEN ST_NRings((table include)) = 1
         THEN ST_ClipByBox2D(f.geom, (table include)::box2d)
         ELSE ST_Intersection(f.geom, (table include))
    END
), f.props, f.layer
FROM features f
WHERE f.chart_name = $2
  AND $3 >= f.z_min AND $4 <= f.z_max
  AND st_intersects(f.geom, (table include));
          """.trimIndent()
            ).apply {
                setBytes(1, inclusionMask)
                setString(2, chartName)
                setInt(3, zoom)
                setInt(4, zoom)
            }.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) ChartFeature(
                        geomWKB = rs.getBytes(1),
                        props = decodeFromString(rs.getString(2)),
                        layer = rs.getString(3),
                        associatedLayerNames = emptyList(),
                    ) else null
                }.toList()
            }
            val assocByLnam = topmarAssocByLnam(conn, features)
            if (assocByLnam.isEmpty()) features else features.map { it.withAssoc(assocByLnam) }
        }

    suspend fun findAllChartFeaturesAsync4326(
        tileWkb: ByteArray,
        chartNames: List<String>,
        zoom: Int,
    ): Map<String, List<ChartFeature>>? {
        if (chartNames.isEmpty()) return emptyMap()
        return sqlOpAsync { conn ->
            val features = conn.prepareStatement(
                """
WITH tile AS (VALUES (st_geomfromwkb($1, 4326)))
SELECT st_asbinary(
    ST_ClipByBox2D(f.geom, (table tile)::box2d)
), f.props, f.layer, f.chart_name
FROM features f
WHERE f.chart_name = ANY($2)
  AND $3 >= f.z_min AND $4 <= f.z_max
  AND st_intersects(f.geom, (table tile))
ORDER BY f.chart_name;
                """.trimIndent()
            ).apply {
                setBytes(1, tileWkb)
                setArray(2, chartNames.map { it as Any }.toTypedArray())
                setInt(3, zoom)
                setInt(4, zoom)
            }.executeQuery().use { rs ->
                val result = mutableMapOf<String, MutableList<ChartFeature>>()
                while (rs.next()) {
                    val chartName = rs.getString(4)
                    result.getOrPut(chartName) { mutableListOf() }.add(
                        ChartFeature(
                            geomWKB = rs.getBytes(1),
                            props = decodeFromString(rs.getString(2)),
                            layer = rs.getString(3),
                            associatedLayerNames = emptyList(),
                        )
                    )
                }
                result
            }
            val allFeatures = features.values.flatten()
            val assocByLnam = topmarAssocByLnam(conn, allFeatures)
            if (assocByLnam.isEmpty()) features else features.mapValues { (_, list) ->
                list.map { it.withAssoc(assocByLnam) }
            }
        }
    }

    suspend fun findInfoAsync(wkb: ByteArray): List<ChartInfo>? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """
                SELECT
                    name,
                    scale,
                    zoom,
                    st_asbinary(covr) as wkb
                FROM charts
                WHERE st_intersects(st_geomfromwkb($1, 4326), covr)
                ORDER BY scale ASC;
            """.trimIndent()
        ).let {
            it.setBytes(1, wkb)
            it.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) {
                        ChartInfo(
                            name = rs.getString(1),
                            scale = rs.getInt(2),
                            zoom = rs.getInt(3),
                            covrWKB = rs.getBytes(4),
                        )
                    } else null
                }.toList()
            }
        }
    }

    suspend fun findAsync(name: String): Chart? = sqlOpAsync { conn ->
        find(name, conn)
    }

    private fun find(name: String, conn: Connection): Chart? {
        val layers = findLayers(name, conn)
        val count = featureDao.featureCount(conn, name)
        return conn.prepareStatement(
            """
            SELECT
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
            WHERE name=$1;
            """.trimIndent()
        ).let { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { result ->
                result.chart(layers, count).firstOrNull()
            }
        }
    }

    private fun chartCount(conn: Connection): Int {
        return conn.prepareStatement("SELECT COUNT(name) FROM charts;").executeQuery().use {
            if (it.next()) it.getInt(1) else 0
        }
    }

    /**
     * One page of the catalog, ordered by the primary key. [nextPageName] is the name the previous
     * page reported as its successor; null (or blank) starts from the beginning - `''` sorts before
     * every chart name, so it works as the start sentinel the way `0` did for the old row ids.
     */
    suspend fun listAsync(nextPageName: String? = null): ChartCatalog? = sqlOpAsync { conn ->
        val totalCount = chartCount(conn)
        conn.prepareStatement(
            """SELECT name FROM charts WHERE name >= $1 ORDER BY name LIMIT ${PAGE_SIZE + 1};
            """.trimIndent()
        ).let {
            it.setString(1, nextPageName ?: "")
            it.executeQuery().use {
                val page = mutableListOf<ChartItem>()
                var num = 0
                var nextName: String? = null
                while (it.next() && num <= PAGE_SIZE) {
                    val name = it.getString(1)
                    if (++num > PAGE_SIZE) {
                        nextName = name
                    } else {
                        page.add(ChartItem(name = name))
                    }
                }
                ChartCatalog(
                    totalChartCount = totalCount,
                    nextName = nextName,
                    page = page
                )
            }
        }
    }

    /**
     * Every chart's revision key as `"<UPDN>:<UADT>:<ISDT>"`, keyed by chart name.
     *
     * `updated`/`issued` are dedicated columns (DSID_UADT / DSID_ISDT); the update number only
     * exists inside the `dsid_props` JSONB blob written by `OgrS57Dataset.chartInsertInfo()`.
     *
     * Charts missing any of the three are omitted rather than reported with a partial key, so
     * enc_cron sees them as absent and re-fetches - the safe direction to fail.
     *
     * See [ChartEditions] for why `DSID_EDTN` is not part of the key.
     *
     * Unpaginated on purpose: the whole NOAA catalog is ~7k cells, roughly 200 KB of JSON.
     */
    suspend fun editionsAsync(): ChartEditions? = sqlOpAsync { conn ->
        conn.prepareStatement(
            """SELECT name, dsid_props->>'DSID_UPDN', updated, issued FROM charts;"""
        ).executeQuery().use { rs ->
            val editions = mutableMapOf<String, String>()
            while (rs.next()) {
                val name = rs.getString(1)
                val updn = rs.getString(2)
                val updated = rs.getString(3)
                val issued = rs.getString(4)
                // getString maps SQL NULL to "", so blank means the field was absent.
                if (name.isNotBlank() && updn.isNotBlank() && updated.isNotBlank() && issued.isNotBlank()) {
                    editions[name] = "$updn:$updated:$issued"
                }
            }
            ChartEditions(editions)
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
                INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt)
                VALUES ($1, $2, $3, $4, $5, $6, st_setsrid(st_geomfromgeojson($7), 4326), $8::json, $9::json)
                RETURNING name, scale, file_name, updated, issued, zoom, st_asgeojson(covr)::JSON, st_asbinary(covr), dsid_props, chart_txt""".trimIndent()
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
            stmt.executeReturning().use { result ->
                result.chart(layers = emptyList(), 0).firstOrNull()
            }
        }
    }

    private fun delete(name: String, conn: Connection): Boolean {
        conn.prepareStatement(
            "DELETE FROM features WHERE chart_name=\$1;"
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

    /**
     * Deletes a chart and its features by S-57 `DSID_DSNM` - the same key [editionsAsync] reports
     * and enc_cron diffs against NOAA's catalog.
     *
     * False means no chart carried that name; null means the statement failed.
     */
    suspend fun deleteByNameAsync(name: String): Boolean? = sqlOpAsync { conn ->
        delete(name, conn)
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}

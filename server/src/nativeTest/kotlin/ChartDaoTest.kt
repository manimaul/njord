import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.geojson.FeatureBuilder
import io.madrona.njord.model.ChartInsert
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.PortDivergence
import io.madrona.njord.testdata.fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P-DB-2 / P-DB-3 / P-DB-4 / P-DB-5 on [ChartDao] and [FeatureDao]: chart overwrite and delete
 * semantics, the TOPMAR association lookups, and the catalog paging / editions key as shared
 * fixtures (lifted from ChartCatalogTest and ChartEditionsTest, which keep the boundary assertions).
 *
 * The D5 case - an overwrite whose insert fails - is recorded with the Kotlin outcome as
 * `expected` and the port's required outcome under `port`, since the Kotlin delete runs outside
 * the insert's transaction and is retried, so the previous chart is gone either way.
 */
class ChartDaoTest {

    private lateinit var featureDao: FeatureDao
    private lateinit var chartDao: ChartDao
    private val prefix = "f2_chartdao_"

    @BeforeTest
    fun before() {
        TestEnv.init()
        Gdal.initialize()
        featureDao = FeatureDao(TestEnv.ds)
        chartDao = ChartDao(TestEnv.ds, featureDao)
        runTest { cleanup() }
    }

    @AfterTest
    fun after() = runTest { cleanup() }

    private suspend fun cleanup() {
        TestEnv.ds.connection()!!.use { conn ->
            conn.statement("DELETE FROM features WHERE chart_name LIKE '$prefix%';").execute()
            conn.statement("DELETE FROM charts WHERE name LIKE '$prefix%';").execute()
        }
    }

    private fun insert(name: String, scale: Int = 10000, geometryJson: String? = """{"type":"Polygon","coordinates":[[[-1,-1],[-1,1],[1,1],[1,-1],[-1,-1]]]}""") =
        ChartInsert(
            name = name,
            scale = scale,
            fileName = name,
            updated = "20240101",
            issued = "20240101",
            zoom = 14,
            covr = FeatureBuilder(geometryJson = geometryJson).build(),
            dsidProps = mapOf("DSID_UPDN" to JsonPrimitive("1")),
            chartTxt = emptyMap(),
        )

    private suspend fun addFeature(chartName: String, layer: String, lnam: String, refs: List<String> = emptyList(), wkt: String = "POINT(0 0)") {
        val chart = chartDao.findAsync(chartName) ?: error("no chart $chartName")
        val props = buildJsonObject {
            put("LNAM", lnam)
            if (refs.isNotEmpty()) put("LNAM_REFS", JsonArray(refs.map { JsonPrimitive(it) }))
        }
        featureDao.insertFeature(layer, chart, OgrGeometry.fromWkt4326(wkt)!!.wkb, props)
    }

    private suspend fun featureCount(name: String) = TestEnv.ds.connection()!!.use { featureDao.featureCount(it, name) }

    @Test
    fun `DB-2 overwrite replaces the chart row and deletes its old features`() = runTest {
        val name = "${prefix}a.000"
        assertNotNull(chartDao.insertAsync(insert(name), overwrite = false))
        addFeature(name, "BOYLAT", "${prefix}L1")
        addFeature(name, "LIGHTS", "${prefix}L2")
        assertEquals(2, featureCount(name))

        val replaced = assertNotNull(chartDao.insertAsync(insert(name, scale = 25000), overwrite = true))
        assertEquals(25000, replaced.scale)
        assertEquals(25000, chartDao.findAsync(name)?.scale)
        assertEquals(0, featureCount(name), "features of the previous edition are gone")
        assertEquals(1, countCharts(name))
    }

    @Test
    fun `DB-3 delete by name cascades to features and reports whether a chart existed`() = runTest {
        val name = "${prefix}b.000"
        chartDao.insertAsync(insert(name), overwrite = false)
        addFeature(name, "BOYLAT", "${prefix}L3")

        assertEquals(true, chartDao.deleteByNameAsync(name))
        assertNull(chartDao.findAsync(name))
        assertEquals(0, featureCount(name))
        assertEquals(false, chartDao.deleteByNameAsync(name), "unknown name -> false, not null")
        assertEquals(false, chartDao.deleteByNameAsync("${prefix}never-existed.000"))
    }

    @Serializable
    data class Outcome(val insertReturned: Boolean, val previousChartSurvives: Boolean, val previousFeaturesSurvive: Boolean)

    /**
     * D5: a failing overwrite insert. `insertAsync(overwrite = true)` deletes features and chart
     * first, then inserts, all outside a transaction and wrapped in a retry - so when the insert
     * fails (here a NULL coverage violating `covr NOT NULL`) the previous chart is already gone.
     * The port runs the three statements in one transaction and keeps the previous chart.
     */
    @Test
    fun `D5 a failing overwrite loses the previous chart in Kotlin and must keep it in the port`() = runTest {
        val name = "${prefix}c.000"
        chartDao.insertAsync(insert(name), overwrite = false)
        addFeature(name, "BOYLAT", "${prefix}L4")

        val result = chartDao.insertAsync(insert(name, geometryJson = null), overwrite = true)
        val outcome = Outcome(
            insertReturned = result != null,
            previousChartSurvives = chartDao.findAsync(name) != null,
            previousFeaturesSurvive = featureCount(name) > 0,
        )
        fixtures.table(
            "db/overwrite.json",
            FixtureHeader(
                pins = "ChartDao.insertAsync(overwrite = true) when the insert itself fails: Kotlin deletes outside the insert's transaction (and retries), so the previous chart and features are lost; the port does delete + insert in one transaction and keeps them",
                decisions = listOf("D5", "DB-2"),
                generator = "ChartDaoTest.D5",
            ),
            listOf("overwrite with a chart insert that violates covr NOT NULL"),
            port = { PortDivergence("D5", Outcome(insertReturned = false, previousChartSurvives = true, previousFeaturesSurvive = true)) },
        ) { outcome }
        assertEquals(Outcome(insertReturned = false, previousChartSurvives = false, previousFeaturesSurvive = false), outcome)
    }

    /**
     * P-DB-4: TOPMAR association. US5WA22M carries no TOPMAR objects (its topmarks ride on the buoys
     * and beacons as attributes), so the association itself is exercised on a synthetic chart: three
     * topmarks, two of them referenced through `LNAM_REFS` by a buoy and a beacon. Both lookups the
     * server uses - the batched GIN scan in ChartDao (observed as `associatedLayerNames`) and
     * FeatureDao.findAssociatedLayerNames - must agree, non-TOPMAR features are left untouched, and
     * the layer page adds `_PTFM`/`_ASOC`. The real cell pins the GIN lookup over every LNAM its
     * features reference (`db/lnam_refs.json`).
     */
    @Test
    fun `P-DB-4 TOPMAR association resolves the platform layers through lnam_refs`() = runTest {
        val name = "${prefix}topmar.000"
        chartDao.insertAsync(insert(name), overwrite = true)
        addFeature(name, "BOYLAT", "${prefix}B1", refs = listOf("${prefix}T1"), wkt = "POINT(0.1 0.1)")
        addFeature(name, "BCNLAT", "${prefix}N1", refs = listOf("${prefix}T2", "${prefix}T1"), wkt = "POINT(0.2 0.2)")
        addFeature(name, "LIGHTS", "${prefix}L1", refs = listOf("${prefix}T1"), wkt = "POINT(0.1 0.1)")
        addFeature(name, "TOPMAR", "${prefix}T1", wkt = "POINT(0.1 0.1)")
        addFeature(name, "TOPMAR", "${prefix}T2", wkt = "POINT(0.2 0.2)")
        addFeature(name, "TOPMAR", "${prefix}T3", wkt = "POINT(0.3 0.3)")

        assertEquals(listOf("BCNLAT", "BOYLAT", "LIGHTS"), featureDao.findAssociatedLayerNames("${prefix}T1").sorted())
        assertEquals(listOf("BCNLAT"), featureDao.findAssociatedLayerNames("${prefix}T2"))
        assertEquals(emptyList(), featureDao.findAssociatedLayerNames("${prefix}T3"))

        val include = OgrGeometry.fromWkt4326("POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))")!!.wkb
        listOf(
            chartDao.findChartFeaturesAsync4326(include, name, 14)!!,
            chartDao.findAllChartFeaturesAsync4326(include, listOf(name), 14)!![name]!!,
        ).forEach { features ->
            val byLnam = features.associate { (it.props["LNAM"] as JsonPrimitive).content to (it.layer to it.associatedLayerNames.sorted()) }
            assertEquals("TOPMAR" to listOf("BCNLAT", "BOYLAT", "LIGHTS"), byLnam["${prefix}T1"])
            assertEquals("TOPMAR" to listOf("BCNLAT"), byLnam["${prefix}T2"])
            assertEquals("TOPMAR" to emptyList(), byLnam["${prefix}T3"])
            assertTrue(features.filter { it.layer != "TOPMAR" }.all { it.associatedLayerNames.isEmpty() }, "non-TOPMAR features untouched")
        }

        val page = featureDao.findLayerPositionsPage("TOPMAR", 0L)!!
        val t1 = page.items.first { (it.props["LNAM"] as JsonPrimitive).content == "${prefix}T1" }
        assertEquals("FLOATING", (t1.props["_PTFM"] as JsonPrimitive).content)
        assertEquals(listOf("BCNLAT", "BOYLAT", "LIGHTS"), (t1.props["_ASOC"] as JsonArray).map { (it as JsonPrimitive).content }.sorted())
        val t3 = page.items.first { (it.props["LNAM"] as JsonPrimitive).content == "${prefix}T3" }
        assertEquals("RIGID", (t3.props["_PTFM"] as JsonPrimitive).content)
    }

    @Test
    fun `P-DB-4 lnam_refs lookup over every LNAM the real cell references`() = runTest {
        TestCharts.ensureSeeded()
        val referenced = TestEnv.ds.connection()!!.use { conn ->
            conn.prepareStatement("SELECT DISTINCT unnest(lnam_refs) FROM features WHERE chart_name = $1 ORDER BY 1;")
                .apply { setString(1, TestCharts.WA) }.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
        assertTrue(referenced.size >= 20, "US5WA22M buoys and beacons reference ${referenced.size} LNAMs")
        val table = fixtures.table(
            "db/lnam_refs.json",
            FixtureHeader(
                pins = "FeatureDao.findAssociatedLayerNames(LNAM) for every LNAM referenced by a US5WA22M feature's lnam_refs: the distinct layers of the referencing features, sorted (the GIN @> lookup behind the TOPMAR association)",
                decisions = listOf("DB-5"),
                generator = "ChartDaoTest.P-DB-4",
                source = "fixtures/US5WA22M (edition 40 update 4); GDAL ${Gdal.gdalVersion()}",
            ),
            referenced,
        ) { runBlocking { featureDao.findAssociatedLayerNames(it).sorted() } }
        assertTrue(table.cases.all { (it.expected as JsonArray).isNotEmpty() })
    }

    @Serializable
    data class EditionInput(val dsidProps: JsonObject, val updated: String, val issued: String)

    /** P-DB-5: the editions key enc_cron diffs against, as a shared table (see ChartEditionsTest for the why). */
    @Test
    fun `P-DB-5 editions key fixture`() = runTest {
        val inputs = listOf(
            EditionInput(JsonObject(mapOf("DSID_UPDN" to JsonPrimitive("6"))), "20251209", "20260520"),
            EditionInput(JsonObject(mapOf("DSID_EDTN" to JsonPrimitive("0"), "DSID_UPDN" to JsonPrimitive("6"))), "20251209", "20260520"),
            EditionInput(JsonObject(mapOf("DSID_UPDN" to JsonPrimitive("0"))), "20251209", "20251209"),
            EditionInput(JsonObject(mapOf("DSID_EDTN" to JsonPrimitive("44"))), "20251209", "20260520"),
            EditionInput(JsonObject(mapOf("DSID_UPDN" to JsonPrimitive("2"))), "", "20260520"),
            EditionInput(JsonObject(mapOf("DSID_UPDN" to JsonPrimitive("2"))), "20251209", ""),
            EditionInput(JsonObject(mapOf("DSID_UPDN" to JsonPrimitive(6))), "20251209", "20260520"),
        )
        fixtures.table(
            "db/editions.json",
            FixtureHeader(
                pins = "ChartDao.editionsAsync key per chart: \"<dsid_props.DSID_UPDN>:<updated>:<issued>\"; charts missing any part are omitted (null); DSID_EDTN is never part of the key; a numeric UPDN reads the same as a string",
                decisions = listOf("API-1"),
                generator = "ChartDaoTest.P-DB-5",
            ),
            inputs,
        ) { input ->
            runBlocking {
                val name = "${prefix}ed_${inputs.indexOf(input)}.000"
                chartDao.insertAsync(insert(name).copy(dsidProps = input.dsidProps, updated = input.updated, issued = input.issued), overwrite = true)
                chartDao.editionsAsync()!!.editions[name]
            }
        }
    }

    @Serializable
    data class PagingInput(val cursor: String?, val chartsBefore: Int)

    @Serializable
    data class PagingResult(val pageSize: Int, val first: String?, val last: String?, val nextName: String?)

    /**
     * P-DB-5: catalog paging. Names use a `zz_` prefix so they sort after real cells and the
     * page starting at the first fixture name holds only fixture names; the cursor is the
     * (PAGE_SIZE + 1)th row, served as the first row of the next page.
     */
    @Test
    fun `P-DB-5 catalog paging fixture`() = runTest {
        val p = "zz_${prefix}"
        fun nameAt(i: Int) = "$p${i.toString().padStart(4, '0')}.000"
        val total = ChartDao.PAGE_SIZE + 5
        TestEnv.ds.connection()!!.use { conn -> conn.statement("DELETE FROM charts WHERE name LIKE '$p%';").execute() }
        TestEnv.ds.connection()!!.use { conn ->
            val values = (1..total).joinToString(",\n") { i ->
                "('${nameAt(i)}', 1, '${nameAt(i)}', '20240101', '20240101', 1, ST_GeomFromText('POLYGON((-1 -1, -1 1, 1 1, 1 -1, -1 -1))', 4326), '{}', '{}')"
            }
            conn.statement("INSERT INTO charts (name, scale, file_name, updated, issued, zoom, covr, dsid_props, chart_txt) VALUES $values;").execute()
        }
        try {
            fixtures.table(
                "db/catalog_paging.json",
                FixtureHeader(
                    pins = "ChartDao.listAsync(cursor) over ${total} charts named zz_…0001-0${total}: name >= cursor ORDER BY name LIMIT PAGE_SIZE + 1, the extra row is nextName and the first row of the next page; null nextName on the last page; totalChartCount is not pinned (it counts the whole table)",
                    decisions = listOf("API-1"),
                    generator = "ChartDaoTest.P-DB-5",
                ),
                listOf(PagingInput(nameAt(1), total), PagingInput(nameAt(ChartDao.PAGE_SIZE + 1), total), PagingInput(nameAt(total), total), PagingInput(nameAt(total) + "~", total)),
            ) { input ->
                runBlocking {
                    val page = chartDao.listAsync(input.cursor)!!
                    PagingResult(page.page.size, page.page.firstOrNull()?.name, page.page.lastOrNull()?.name, page.nextName)
                }
            }
        } finally {
            TestEnv.ds.connection()!!.use { conn -> conn.statement("DELETE FROM charts WHERE name LIKE '$p%';").execute() }
        }
    }

    private suspend fun countCharts(name: String): Int = TestEnv.ds.connection()!!.use { conn ->
        conn.prepareStatement("SELECT count(*) FROM charts WHERE name = $1;").apply { setString(1, name) }
            .executeQuery().use { if (it.next()) it.getInt(1) else 0 }
    }
}

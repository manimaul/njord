import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P-TE-5, the golden tiles: `/v1/tile/{z}/{x}/{y}` decoded, over the three fixture cells and the
 * Natural Earth coastline extract, one fixture file per tile under `geo/tiles/`. This is the
 * single most important fixture of the port - the whole `TileEncoder.addCharts` algorithm is in
 * here: chart priority masking (TE-1), zoom eligibility (TE-2), LIGHTS left unclipped (TE-4), the
 * PLY boundary for every intersecting chart (TE-5), base map fill where no chart covers (TE-6)
 * and `?info=true` (TE-7).
 *
 * Tile coordinates were derived once with `TileSystem.latLngToTileXy` and are listed explicitly
 * so the fixture set is stable. `US5WA22M` is zoom 14, `US4ZZ01M` 11, `US5ZZ02M` 14.
 */
class GoldenTilesTest {

    @BeforeTest
    fun before() {
        TestCharts.ensureSeeded()
    }

    private fun header(pins: String, decisions: List<String>, generator: String) = FixtureHeader(
        pins = pins,
        decisions = decisions,
        generator = "GoldenTilesTest.$generator",
        source = goldenSource(),
    )

    companion object {
        /**
         * Base map tiles: the 110m coastline at z0-2 (z3-4 ask for the 50m set, which is not loaded). A
         * world-sized tile intersects every chart coverage, so the PLY boundary lines ride along except
         * over Europe (2/2/1), the one tile here with no chart at all.
         */
        val baseMapOnly = listOf(TileXyz(0, 0, 0), TileXyz(1, 0, 0), TileXyz(2, 0, 1), TileXyz(2, 2, 1), TileXyz(3, 1, 2))

        /** US5WA22M intersects but its zoom (14) exceeds z: PLY boundary only, no content. */
        val waNotEligible = listOf(
            TileXyz(4, 2, 5), TileXyz(6, 10, 22), TileXyz(8, 40, 89), TileXyz(10, 163, 359),
            TileXyz(12, 654, 1436), TileXyz(13, 1309, 2872),
        )

        /** US5WA22M eligible, across its coverage. */
        val waEligible = listOf(
            TileXyz(14, 2619, 5744), TileXyz(14, 2615, 5746), TileXyz(14, 2623, 5741), TileXyz(14, 2616, 5742),
            TileXyz(14, 2621, 5745), TileXyz(14, 2619, 5741),
            TileXyz(15, 5238, 11488), TileXyz(15, 5230, 11493), TileXyz(15, 5246, 11482), TileXyz(15, 5233, 11485),
            TileXyz(15, 5242, 11490), TileXyz(15, 5238, 11482),
            TileXyz(16, 10476, 22976), TileXyz(16, 10460, 22986), TileXyz(16, 10493, 22965), TileXyz(16, 10467, 22970),
            TileXyz(16, 10485, 22981), TileXyz(16, 10476, 22965),
        )

        /** West of US5WA22M at z14: no chart, and no base map data at that scale - an empty tile. */
        val waOutside = TileXyz(14, 2612, 5744)

        /** The synthetic pair: overlap centre, the outer cell's west BOYLAT, its east LIGHTS. */
        val synOverlap = listOf(
            TileXyz(10, 162, 360), TileXyz(11, 324, 720), TileXyz(12, 648, 1440), TileXyz(13, 1297, 2881),
            TileXyz(14, 2594, 5762), TileXyz(14, 2593, 5762), TileXyz(15, 5188, 11525), TileXyz(15, 5189, 11524),
        )
        val synWest = listOf(TileXyz(11, 323, 719), TileXyz(12, 646, 1439), TileXyz(14, 2587, 5759), TileXyz(15, 5174, 11518))
        val synEast = listOf(TileXyz(12, 650, 1441), TileXyz(14, 2600, 5766))

        val all = baseMapOnly + waNotEligible + waEligible + waOutside + synOverlap + synWest + synEast

        val infoTiles = listOf(TileXyz(14, 2619, 5744), TileXyz(14, 2594, 5762))
    }

    private suspend fun golden(t: TileXyz): JsonObject {
        val table = fixtures.table(
            "geo/tiles/${t.z}_${t.x}_${t.y}.json",
            header(
                pins = "decoded /v1/tile/${t.z}/${t.x}/${t.y}: layers sorted by name, features sorted by LNAM, props, geometry; feature ids dropped",
                decisions = listOf("TE-1", "TE-2", "TE-4", "TE-5", "TE-6"),
                generator = "golden tiles",
            ),
            listOf(t),
        ) { runBlocking { encodeCanonicalTile(it) } }
        return table.cases.single().expected.jsonObject
    }

    @Test
    fun `every golden tile matches the committed decode`() = runTest {
        all.forEach { golden(it) }
    }

    @Test
    fun `TE-7 info mode returns WGS84 GeoJSON per chart feature and skips the base map`() = runTest {
        infoTiles.forEach { t ->
            val table = fixtures.table(
                "geo/tiles/${t.z}_${t.x}_${t.y}_info.json",
                header(
                    pins = "/v1/tile/${t.z}/${t.x}/${t.y}?info=true: ChartFeatureInfo list (layer, props after preTileEncode, WGS84 geometry) sorted by layer, LNAM, props, geometry; no base map, no PLY",
                    decisions = listOf("TE-7"),
                    generator = "info tiles",
                ),
                listOf(t),
            ) { runBlocking { encodeCanonicalInfo(it) } }
            val info = table.cases.single().expected.jsonArray
            assertTrue(info.isNotEmpty(), "$t")
            assertFalse(info.any { it.jsonObject["layer"]!!.jsonPrimitive.content == "PLY" })
            info.forEach { e ->
                val coords = e.jsonObject["geom"]!!.jsonObject["coordinates"].toString()
                assertTrue(coords.contains("-122.") , "$t geometry must be WGS84, got $coords")
            }
        }
    }

    @Test
    fun `TE-6 where no eligible chart covers the tile only base map features render`() = runTest {
        baseMapOnly.forEach { t ->
            val tile = golden(t)
            assertTrue(tile.layerNames().all { it == "PLY" || it == "coastline" }, "$t: ${tile.layerNames()}")
            assertEquals(t.z < 3, "coastline" in tile.layerNames(), "$t: the 110m coastline is the only loaded base set")
        }
        assertEquals(listOf("coastline"), golden(TileXyz(2, 2, 1)).layerNames(), "Europe: no chart at all")
        assertEquals(emptyList(), golden(waOutside).layerNames(), "no chart and no base data at z14")
    }

    /**
     * The PLY layer is the coverage *boundary* line, so it appears only on tiles that boundary
     * crosses; a tile wholly inside a coverage has none. Either way it is never content.
     */
    @Test
    fun `TE-2 TE-5 a chart whose zoom exceeds z contributes at most its PLY boundary`() = runTest {
        waNotEligible.forEach { t ->
            val tile = golden(t)
            assertTrue(tile.layerNames().all { it == "PLY" || it == "coastline" }, "$t: ${tile.layerNames()}")
            val encoder = testTileEncoder(t)
            encoder.addCharts(false)
            assertFalse(encoder.hasContent(), "$t: PLY alone is not content")
        }
        assertTrue("PLY" in golden(TileXyz(12, 654, 1436)).layerNames(), "the boundary crosses this z12 tile")
        assertEquals(emptyList(), golden(TileXyz(13, 1309, 2872)).layerNames(), "this z13 tile is wholly inside the coverage")
    }

    @Test
    fun `eligible US5WA22M tiles carry chart features named after the chart`() = runTest {
        waEligible.forEach { t ->
            val tile = golden(t)
            val chartLayers = tile.layerNames().filter { it != "PLY" }
            assertTrue(chartLayers.isNotEmpty(), "$t")
            chartLayers.forEach { layer ->
                tile.features(layer).forEach { f ->
                    assertEquals(TestCharts.WA, f.prop("NAME")?.jsonPrimitive?.content, "$t $layer")
                }
            }
            val encoder = testTileEncoder(t)
            encoder.addCharts(false)
            assertTrue(encoder.hasContent(), "$t")
        }
    }

    /**
     * TE-1: inside the overlap the harbour cell (zoom 14) masks the approach cell (zoom 11) once
     * both are eligible, while below z14 only the approach cell is eligible and its BOYLAT in the
     * overlap shows. Outside the overlap the approach cell's features always show.
     */
    @Test
    fun `TE-1 the larger-scale chart masks the smaller-scale one inside the overlap`() = runTest {
        val outerOnly = synOverlap.filter { it.z < 14 }
        outerOnly.forEach { t ->
            val tile = golden(t)
            if (t.z < 11) {
                assertEquals(listOf("PLY"), tile.layerNames(), "$t: neither cell eligible below z11")
            } else {
                assertEquals(listOf(TestCharts.OUTER), tile.features("BOYLAT").map { it.prop("NAME")!!.jsonPrimitive.content }, "$t")
                assertTrue(tile.features("BOYSPP").isEmpty(), "$t: harbour cell not eligible below z14")
                assertTrue(tile.features("DEPARE").all { it.prop("NAME")!!.jsonPrimitive.content == TestCharts.OUTER }, "$t")
            }
        }
        synOverlap.filter { it.z >= 14 }.forEach { t ->
            val tile = golden(t)
            val names = tile.layerNames().filter { it != "PLY" }.flatMap { l -> tile.features(l).map { it.prop("NAME")!!.jsonPrimitive.content } }.toSet()
            assertTrue(TestCharts.INNER in names, "$t: harbour cell features present")
            assertTrue(tile.features("BOYLAT").none { it.prop("NAME")!!.jsonPrimitive.content == TestCharts.OUTER }, "$t: the outer BOYLAT at (-123, 47) is masked")
            assertEquals(listOf(TestCharts.INNER), tile.features("DEPARE").map { it.prop("NAME")!!.jsonPrimitive.content }, "$t: the outer DEPARE is masked out entirely")
            if (t == TileXyz(14, 2594, 5762)) {
                assertEquals(listOf(TestCharts.INNER), tile.features("BOYSPP").map { it.prop("NAME")!!.jsonPrimitive.content })
            }
            if (t == TileXyz(14, 2593, 5762)) {
                assertEquals(listOf(TestCharts.INNER), tile.features("SOUNDG").map { it.prop("NAME")!!.jsonPrimitive.content }, "the 3.4 m sounding sits in this tile")
                assertEquals(3.4, tile.features("SOUNDG").single().prop("METERS")!!.jsonPrimitive.content.toDouble())
            }
        }
        synWest.forEach { t ->
            val tile = golden(t)
            assertEquals(listOf(TestCharts.OUTER), tile.features("BOYLAT").map { it.prop("NAME")!!.jsonPrimitive.content }, "$t: west BOYLAT is outside the overlap")
        }
        synEast.forEach { t ->
            val tile = golden(t)
            assertEquals(listOf(TestCharts.OUTER), tile.features("LIGHTS").map { it.prop("NAME")!!.jsonPrimitive.content }, "$t: the outer LIGHTS is outside the overlap")
        }
    }

    /** TE-4: LIGHTS geometry is not intersected with the include mask, so a light keeps its full geometry and symbol. */
    @Test
    fun `TE-4 LIGHTS features reach the tile with their symbol props`() = runTest {
        val withLights = waEligible.map { it to golden(it) }.filter { (_, tile) -> tile.features("LIGHTS").isNotEmpty() }
        assertTrue(withLights.isNotEmpty(), "US5WA22M has lights; at least one z14-16 tile must carry one")
        withLights.forEach { (t, tile) ->
            tile.features("LIGHTS").forEach { f ->
                assertTrue(f.prop("SY") != null, "$t: every light gets a symbol")
                assertEquals("POINT", f["type"]!!.jsonPrimitive.content)
            }
        }
    }

    @Test
    fun `the golden set is the size the plan asks for and geo tiles holds exactly it`() {
        assertTrue(all.size >= 40, "${all.size} tiles")
        assertEquals(all.size, all.toSet().size)
        assertEquals(all.map { it.z }.toSet(), (0..16).toSet() - setOf(5, 7, 9))
        val expectedFiles = all.map { "${it.z}_${it.x}_${it.y}.json" } + infoTiles.map { "${it.z}_${it.x}_${it.y}_info.json" }
        val onDisk = fixtures.file("geo/tiles").listFiles(false).map { it.name }.filter { it.endsWith(".json") }
        assertEquals(expectedFiles.sorted(), onDisk.sorted(), "stale golden tile files must be deleted by hand")
    }
}

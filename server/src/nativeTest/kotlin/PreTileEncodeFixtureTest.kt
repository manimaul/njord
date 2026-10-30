import io.madrona.njord.Singletons
import io.madrona.njord.layers.Depare
import io.madrona.njord.layers.Layerable
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-SY-1 / P-SY-3 / SY-10 / SY-11: the `preTileEncode` symbology rules of every registered
 * [Layerable], pinned as `testdata/symbology/pre_tile_encode/<KEY>.json`.
 *
 * The input set of a layer is derived mechanically rather than written by hand: a recording props
 * map (see [RecordingProps]) reveals which attributes the rule reads, iterating until no new key
 * turns up (a rule may read `VALSOU` only when `CATWRK` is absent), and every discovered key is
 * then swept over the catalogue's values for it ([AttributeCandidates]) alone and pairwise. The
 * hand-written tier-0 cases ([tier0Cases]) sit on top of that, at exact threshold boundaries. A
 * file is only written for a layer whose output ever differs from its input; everything else is
 * the identity and the Rust port can treat it as such.
 */
class PreTileEncodeFixtureTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
    }

    private val factory get() = Singletons.layerFactory
    private val candidates = AttributeCandidates()

    private fun encode(layerable: Layerable, input: SymbologyInput): JsonObject {
        val feature = input.toFeature(layerable.key)
        runBlocking { layerable.preTileEncode(feature) }
        return JsonObject(feature.props)
    }

    /** The attribute keys [layerable] reads on any of [inputs], recorded while running them. */
    private fun readKeys(layerable: Layerable, inputs: List<JsonObject>): Set<String> {
        val reads = mutableSetOf<String>()
        inputs.forEach { input ->
            val recording = RecordingProps(input.toMutableMap())
            val feature = io.madrona.njord.model.ChartFeature(layerable.key, null, recording, emptyList())
            runBlocking { layerable.preTileEncode(feature) }
            reads += recording.reads
        }
        return reads
    }

    /** Discovers the read set to a fixed point, then builds the sweep: empty × geometry, singles, reduced pairs. */
    private fun sweep(layerable: Layerable): List<SymbologyInput> {
        var keys = readKeys(layerable, listOf(JsonObject(emptyMap())))
        repeat(4) {
            val probes = keys.flatMap { k -> candidates.full(k).map { v -> JsonObject(mapOf(k to v)) } }
            val found = readKeys(layerable, probes)
            if (found == keys) return@repeat
            keys = keys + found
        }
        val sorted = keys.sorted()
        val cases = mutableListOf<SymbologyInput>()
        GEOMETRY_TYPES.forEach { cases += SymbologyInput(JsonObject(emptyMap()), it) }
        sorted.forEach { k -> candidates.full(k).forEach { v -> cases += SymbologyInput(JsonObject(mapOf(k to v))) } }
        for (i in sorted.indices) for (j in i + 1 until sorted.size) {
            val (a, b) = sorted[i] to sorted[j]
            candidates.reduced(a).forEach { va ->
                candidates.reduced(b).forEach { vb -> cases += SymbologyInput(JsonObject(mapOf(a to va, b to vb))) }
            }
        }
        return cases
    }

    /**
     * Tier-0 cases (audit SY-3 … SY-8) at the exact boundaries the sweep's grid cannot be trusted
     * to hit, written against the shipped thresholds (3 / 6 / 9 m).
     */
    private fun tier0Cases(key: String): List<SymbologyInput> = when (key) {
        "DEPARE" -> listOf(-5.0 to -1.0, -5.0 to 0.0, -0.1 to 0.1, 0.0 to 3.0, 3.0 to 6.0, 3.0001 to 6.0, 6.0 to 9.0, 6.0001 to 9.0, 9.0 to 20.0, 9.0001 to 20.0, 30.0 to 50.0)
            .map { (d1, d2) -> SymbologyInput(props("DRVAL1" to d1, "DRVAL2" to d2), "POLYGON") } +
                listOf(SymbologyInput(props("DRVAL1" to 5.0), "POLYGON"), SymbologyInput(props("DRVAL2" to 5.0), "POLYGON"))

        "SOUNDG" -> listOf(0.0, 0.1, 2.3, 7.6, 9.95, 10.0, 31.0, -0.3, -1.5, -12.0).map { SymbologyInput(props("METERS" to it)) }

        "OBSTRN" -> listOf(
            props("CATOBS" to 5),
            props("CATOBS" to 6), props("CATOBS" to 7),
            props("CATOBS" to 9), props("CATOBS" to 10),
            props("CATOBS" to 1, "QUASOU" to listOf(1), "VALSOU" to 2.0),
            props("WATLEV" to 3, "QUASOU" to listOf(1), "VALSOU" to 2.9),
            props("WATLEV" to 3, "QUASOU" to listOf(1), "VALSOU" to 3.0),
            props("WATLEV" to 3, "QUASOU" to listOf(1), "VALSOU" to 6.0),
            props("WATLEV" to 3, "QUASOU" to listOf(1), "VALSOU" to 6.1),
            props("WATLEV" to 3, "QUASOU" to listOf(1), "VALSOU" to 9.0),
            props("WATLEV" to 3, "QUASOU" to listOf(1), "VALSOU" to 9.1),
            props("WATLEV" to 3, "QUASOU" to listOf(2), "VALSOU" to 9.1),
            props("WATLEV" to 3, "QUASOU" to listOf(3), "VALSOU" to 2.0),
            props("WATLEV" to 3, "VALSOU" to 12.0),
            props("WATLEV" to 4, "QUASOU" to listOf(3)),
            props("WATLEV" to 7, "VALSOU" to 1.0, "QUASOU" to listOf(1)),
            props("WATLEV" to 2), props("WATLEV" to 5), props("WATLEV" to 1),
        ).flatMap { p -> listOf(SymbologyInput(p, "POINT"), SymbologyInput(p, "POLYGON")) }

        "WRECKS" -> listOf(
            props("CATWRK" to 1), props("CATWRK" to 2), props("CATWRK" to 3), props("CATWRK" to 4), props("CATWRK" to 5),
            props("CATWRK" to 1, "VALSOU" to 5.9), props("CATWRK" to 1, "VALSOU" to 6.0), props("CATWRK" to 1, "VALSOU" to 6.1),
            props("WATLEV" to 3), props("WATLEV" to 3, "VALSOU" to 2.0), props("WATLEV" to 3, "VALSOU" to 8.0),
            props("VALSOU" to 6.0), props("VALSOU" to 6.01), props("WATLEV" to 4),
        ).map { SymbologyInput(it) }

        "UWTROC" -> (0..8).map { SymbologyInput(props("WATLEV" to it)) }

        "LIGHTS" -> listOf(
            props("COLOUR" to listOf(3)), props("COLOUR" to listOf(4)), props("COLOUR" to listOf(1)), props("COLOUR" to listOf(6)),
            props("COLOUR" to listOf(9)), props("COLOUR" to listOf(11)), props("COLOUR" to listOf(2)), props("COLOUR" to listOf(3, 4)),
            props("COLOUR" to listOf(3), "VALNMR" to 9.9), props("COLOUR" to listOf(3), "VALNMR" to 10.0),
            props("COLOUR" to listOf(4), "SECTR1" to 10.0, "SECTR2" to 200.0),
            props("COLOUR" to listOf(1), "SECTR1" to 350.5, "SECTR2" to 10.25, "VALNMR" to 12.0),
            props("COLOUR" to listOf(1), "SECTR1" to 90.0),
            props("COLOUR" to listOf(3), "LITCHR" to 2, "SIGGRP" to "(1)", "SIGPER" to 4.0, "HEIGHT" to 7.6, "VALNMR" to 5.0),
            props("COLOUR" to listOf(1, 3, 4), "CATLIT" to listOf(1), "LITCHR" to 8, "SIGGRP" to "(2)", "SIGPER" to 6.0, "VALNMR" to 12.0, "STATUS" to listOf(2)),
            props("COLOUR" to listOf(3), "LITCHR" to 2, "SIGPER" to 4.0, "SECTR1" to 10.0, "SECTR2" to 20.0),
        ).map { SymbologyInput(it) }

        "TOPMAR" -> (0..34).flatMap { shape ->
            listOf(
                SymbologyInput(props("TOPSHP" to shape), assoc = emptyList()),
                SymbologyInput(props("TOPSHP" to shape), assoc = listOf("BOYLAT")),
                SymbologyInput(props("TOPSHP" to shape), assoc = listOf("BCNLAT")),
            )
        }

        "BOYLAT", "BOYSPP", "BOYSAW", "BOYISD", "BOYCAR", "BOYINB" -> (0..9).flatMap { shape ->
            listOf(1, 2, 3, 4, 6, 14).map { colour -> SymbologyInput(props("BOYSHP" to shape, "COLOUR" to listOf(colour))) }
        } + (0..5).flatMap { cat -> listOf(3, 4).map { colour -> SymbologyInput(props("CATLAM" to cat, "COLOUR" to listOf(colour))) } }

        "BCNLAT", "BCNSPP", "BCNSAW", "BCNISD", "BCNCAR" -> (0..8).flatMap { shape ->
            listOf(1, 2, 3, 4, 6, 14).map { colour -> SymbologyInput(props("BCNSHP" to shape, "COLOUR" to listOf(colour))) }
        }

        else -> emptyList()
    }

    private fun header(layerable: Layerable, sweepSize: Int) = FixtureHeader(
        pins = "preTileEncode of ${layerable::class.simpleName} (layer key ${layerable.key}): the symbology props (SY, AC, LC, AP, LP, …) added for a sweep of the attributes the rule reads",
        decisions = if (tier0Cases(layerable.key).isEmpty()) listOf("SY-10", "SY-12") else listOf("SY-3", "SY-4", "SY-5", "SY-6", "SY-7", "SY-8", "SY-9", "SY-12"),
        generator = "PreTileEncodeFixtureTest.everyLayerable",
        source = "$sweepSize cases from a recorded-read sweep over the S-57 catalogue values (shipped thresholds 3/6/9 m) plus ${tier0Cases(layerable.key).size} hand-written boundary cases",
    )

    /**
     * `symbology/pre_tile_encode/<KEY>.json` for the S-57 object classes. Layerables registered
     * under a custom, non-uppercase key - the InlandLayers twins (`achare`, `achbrt`, `lokbsn`,
     * same class as their uppercase StandardLayers instance) and the Natural Earth base layers
     * (`glaciated_areas`, `reefs`) - go under `custom_key/`, because a case-insensitive filesystem
     * would merge `achare.json` into `ACHARE.json`.
     */
    private fun fixturePath(key: String) =
        if (key == key.uppercase()) "symbology/pre_tile_encode/$key.json" else "symbology/pre_tile_encode/custom_key/$key.json"

    /** Every sprite the rules ever assign, collected while generating, for SY-11. */
    private val assignedSprites = mutableSetOf<String>()

    @Test
    fun everyLayerable() {
        val written = mutableListOf<String>()
        factory.layerables.values.sortedBy { it.key }.forEach { layerable ->
            val sweep = sweep(layerable)
            val inputs = sweep + tier0Cases(layerable.key)
            val outputs = inputs.map { encode(layerable, it) }
            outputs.forEach { out ->
                listOf("SY", "SY2", "AP", "LP").forEach { k -> out[k]?.jsonPrimitive?.content?.let { assignedSprites += it } }
            }
            val changesSomething = inputs.zip(outputs).any { (i, o) -> i.props != o }
            if (!changesSomething) return@forEach
            written += layerable.key
            fixtures.table(fixturePath(layerable.key), header(layerable, sweep.size), inputs) { encode(layerable, it) }
        }
        // the tier-0 layers must be among those pinned
        listOf("DEPARE", "SOUNDG", "OBSTRN", "WRECKS", "UWTROC", "LIGHTS", "TOPMAR", "BOYLAT", "BCNLAT").forEach {
            assertTrue(it in written, "$it must have a pre_tile_encode fixture")
        }
        assertTrue(written.size > 60, "expected most layerables to change props, got ${written.size}")
    }

    /** SY-10: the smoke test across all layerables, independent of the fixtures. */
    @Test
    fun `every layerable accepts a minimal feature of every geometry type`() {
        factory.layerables.values.forEach { layerable ->
            GEOMETRY_TYPES.forEach { geometry ->
                encode(layerable, SymbologyInput(JsonObject(emptyMap()), geometry))
            }
        }
    }

    /** SY-11: every sprite any rule assigns is a [Sprite] and exists in every theme's sheet. */
    @Test
    fun `every assigned sprite exists in every sprite sheet`() {
        if (assignedSprites.isEmpty()) everyLayerable()
        val sheets = Singletons.spriteSheet.spritesByTheme
        assertEquals(ThemeMode.entries.toSet(), sheets.keys)
        assignedSprites.forEach { name ->
            val sprite = Sprite.entries.firstOrNull { it.name == name } ?: error("$name is assigned but is not a Sprite")
            ThemeMode.entries.forEach { theme ->
                assertTrue(sheets[theme]!!.containsKey(sprite), "$name missing from the $theme sheet")
            }
        }
        assertTrue(assignedSprites.size > 100, "only ${assignedSprites.size} sprites were assigned")
    }

    /**
     * P-SY-3: DEPARE colour bands come from [io.madrona.njord.ChartsConfig], proven with
     * thresholds that differ from the shipped 3 / 6 / 9 m. `<=` at every threshold, DEPIT only
     * when both bounds are at or below zero, CHBLK when a bound is missing.
     */
    @Test
    fun depthBands() {
        val config = Singletons.config.copy(shallowDepth = 2f, safetyDepth = 5f, deepDepth = 20f)
        val depare = Depare(config)
        val d1 = listOf(-5.0, -0.1, 0.0, 1.9, 2.0, 2.1, 4.9, 5.0, 5.1, 19.9, 20.0, 20.1, 100.0)
        val d2 = listOf(-1.0, 0.0, 0.1, 10.0)
        val inputs = d1.flatMap { a -> d2.map { b -> SymbologyInput(props("DRVAL1" to a, "DRVAL2" to b), "POLYGON") } } +
                listOf(SymbologyInput(props("DRVAL1" to 1.0), "POLYGON"), SymbologyInput(props("DRVAL2" to 1.0), "POLYGON"), SymbologyInput(props(), "POLYGON"))
        fixtures.table(
            "symbology/depth_bands.json",
            FixtureHeader(
                pins = "Depare.preTileEncode colour band (AC) with shallowDepth=2, safetyDepth=5, deepDepth=20: DEPIT when DRVAL1<0 and DRVAL2<=0, else DEPVS/DEPMS/DEPMD by DRVAL1 <= threshold, DEPDW above deepDepth, CHBLK when a bound is missing; LC is always CHGRD",
                decisions = listOf("SY-3"),
                generator = "PreTileEncodeFixtureTest.depthBands",
                source = "DRVAL1 at every threshold and its neighbours × DRVAL2 around zero",
            ),
            inputs,
        ) { encode(depare, it) }
        assertEquals("DEPVS", encode(depare, SymbologyInput(props("DRVAL1" to 2.0, "DRVAL2" to 10.0)))["AC"]?.jsonPrimitive?.content)
        assertEquals("DEPMS", encode(depare, SymbologyInput(props("DRVAL1" to 2.1, "DRVAL2" to 10.0)))["AC"]?.jsonPrimitive?.content)
        assertEquals("DEPDW", encode(depare, SymbologyInput(props("DRVAL1" to 20.1, "DRVAL2" to 10.0)))["AC"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive("DEPIT"), encode(depare, SymbologyInput(props("DRVAL1" to -5.0, "DRVAL2" to 0.0)))["AC"])
    }
}

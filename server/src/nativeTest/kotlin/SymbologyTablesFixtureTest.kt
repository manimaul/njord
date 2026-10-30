import io.madrona.njord.Singletons
import io.madrona.njord.buildSectorSvg
import io.madrona.njord.geo.symbols.Colour.Companion.colors
import io.madrona.njord.layers.LightDescription
import io.madrona.njord.layers.LightDescription.Companion.format
import io.madrona.njord.layers.Sbdare
import io.madrona.njord.layers.attributehelpers.Bcnshp.Companion.bcnshp
import io.madrona.njord.layers.attributehelpers.Boyshp.Companion.boyshp
import io.madrona.njord.layers.attributehelpers.Catbrg.Companion.catbrg
import io.madrona.njord.layers.attributehelpers.Catcam.Companion.catcam
import io.madrona.njord.layers.attributehelpers.Catdam.Companion.catdam
import io.madrona.njord.layers.attributehelpers.Catfif.Companion.catfif
import io.madrona.njord.layers.attributehelpers.Catgat.Companion.catgat
import io.madrona.njord.layers.attributehelpers.Catlam.Companion.catlam
import io.madrona.njord.layers.attributehelpers.Catlmk.Companion.catlmk
import io.madrona.njord.layers.attributehelpers.Catlnd.Companion.catlnd
import io.madrona.njord.layers.attributehelpers.Catmor.Companion.catmor
import io.madrona.njord.layers.attributehelpers.Catobs.Companion.catobs
import io.madrona.njord.layers.attributehelpers.Catpip.Companion.catpip
import io.madrona.njord.layers.attributehelpers.Catpra.Companion.catpra
import io.madrona.njord.layers.attributehelpers.Catrea.Companion.catrea
import io.madrona.njord.layers.attributehelpers.Catsea.Companion.catsea
import io.madrona.njord.layers.attributehelpers.Catsil.Companion.catsil
import io.madrona.njord.layers.attributehelpers.Catspm.Companion.catspm
import io.madrona.njord.layers.attributehelpers.Cattrk.Companion.cattrk
import io.madrona.njord.layers.attributehelpers.Catwrk.Companion.catwrk
import io.madrona.njord.layers.attributehelpers.Conrad.Companion.conrad
import io.madrona.njord.layers.attributehelpers.Convis.Companion.convis
import io.madrona.njord.layers.attributehelpers.Functn.Companion.functn
import io.madrona.njord.layers.attributehelpers.Prodct.Companion.prodct
import io.madrona.njord.layers.attributehelpers.Quasou.Companion.quasou
import io.madrona.njord.layers.attributehelpers.Restrn.Companion.restrn
import io.madrona.njord.layers.attributehelpers.Topshp.Companion.topshp
import io.madrona.njord.layers.attributehelpers.Trafic.Companion.trafic
import io.madrona.njord.layers.attributehelpers.Watlev.Companion.watlev
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Color
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.model.colorFrom
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The table-shaped symbology fixtures: P-SY-4 display depths, P-SY-5 light descriptions, P-SY-6
 * attribute helpers, P-SY-8 sprite sheets and P-SY-9 sector SVGs.
 */
class SymbologyTablesFixtureTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
        Gdal.initialize()
    }

    private val json = Json

    /**
     * P-SY-4: `displayDepths` via `addSoundingConversions`, i.e. the sounding props the tile
     * carries. 0.0…31.0 in tenths plus drying heights, from the fixed D1 code: the tenths follow
     * the encoded decimal (2.3 -> 2 and 3), extra precision truncates, drying heights keep the
     * sign on every part.
     */
    @Test
    fun displayDepths() {
        val inputs = (0..310).map { it / 10.0 } + (1..30).map { -it / 10.0 } + listOf(2.29999, 4.35, 9.99, 1.5, 7.6)
        fixtures.table(
            "symbology/display_depths.json",
            FixtureHeader(
                pins = "displayDepths/addSoundingConversions: METERS plus METERS_W/METERS_T (decimal tenths, truncated), FEET (rounded up past .9), FATHOMS/FATHOMS_FT; drying heights negative on every part",
                decisions = listOf("D1", "SY-1", "SY-2"),
                generator = "SymbologyTablesFixtureTest.displayDepths",
                source = "0.0..31.0 in 0.1 steps, -0.1..-3.0 in 0.1 steps, and the SoundingsTest edge values",
            ),
            inputs,
        ) { meters ->
            val props = mutableMapOf<String, JsonElement>()
            props.addSoundingConversions(meters)
            JsonObject(props)
        }
    }

    @Serializable
    data class LightText(val head: String, val tail: String)

    /**
     * P-SY-5: [LightDescription.from] for the PresLib examples in LightDescriptionTest and every
     * LIGHTS feature of the fixture cell. `null` means no description (no LITCHR).
     */
    @Test
    fun lightDescription() {
        val examples = listOf(
            props("LITCHR" to 2, "SIGGRP" to "(1)", "COLOUR" to listOf(3), "SIGPER" to 4.0, "HEIGHT" to 7.6, "VALNMR" to 5.0),
            props("LITCHR" to 2, "SIGGRP" to "(1)", "COLOUR" to listOf(1), "SIGPER" to 30.0, "HEIGHT" to 7.0, "VALNMR" to 10.0),
            props("CATLIT" to listOf(1), "LITCHR" to 8, "SIGGRP" to "(2)", "COLOUR" to listOf(1, 3, 4), "SIGPER" to 6.0, "VALNMR" to 12.0, "STATUS" to listOf(2)),
            props("LITCHR" to 1, "SIGGRP" to "()", "COLOUR" to listOf(4)),
            props("LITCHR" to 4, "HEIGHT" to 5.0),
            props("COLOUR" to listOf(3), "VALNMR" to 5.0),
            props("LITCHR" to 25, "SIGGRP" to "(2+1)", "COLOUR" to listOf(1), "SIGPER" to 12.5, "VALNMR" to 15.0, "STATUS" to listOf(7, 8)),
            props("CATLIT" to listOf(5, 7), "LITCHR" to 29, "COLOUR" to listOf(9, 10, 11, 5), "SIGPER" to 0.0, "VALNMR" to 0.25),
            props("LITCHR" to 99, "COLOUR" to listOf(1)),
            props("LITCHR" to 2, "COLOUR" to listOf(2, 7)),
        )
        val cell = OgrS57Dataset(fixtures.file("fixtures/US5WA22M/US5WA22M.000"))
        val cellLights = cell.getLayer("LIGHTS")!!.mapFeature { it.properties }
        assertTrue(cellLights.isNotEmpty())
        fixtures.table(
            "symbology/light_description.json",
            FixtureHeader(
                pins = "LightDescription.from: S-52 PresLib §10.6.3 text in draw order CATLIT, LITCHR+SIGGRP, COLOUR, SIGPER | HEIGHT (spliced by the style) | VALNMR, STATUS; default groups omitted; numbers whole or one decimal; null without LITCHR",
                decisions = emptyList(),
                generator = "SymbologyTablesFixtureTest.lightDescription",
                source = "PresLib examples from LightDescriptionTest plus every LIGHTS feature of fixtures/US5WA22M (GDAL ${Gdal.gdalVersion()})",
            ),
            examples + cellLights,
        ) { p -> LightDescription.from(p.toMutableMap())?.let { LightText(it.head, it.tail) } }

        fixtures.table(
            "symbology/light_number_format.json",
            FixtureHeader(
                pins = "LightDescription number formatting: whole when the value rounds to a whole tenth, else one decimal (half-up on the tenth)",
                decisions = emptyList(),
                generator = "SymbologyTablesFixtureTest.lightDescription",
            ),
            listOf(0.0, 4.0, 2.5, 0.25000001, 9.96, 9.94, 10.05, 0.05, 0.04, 123.456, 7.6, 1.0e6),
        ) { it.format() }
    }

    @Serializable
    data class HelperInput(val helper: String, val attribute: String, val value: JsonElement)

    private class Helper(val name: String, val attribute: String, val list: Boolean, val decode: (ChartFeature) -> JsonElement)

    private fun single(name: String, attribute: String, decode: (ChartFeature) -> Enum<*>?) =
        Helper(name, attribute, false) { f -> decode(f)?.let { JsonPrimitive(it.name) } ?: JsonNull }

    private fun multi(name: String, attribute: String, decode: (ChartFeature) -> List<Enum<*>>) =
        Helper(name, attribute, true) { f -> JsonArray(decode(f).map { JsonPrimitive(it.name) }) }

    private val helpers = listOf(
        single("Bcnshp", "BCNSHP") { it.bcnshp() },
        single("Boyshp", "BOYSHP") { it.boyshp() },
        multi("Catbrg", "CATBRG") { it.catbrg() },
        single("Catcam", "CATCAM") { it.catcam() },
        single("Catdam", "CATDAM") { it.catdam() },
        single("Catfif", "CATFIF") { it.catfif() },
        single("Catgat", "CATGAT") { it.catgat() },
        single("Catlam", "CATLAM") { it.catlam() },
        multi("Catlmk", "CATLMK") { it.catlmk() },
        multi("Catlnd", "CATLND") { it.catlnd() },
        single("Catmor", "CATMOR") { it.catmor() },
        single("Catobs", "CATOBS") { it.catobs() },
        multi("Catpip", "CATPIP") { it.catpip() },
        single("Catpra", "CATPRA") { it.catpra() },
        multi("Catrea", "CATREA") { it.catrea() },
        single("Catsea", "CATSEA") { it.catsea() },
        single("Catsil", "CATSIL") { it.catsil() },
        multi("Catspm", "CATSPM") { it.catspm() },
        single("Cattrk", "CATTRK") { it.cattrk() },
        single("Catwrk", "CATWRK") { it.catwrk() },
        single("Conrad", "CONRAD") { it.conrad() },
        single("Convis", "CONVIS") { it.convis() },
        multi("Functn", "FUNCTN") { it.functn() },
        multi("Prodct", "PRODCT") { it.prodct() },
        multi("Quasou", "QUASOU") { it.quasou() },
        multi("Restrn", "RESTRN") { it.restrn() },
        single("Topshp", "TOPSHP") { it.topshp() },
        single("Trafic", "TRAFIC") { it.trafic() },
        single("Watlev", "WATLEV") { it.watlev() },
        multi("Colour", "COLOUR") { it.colors() },
    )

    /**
     * P-SY-6: every attribute helper's decode over 0 … max+1 of the catalogue's expected inputs
     * for its attribute. Single-valued helpers take an integer and answer an enum name or null;
     * list helpers take a one-element array and answer a list (unknown IDs are dropped, not
     * errors). Plus [Sbdare.natsur]'s abbreviation table.
     */
    @Test
    fun attributeHelpers() {
        val candidates = AttributeCandidates()
        val inputs = helpers.flatMap { h ->
            val max = candidates.enumIds(h.attribute).maxOrNull() ?: 40
            (0..max + 1).map { id ->
                // GDAL emits list attributes as arrays of strings, enumerated ones as integers
                HelperInput(h.name, h.attribute, if (h.list) JsonArray(listOf(JsonPrimitive(id.toString()))) else JsonPrimitive(id))
            } + if (h.list) listOf(
                HelperInput(h.name, h.attribute, JsonArray(emptyList())),
                HelperInput(h.name, h.attribute, JsonArray(listOf(JsonPrimitive("1"), JsonPrimitive("2"), JsonPrimitive("999")))),
                HelperInput(h.name, h.attribute, JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))),
            ) else listOf(HelperInput(h.name, h.attribute, JsonPrimitive("3")), HelperInput(h.name, h.attribute, JsonPrimitive(3.0)))
        }
        fixtures.table(
            "symbology/attribute_helpers.json",
            FixtureHeader(
                pins = "attributehelpers/* and Colour decode: S-57 enumerated ID -> enum name (null when unknown), list attributes (arrays of strings as GDAL emits them, integers accepted too) element-wise dropping unknown IDs; a string \"3\" decodes like 3, a real 3.0 does not",
                decisions = listOf("SY-9"),
                generator = "SymbologyTablesFixtureTest.attributeHelpers",
                source = "0..max+1 of the catalogue's expected inputs per attribute",
            ),
            inputs,
        ) { input ->
            val helper = helpers.first { it.name == input.helper }
            helper.decode(ChartFeature(input.helper.uppercase(), null, mutableMapOf(input.attribute to input.value), emptyList()))
        }

        fixtures.table(
            "symbology/natsur.json",
            FixtureHeader(
                pins = "Sbdare.natsur: PresLib §14.6.3 seabed abbreviations, space separated, repeats dropped (lava/boulders/rock all R), null when nothing maps",
                decisions = emptyList(),
                generator = "SymbologyTablesFixtureTest.attributeHelpers",
            ),
            (0..19).map { listOf(it) } + listOf(listOf(1, 4, 6), listOf(9, 11, 18), listOf(4, 4), listOf(10, 12), emptyList()),
        ) { Sbdare.natsur(it) }
    }

    /** P-SY-8: the sprite sheets by theme (name -> rect and stretch metadata) and the Sprite enum. */
    @Test
    fun sprites() {
        val sheets = Singletons.spriteSheet.spritesByTheme
        fixtures.table(
            "symbology/sprites.json",
            FixtureHeader(
                pins = "SpriteSheet: every Sprite's IconInfo per theme from www/sprites/<theme>_simplified@2x.json, and the Sprite enum in declaration order",
                decisions = emptyList(),
                generator = "SymbologyTablesFixtureTest.sprites",
            ),
            ThemeMode.entries.map { it.name } + "ENUM",
        ) { name ->
            if (name == "ENUM") JsonArray(Sprite.entries.map { JsonPrimitive(it.name) })
            else JsonObject(sheets[ThemeMode.valueOf(name)]!!.entries.sortedBy { it.key.name }.associate { (k, v) -> k.name to json.encodeToJsonElement(v) })
        }
        ThemeMode.entries.forEach { assertEquals(Sprite.entries.toSet(), sheets[it]!!.keys, it.name) }
    }

    @Serializable
    data class SectorInput(val sectr1: Double, val sectr2: Double, val fillColor: String, val lineColor: String, val radius: Double)

    /** P-SY-9: [buildSectorSvg] over a grid of sectors and the three light colours (with their radii) in every theme. */
    @Test
    fun sectorSvg() {
        val angles = listOf(0.0, 45.0, 90.0, 180.0, 270.0, 359.5, 10.25)
        val lights = listOf(Color.LITRD to 68.0, Color.LITGN to 74.0, Color.LITYW to 80.0)
        val inputs = ThemeMode.entries.flatMap { theme ->
            lights.flatMap { (color, radius) ->
                angles.flatMap { s1 -> angles.map { s2 -> SectorInput(s1, s2, colorFrom(color, theme), colorFrom(Color.CHGRD, theme), radius) } }
            }
        }
        fixtures.table(
            "symbology/svg_sector.json",
            FixtureHeader(
                pins = "buildSectorSvg: 200x200 viewBox, reciprocal bearings (+180), arc with large-arc flag when span > 180, full circle when span == 0, dashed sector lines, coordinates to one decimal",
                decisions = emptyList(),
                generator = "SymbologyTablesFixtureTest.sectorSvg",
                source = "7 angles squared × LITRD/LITGN/LITYW radii × Day/Dusk/Night colours",
            ),
            inputs,
        ) { buildSectorSvg(it.sectr1, it.sectr2, it.fillColor, it.lineColor, it.radius) }
        assertTrue(buildSectorSvg(0.0, 0.0, "#f00", "#000").contains("<circle"))
    }
}

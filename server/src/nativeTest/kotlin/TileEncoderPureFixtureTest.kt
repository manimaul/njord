import io.madrona.njord.Singletons
import io.madrona.njord.geo.TileEncoder
import io.madrona.njord.geo.filteredProps
import io.madrona.njord.testdata.FixtureHeader
import io.madrona.njord.testdata.fixtures
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two pure decisions inside the tile encoder: P-TE-3 property filtering and P-TE-4 the base
 * map scale per zoom.
 */
class TileEncoderPureFixtureTest {

    @BeforeTest
    fun before() {
        TestEnv.init()
        Gdal.initialize()
    }

    /**
     * `"0"` is dropped only on an *enumerated* attribute (CATOBS, WATLEV, BOYSHP …), where S-57
     * has no value 0 and GDAL emits it for "unset"; a float or integer 0 (`VALSOU`, `DRVAL1`,
     * `SCAMIN`) is data. The comparison is on the primitive's text, so `0.0` is kept everywhere
     * and a string `"0"` on an enumerated attribute is dropped. A nested object is an error.
     */
    @Test
    fun filteredProps() {
        val inputs = listOf(
            props("OBJNAM" to "", "INFORM" to "   ", "NINFOM" to "kept"),
            props("CATOBS" to 0, "WATLEV" to 0, "BOYSHP" to 0, "TOPSHP" to 1),
            props("CATOBS" to "0", "CATOBS2" to "0", "VALSOU" to "0"),
            props("VALSOU" to 0, "DRVAL1" to 0.0, "SCAMIN" to 0, "ORIENT" to 0, "METERS" to 0),
            props("WATLEV" to 0.0, "CATOBS" to 0.5),
            props("COLOUR" to emptyList<Int>(), "STATUS" to listOf(0), "LNAM_REFS" to emptyList<String>(), "CATSPM" to listOf(1, 2)),
            props("EA" to true, "_OPBR" to false, "LNAM" to "0226", "NAME" to "US5WA22M.000", "MINZ" to 11, "MAXZ" to 32),
            props("UNKNOWN_ATTR" to 0, "UNKNOWN_STR" to "0", "unknown" to ""),
            props("BAD" to buildJsonObject { put("x", 1) }),
            props(),
        )
        fixtures.table(
            "geo/filtered_props.json",
            FixtureHeader(
                pins = "TileEncoder filtered(): blank strings removed, empty arrays removed, \"0\" removed only when the key is an enumerated (E) S-57 attribute, everything else kept; a nested object throws",
                decisions = listOf("TE-3"),
                generator = "TileEncoderPureFixtureTest.filteredProps",
                source = "hand-written against resources/s57attributes.json attribute types",
            ),
            inputs,
        ) { input -> filteredOrThrows(input) }
    }

    private fun filteredOrThrows(input: JsonObject): JsonElement = try {
        JsonObject(input.toMutableMap().filteredProps(Singletons.s57ObjectLibrary))
    } catch (t: Throwable) {
        buildJsonObject { put("throws", t::class.simpleName ?: "Throwable") }
    }

    /** NE 110m below z3, 50m below z5, 10m from z5 up - the scale that selects base_features rows. */
    @Test
    fun baseMapScale() {
        val encoder = TileEncoder(0, 0, 0)
        fixtures.table(
            "geo/base_map_scale.json",
            FixtureHeader(
                pins = "TileEncoder.findBaseMapScale: Natural Earth scale denominator per tile zoom (110m z0-2, 50m z3-4, 10m z5+)",
                decisions = listOf("TE-6"),
                generator = "TileEncoderPureFixtureTest.baseMapScale",
            ),
            (0..24).toList(),
        ) { encoder.findBaseMapScale(it) }
        assertEquals(110_000_000, encoder.findBaseMapScale(2))
        assertEquals(50_000_000, encoder.findBaseMapScale(3))
        assertEquals(50_000_000, encoder.findBaseMapScale(4))
        assertEquals(10_000_000, encoder.findBaseMapScale(5))
    }
}

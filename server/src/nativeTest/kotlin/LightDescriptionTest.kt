import io.madrona.njord.ext.json
import io.madrona.njord.layers.LightDescription
import io.madrona.njord.layers.LightDescription.Companion.format
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The height is spliced between [LightDescription.head] and [LightDescription.tail] by the style,
 * so these assert on `head + <height> + tail` with the height pre-formatted the way the metres
 * style expression would.
 */
class LightDescriptionTest {

    private fun props(vararg pairs: Pair<String, Any>): MutableMap<String, JsonElement> =
        pairs.associate { (k, v) ->
            k to when (v) {
                is List<*> -> JsonArray(v.map { it.toString().json })
                is Int -> v.json
                is Double -> v.json
                else -> v.toString().json
            }
        }.toMutableMap()

    private fun describe(height: String = "", vararg pairs: Pair<String, Any>): String? =
        LightDescription.from(props(*pairs))?.let { it.head + height + it.tail }

    @Test
    fun `NOAA US5MA1FG light - default signal group is omitted`() {
        assertEquals(
            "Fl R 4s7.6m5M",
            describe(
                "7.6m",
                "LITCHR" to 2, "SIGGRP" to "(1)", "COLOUR" to listOf(3),
                "SIGPER" to 4.0, "HEIGHT" to 7.6, "VALNMR" to 5.0,
            )
        )
    }

    @Test
    fun `PresLib 10_6_3 example`() {
        assertEquals(
            "Fl W 30s7m10M",
            describe(
                "7m",
                "LITCHR" to 2, "SIGGRP" to "(1)", "COLOUR" to listOf(1),
                "SIGPER" to 30.0, "HEIGHT" to 7.0, "VALNMR" to 10.0,
            )
        )
    }

    @Test
    fun `non default group with multiple colours and category and status`() {
        assertEquals(
            "Dir Oc(2) WRG 6s12M occas",
            describe(
                "",
                "CATLIT" to listOf(1), "LITCHR" to 8, "SIGGRP" to "(2)", "COLOUR" to listOf(1, 3, 4),
                "SIGPER" to 6.0, "VALNMR" to 12.0, "STATUS" to listOf(2),
            )
        )
    }

    @Test
    fun `empty group and no numbers`() {
        assertEquals("F G", describe("", "LITCHR" to 1, "SIGGRP" to "()", "COLOUR" to listOf(4)))
    }

    @Test
    fun `height only keeps a separating space`() {
        assertEquals("Q 5m", describe("5m", "LITCHR" to 4, "HEIGHT" to 5.0))
    }

    @Test
    fun `no characteristic means no description`() {
        assertNull(LightDescription.from(props("COLOUR" to listOf(3), "VALNMR" to 5.0)))
    }

    @Test
    fun `numbers are whole or one decimal`() {
        assertEquals("4", 4.0.format())
        assertEquals("2.5", 2.5.format())
        assertEquals("0.3", 0.25000001.format())
        assertEquals("10", 9.96.format())
    }
}

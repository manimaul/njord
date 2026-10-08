package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Seabed area
 *
 * Acronym: SBDARE
 *
 * Code: 116
 *
 * S-52 lookups: `TX(NATSUR,1,2,2,'15110',0,0,CHBLK,25)` on every geometry, centred, plus
 * `LS(SOLD,1,CHGRD)` on a line and `LS(DASH,1,CHGRD)` around an area whose WATLEV is 3 (always
 * under water) or 4 (covers and uncovers). No fill.
 *
 * Not done: an intertidal (WATLEV 4) rock, lava or coral area is filled with the RCKLDG01 pattern
 * in place of the text, but that pattern is vector-only in the presentation library and isn't in
 * the sprite sheets, so those areas get the text instead.
 */
class Sbdare : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        natsur(feature.props.intValues("NATSUR"))?.let { feature.props[NATURE_OF_SURFACE] = it.json }
        when (feature.props.intValue("WATLEV")) {
            3, 4 -> feature.props[DASHED_OUTLINE] = true.json
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRD,
            width = 1f,
            filter = Filters.eqTypeLineString,
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRD,
            width = 1f,
            style = LineStyle.DashLine,
            filter = listOf(Filters.all, Filters.eqTypePolyGon, listOf("has", DASHED_OUTLINE)).json,
        ),
        textLayer(
            textField = listOf("get", NATURE_OF_SURFACE).json,
            theme = options.theme,
            filter = listOf("has", NATURE_OF_SURFACE).json,
        ),
    )

    companion object {
        private const val NATURE_OF_SURFACE = "_NS"
        private const val DASHED_OUTLINE = "_DO"

        /**
         * PresLib 4.0 Part I §14.6.3 "Nature of seabed abbreviations" - space separated, "to reduce
         * undue clutter ... " M S G" rather than "Mud Sand Gravel"". Lava and boulders abbreviate
         * to `R`, the same as rock, so repeats are dropped.
         */
        fun natsur(values: List<Int>): String? = values
            .mapNotNull { NATSUR[it] }
            .distinct()
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }

        private val NATSUR = mapOf(
            1 to "M",
            2 to "Cy",
            3 to "Si",
            4 to "S",
            5 to "St",
            6 to "G",
            7 to "P",
            8 to "Cb",
            9 to "R",
            11 to "R",
            14 to "Co",
            17 to "Sh",
            18 to "R",
        )
    }
}

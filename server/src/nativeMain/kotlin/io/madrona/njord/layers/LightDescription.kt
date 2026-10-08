package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.doubleValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.geo.symbols.stringValue
import kotlin.math.floor
import kotlin.math.round

/**
 * The light description text string — `Fl(2) R 6s7.6m5M` — per S-52 PresLib 4.0 Part I §10.6.3
 * "Light Description Text Strings" (the textual replacement for the old `LITDSN01` C function),
 * in `docs/reference_material/s52_s57_spec/TSMAD26_DIPWG5-8.1C_S-52_PresLib_v3.4_to_v4.0_Redlines.pdf`.
 *
 * Attributes are output in the spec's draw order: CATLIT, LITCHR, SIGGRP, COLOUR, SIGPER, HEIGHT,
 * VALNMR, STATUS.
 *
 * HEIGHT is deliberately *not* rendered here. Like [ClearanceLabel], the tile stays
 * unit-agnostic: the string is split into a [head] (everything up to and including the period)
 * and a [tail] (range and status), and the style splices the height between them in the viewer's
 * units. The spacing around that splice is already baked into [head] and [tail].
 */
class LightDescription(
    val head: String,
    val tail: String,
) {
    companion object {

        /**
         * Returns null when the light has no characteristic — there's nothing worth describing,
         * and S-52 doesn't fall back to a bare height/range.
         */
        fun from(props: S57Prop): LightDescription? {
            val characteristic = props.intValue("LITCHR")?.let { LITCHR[it] } ?: return null

            val category = props.intValues("CATLIT").mapNotNull { CATLIT[it] }.joinToString(" ")
            val group = props.stringValue("SIGGRP")?.takeUnless { it.isDefaultGroup() } ?: ""
            val colour = props.intValues("COLOUR").mapNotNull { COLOUR[it] }.joinToString("")
            val status = props.intValues("STATUS").mapNotNull { STATUS[it] }.joinToString(" ")

            val period = props.positive("SIGPER")?.let { "${it.format()}s" } ?: ""
            val range = props.positive("VALNMR")?.let { "${it.format()}M" } ?: ""
            val hasHeight = props.positive("HEIGHT") != null
            val hasNumbers = period.isNotEmpty() || hasHeight || range.isNotEmpty()

            val head = listOf(category, characteristic + group, colour)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .let { if (hasNumbers) "$it " else it } + period
            val tail = if (status.isEmpty()) range else "$range $status"
            return LightDescription(head, tail)
        }

        private fun S57Prop.positive(key: String) = doubleValue(key)?.takeIf { it > 0.0 }

        /**
         * "When the signal group value is set to or include "()" and/or "(1)" there is no
         * requirement for this to be populated in the light description text ... this follows the
         * paper chart convention Mariners are used to seeing."
         */
        private fun String.isDefaultGroup() = replace("()", "").replace("(1)", "").isBlank()

        /**
         * "The default presentation for each numeric value ... is no decimals. If the value of the
         * attribute has non-zero decimal part then the value is displayed to one decimal place."
         */
        internal fun Double.format(): String {
            val tenths = round(this * 10.0) / 10.0
            return if (tenths == floor(tenths)) tenths.toLong().toString() else tenths.toString()
        }

        /** CATLIT - only these three categories have an abbreviation in the spec. */
        private val CATLIT = mapOf(
            1 to "Dir",
            5 to "Aero",
            7 to "Fog Det Lt",
        )

        /** LITCHR */
        private val LITCHR = mapOf(
            1 to "F",
            2 to "Fl",
            3 to "LFl",
            4 to "Q",
            5 to "VQ",
            6 to "UQ",
            7 to "Iso",
            8 to "Oc",
            9 to "IQ",
            10 to "IVQ",
            11 to "IUQ",
            12 to "Mo",
            13 to "FFl",
            14 to "Fl+LFl",
            15 to "OcFl",
            16 to "FLFl",
            17 to "AlOc",
            18 to "AlLFl",
            19 to "AlFl",
            20 to "Al",
            25 to "Q+LFl",
            26 to "VQ+LFl",
            27 to "UQ+LFl",
            28 to "Al",
            29 to "AlF Fl",
        )

        /**
         * COLOR - the spec lists only W, R, G and Y; the rest of the light colors are the INT 1
         * (IP 11.4-11.8) abbreviations. Black, grey, brown, magenta and pink aren't light colors.
         */
        private val COLOUR = mapOf(
            1 to "W",
            3 to "R",
            4 to "G",
            5 to "Bu",
            6 to "Y",
            9 to "Am",
            10 to "Vi",
            11 to "Or",
        )

        /** STATUS */
        private val STATUS = mapOf(
            2 to "occas",
            7 to "temp",
            8 to "priv",
            11 to "exting",
            17 to "U",
        )
    }
}

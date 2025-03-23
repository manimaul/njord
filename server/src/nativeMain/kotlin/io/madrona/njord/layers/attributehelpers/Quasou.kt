package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/OBSTRN/QUASOU
 * List
 */
enum class Quasou {
    DEPTH_KNOWN,
    DEPTH_UNKNOWN,
    DOUBTFUL_SOUNDING,
    UNRELIABLE_SOUNDING,
    NO_BOTTOM_FOUND_AT_VALUE_SHOWN,
    LEAST_DEPTH_KNOWN,
    LEAST_DEPTH_UNKNOWN_SAFE_CLEARANCE_AT_VALUE_SHOWN,
    VALUE_REPORTED_NOT_SURVEYED,
    VALUE_REPORTED_NOT_CONFIRMED,
    MAINTAINED_DEPTH,
    NOT_REGULARLY_MAINTAINED;

    companion object {
        fun ChartFeature.quasou(): List<Quasou> {
            return props.intValues("QUASOU").mapNotNull {
                when (it) {
                    1 -> DEPTH_KNOWN
                    2 -> DEPTH_UNKNOWN
                    3 -> DOUBTFUL_SOUNDING
                    4 -> UNRELIABLE_SOUNDING
                    5 -> NO_BOTTOM_FOUND_AT_VALUE_SHOWN
                    6 -> LEAST_DEPTH_KNOWN
                    7 -> LEAST_DEPTH_UNKNOWN_SAFE_CLEARANCE_AT_VALUE_SHOWN
                    8 -> VALUE_REPORTED_NOT_SURVEYED
                    9 -> VALUE_REPORTED_NOT_CONFIRMED
                    10 -> MAINTAINED_DEPTH
                    11 -> NOT_REGULARLY_MAINTAINED
                    else -> null
                }
            }

        }
    }
}

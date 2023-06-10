package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/OBSTRN/WATLEV
 * Enum
 */
enum class Watlev {
    PARTLY_SUBMERGED_AT_HIGH_WATER,
    ALWAYS_DRY,
    ALWAYS_UNDER_WATER_SUBMERGED,
    COVERS_AND_UNCOVERS,
    AWASH,
    SUBJECT_TO_INUNDATION_OR_FLOODING,
    FLOATING;

    companion object {
        fun ChartFeature.watlev(): Watlev? {
            return when (props.intValue("WATLEV")) {
                1 -> PARTLY_SUBMERGED_AT_HIGH_WATER
                2 -> ALWAYS_DRY
                3 -> ALWAYS_UNDER_WATER_SUBMERGED
                4 -> COVERS_AND_UNCOVERS
                5 -> AWASH
                6 -> SUBJECT_TO_INUNDATION_OR_FLOODING
                7 -> FLOATING
                else -> null
            }
        }
    }
}

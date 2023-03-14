package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.intValue

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
        fun fromId(id: Int): Watlev? = when (id) {
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

fun S57Prop.watlev() : Watlev? {
    return intValue("WATLEV")?.let { Watlev.fromId(it) }
}

package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Filters

/**
 * https://openenc.com/control/symbols/MORFAC/CATMOR
 * Enum
 */
enum class Catmor {
    DOLPHIN,
    DEVIATION_DOLPHIN,
    BOLLARD,
    TIE_UP_WALL,
    POST_OR_PILE,
    CHAIN_WIRE_CABLE,
    MOORING_BUOY;

    fun filterEq() : List<Any> {
        return listOf(Filters.eq, "CATMOR", ordinal + 1)
    }

    fun filterNotEq() : List<Any> {
        return listOf(Filters.notEq, "CATMOR", ordinal + 1)
    }

    companion object {
        fun ChartFeature.catmor(): Catmor? {
            return when (props.intValue("CATMOR")) {
                1 -> DOLPHIN
                2 -> DEVIATION_DOLPHIN
                3 -> BOLLARD
                4 -> TIE_UP_WALL
                5 -> POST_OR_PILE
                6 -> CHAIN_WIRE_CABLE
                7 -> MOORING_BUOY
                else -> null
            }
        }
    }
}

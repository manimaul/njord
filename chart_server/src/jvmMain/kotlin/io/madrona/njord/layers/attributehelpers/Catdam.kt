package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/*
 * https://openenc.com/control/symbols/DAMCON/CATDAM
 * Enum
 */
enum class Catdam {
    WEIR,
    DAM,
    FLOOD_BARRAGE;

    companion object {
        fun ChartFeature.catdam() : Catdam? {
            return when (props.intValue("CATDAM")) {
                1 -> WEIR
                2 -> DAM
                3 -> FLOOD_BARRAGE
                else -> null
            }
        }
    }
}
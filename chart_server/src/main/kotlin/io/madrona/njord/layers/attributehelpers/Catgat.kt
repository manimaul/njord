package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/GATCON/CATGAT
 * Enum
 */
enum class Catgat {
    FLOOD_BARRAGE_GATE,
    CAISSON,
    LOCK_GATE,
    DYKE_GATE;

    companion object {

        fun ChartFeature.catgat(): Catgat? {
            return props.intValue("CATGAT").let {
                when (it) {
                    2 -> FLOOD_BARRAGE_GATE
                    3 -> CAISSON
                    4 -> LOCK_GATE
                    5 -> DYKE_GATE
                    else -> null
                }
            }
        }
    }
}

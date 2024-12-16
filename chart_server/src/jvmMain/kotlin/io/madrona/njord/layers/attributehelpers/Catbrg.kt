package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/BRIDGE/CATBRG
 * List
 */
enum class Catbrg {
    FIXED_BRIDGE,
    OPENING_BRIDGE,
    SWING_BRIDGE,
    LIFTING_BRIDGE,
    BASCULE_BRIDGE,
    PONTOON_BRIDGE,
    DRAW_BRIDGE,
    TRANSPORTER_BRIDGE,
    FOOTBRIDGE,
    VIADUCT,
    AQUEDUCT,
    SUSPENSION_BRIDGE;

    companion object {

        fun ChartFeature.catbrg(): List<Catbrg> {
            return props.intValues("CATBRG").mapNotNull {
                when (it) {
                    1 -> FIXED_BRIDGE
                    2 -> OPENING_BRIDGE
                    3 -> SWING_BRIDGE
                    4 -> LIFTING_BRIDGE
                    5 -> BASCULE_BRIDGE
                    6 -> PONTOON_BRIDGE
                    7 -> DRAW_BRIDGE
                    8 -> TRANSPORTER_BRIDGE
                    9 -> FOOTBRIDGE
                    10 -> VIADUCT
                    11 -> AQUEDUCT
                    12 -> SUSPENSION_BRIDGE
                    else -> null
                }
            }
        }
    }
}

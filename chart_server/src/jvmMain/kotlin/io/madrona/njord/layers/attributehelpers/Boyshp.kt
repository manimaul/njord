package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/BOYSPP/BOYSHP
 * Enum
 */
enum class Boyshp {
    CONICAL,
    CAN,
    SPHERICAL,
    PILLAR,
    SPAR,
    BARREL,
    SUPERBUOY,
    ICEBUOY;

    companion object {

        fun ChartFeature.boyshp() : Boyshp? {
            return when (props.intValue("BOYSHP")) {
                1 -> CONICAL
                2 -> CAN
                3 -> SPHERICAL
                4 -> PILLAR
                5 -> SPAR
                6 -> BARREL
                7 -> SUPERBUOY
                8 -> ICEBUOY
                else -> null
            }

        }
    }
}

package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues

/**
 * https://s57dev.mxmariner.com/control/symbols/BOYSPP/BOYSHP
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
        fun fromId(id: Int): Boyshp? = when (id) {
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

fun S57Prop.boyshp() : Boyshp? {
    return intValue("BOYSHP")?.let { Boyshp.fromId(it) }
}

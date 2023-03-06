package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.intValue

/**
 * https://s57dev.mxmariner.com/control/symbols/SILTNK/CONVIS
 * Enum
 */
enum class Convis {
    VISUAL_CONSPICUOUS,
    NOT_VISUAL_CONSPICUOUS;

    companion object {
        fun fromId(id: Int): Convis? = when (id) {
            1 -> VISUAL_CONSPICUOUS
            2 -> NOT_VISUAL_CONSPICUOUS
            else -> null
        }
    }
}

fun S57Prop.convis() : Convis? {
    return intValue("CONVIS")?.let { Convis.fromId(it) }
}

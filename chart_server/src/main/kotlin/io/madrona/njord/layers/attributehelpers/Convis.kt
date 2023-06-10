package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/SILTNK/CONVIS
 * Enum
 */
enum class Convis {
    VISUAL_CONSPICUOUS,
    NOT_VISUAL_CONSPICUOUS;

    companion object {
        fun ChartFeature.convis(): Convis? {
            return when (props.intValue("CONVIS")) {
                1 -> VISUAL_CONSPICUOUS
                2 -> NOT_VISUAL_CONSPICUOUS
                else -> null
            }
        }
    }
}

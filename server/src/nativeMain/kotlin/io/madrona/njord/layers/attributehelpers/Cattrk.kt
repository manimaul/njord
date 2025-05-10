package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/RECTRC/CATTRK
 * Enum
 */
enum class Cattrk {
    BASED_ON_A_SYSTEM_OF_MARKS,
    NOT_BASED_ON_A_SYSTEM_OF_MARKS;

    companion object {

        fun ChartFeature.cattrk(): Cattrk? {
            return props.intValue("CATTRK")?.let{
                when (it) {
                    1 -> BASED_ON_A_SYSTEM_OF_MARKS
                    2 -> NOT_BASED_ON_A_SYSTEM_OF_MARKS
                    else -> null
                }
            }
        }
    }
}

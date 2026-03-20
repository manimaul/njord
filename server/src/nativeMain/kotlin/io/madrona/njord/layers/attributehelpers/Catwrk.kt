package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/WRECKS/CATWRK
 * Enum
 */
enum class Catwrk {

    NON_DANGEROUS_WRECK,
    DANGEROUS_WRECK,
    DISTRIBUTED_REMAINS_OF_WRECK,
    WRECK_SHOWING_MAST_MASTS,
    WRECK_SHOWING_ANY_PORTION_OF_HULL_OR_SUPERSTRUCTURE;

    companion object {

        fun ChartFeature.catwrk(): Catwrk? {
            return props.intValue("CATWRK")?.let {
                when (it) {
                    1 -> NON_DANGEROUS_WRECK
                    2 -> DANGEROUS_WRECK
                    3 -> DISTRIBUTED_REMAINS_OF_WRECK
                    4 -> WRECK_SHOWING_MAST_MASTS
                    5 -> WRECK_SHOWING_ANY_PORTION_OF_HULL_OR_SUPERSTRUCTURE
                    else -> null
                }
            }
        }
    }
}

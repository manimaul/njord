package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/BCNCAR/CATCAM
 * Enum
 */
enum class Catcam {
    NORTH_CARDINAL_MARK,
    EAST_CARDINAL_MARK,
    SOUTH_CARDINAL_MARK,
    WEST_CARDINAL_MARK;

    companion object {

        fun ChartFeature.catcam(): Catcam? {
            return props.intValue("CATCAM").let {
                when (it) {
                    1 -> NORTH_CARDINAL_MARK
                    2 -> EAST_CARDINAL_MARK
                    3 -> SOUTH_CARDINAL_MARK
                    4 -> WEST_CARDINAL_MARK
                    else -> null
                }
            }
        }
    }
}

package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/PIPARE/CATPIP
 *
 * List
 */
enum class Catpip {
    OUTFALL_PIPE,
    INTAKE_PIPE,
    SEWER,
    BUBBLER_SYSTEM,
    SUPPLY_PIPE;

    companion object {
        fun ChartFeature.catpip(): List<Catpip> {
            return props.intValues("CATPIP").mapNotNull {
                when (it) {
                    2 -> OUTFALL_PIPE
                    3 -> INTAKE_PIPE
                    4 -> SEWER
                    5 -> BUBBLER_SYSTEM
                    6 -> SUPPLY_PIPE
                    else -> null
                }
            }
        }
    }
}

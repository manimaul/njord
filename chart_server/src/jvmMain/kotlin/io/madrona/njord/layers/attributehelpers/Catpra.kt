package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/PRDARE/CATPRA
 * Enum
 */
enum class Catpra {
    QUARRY,
    MINE,
    STOCKPILE,
    POWER_STATION_AREA,
    REFINERY_AREA,
    TIMBER_YARD,
    FACTORY_AREA,
    TANK_FARM,
    WIND_FARM;

    companion object {

        fun ChartFeature.catpra(): Catpra? {
            return props.intValue("CATPRA")?.let {
                when (it) {
                    1 -> QUARRY
                    2 -> MINE
                    3 -> STOCKPILE
                    4 -> POWER_STATION_AREA
                    5 -> REFINERY_AREA
                    6 -> TIMBER_YARD
                    7 -> FACTORY_AREA
                    8 -> TANK_FARM
                    9 -> WIND_FARM
                    else -> null
                }
            }
        }
    }
}

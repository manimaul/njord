package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/LNDRGN/CATLND
 * List
 */
enum class Catlnd {
    FEN,
    MARSH,
    MOOR_BOG,
    HEATHLAND,
    MOUNTAIN_RANGE,
    LOWLANDS,
    CANYON_LANDS,
    PADDY_FIELD,
    AGRICULTURAL_LAND,
    SAVANNA_GRASSLAND,
    PARKLAND,
    SWAMP,
    LANDSLIDE,
    LAVA_FLOW,
    SALT_PAN,
    MORAINE,
    CRATER,
    CAVE,
    ROCK_COLUMN_OR_PINNACLE;

    companion object {
        fun ChartFeature.catlnd(): List<Catlnd> {
            return props.intValues("CATLND").mapNotNull {
                when (it) {
                    1 -> FEN
                    2 -> MARSH
                    3 -> MOOR_BOG
                    4 -> HEATHLAND
                    5 -> MOUNTAIN_RANGE
                    6 -> LOWLANDS
                    7 -> CANYON_LANDS
                    8 -> PADDY_FIELD
                    9 -> AGRICULTURAL_LAND
                    10 -> SAVANNA_GRASSLAND
                    11 -> PARKLAND
                    12 -> SWAMP
                    13 -> LANDSLIDE
                    14 -> LAVA_FLOW
                    15 -> SALT_PAN
                    16 -> MORAINE
                    17 -> CRATER
                    18 -> CAVE
                    19 -> ROCK_COLUMN_OR_PINNACLE
                    else -> null
                }
            }
        }
    }
}

package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/SEAARE/CATSEA
 * Enum
 */
enum class Catsea {
    GAT,
    BANK,
    DEEP,
    BAY,
    TRENCH,
    BASIN,
    MUD_FLATS,
    REEF,
    LEDGE,
    CANYON,
    NARROWS,
    SHOAL,
    KNOLL,
    RIDGE,
    SEAMOUNT,
    PINNACLE,
    ABYSSAL_PLAIN,
    PLATEAU,
    SPUR,
    SHELF,
    TROUGH,
    SADDLE,
    ABYSSAL_HILLS,
    APRON,
    ARCHIPELAGIC_APRON,
    BORDERLAND,
    CONTINENTAL_MARGIN,
    CONTINENTAL_RISE,
    ESCARPMENT,
    FAN,
    FRACTURE_ZONE,
    GAP,
    GUYOT,
    HILL,
    HOLE,
    LEVEE,
    MEDIAN_VALLEY,
    MOAT,
    MOUNTAINS,
    PEAK,
    PROVINCE,
    RISE,
    SEACHANNEL,
    SEAMOUNT_CHAIN,
    SHELF_EDGE,
    SILL,
    SLOPE,
    TERRACE,
    VALLEY,
    CANAL,
    LAKE,
    RIVER;

    companion object {
        fun ChartFeature.catsea(): Catsea? {
            return when (props.intValue("CATSEA")) {
                2 -> GAT
                3 -> BANK
                4 -> DEEP
                5 -> BAY
                6 -> TRENCH
                7 -> BASIN
                8 -> MUD_FLATS
                9 -> REEF
                10 -> LEDGE
                11 -> CANYON
                12 -> NARROWS
                13 -> SHOAL
                14 -> KNOLL
                15 -> RIDGE
                16 -> SEAMOUNT
                17 -> PINNACLE
                18 -> ABYSSAL_PLAIN
                19 -> PLATEAU
                20 -> SPUR
                21 -> SHELF
                22 -> TROUGH
                23 -> SADDLE
                24 -> ABYSSAL_HILLS
                25 -> APRON
                26 -> ARCHIPELAGIC_APRON
                27 -> BORDERLAND
                28 -> CONTINENTAL_MARGIN
                29 -> CONTINENTAL_RISE
                30 -> ESCARPMENT
                31 -> FAN
                32 -> FRACTURE_ZONE
                33 -> GAP
                34 -> GUYOT
                35 -> HILL
                36 -> HOLE
                37 -> LEVEE
                38 -> MEDIAN_VALLEY
                39 -> MOAT
                40 -> MOUNTAINS
                41 -> PEAK
                42 -> PROVINCE
                43 -> RISE
                44 -> SEACHANNEL
                45 -> SEAMOUNT_CHAIN
                46 -> SHELF_EDGE
                47 -> SILL
                48 -> SLOPE
                49 -> TERRACE
                50 -> VALLEY
                51 -> CANAL
                52 -> LAKE
                53 -> RIVER
                else -> null
            }
        }
    }
}

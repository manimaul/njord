package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/PIPARE/PRODCT
 *
 * List
 */
enum class Prodct {
    OIL,
    GAS,
    WATER,
    STONE,
    COAL,
    ORE,
    CHEMICALS,
    DRINKING_WATER,
    MILK,
    BAUXITE,
    COKE,
    IRON_INGOTS,
    SALT,
    SAND,
    TIMBER,
    SAWDUST_WOOD_CHIPS,
    SCRAP_METAL,
    LIQUIFIED_NATURAL_GAS,
    LIQUIFIED_PETROLEUM_GAS,
    WINE,
    CEMENT,
    GRAIN;


    companion object {
        fun ChartFeature.prodct(): List<Prodct> {
            return props.intValues("PRODCT").mapNotNull {
                when (it) {
                    1 -> OIL
                    2 -> GAS
                    3 -> WATER
                    4 -> STONE
                    5 -> COAL
                    6 -> ORE
                    7 -> CHEMICALS
                    8 -> DRINKING_WATER
                    9 -> MILK
                    10 -> BAUXITE
                    11 -> COKE
                    12 -> IRON_INGOTS
                    13 -> SALT
                    14 -> SAND
                    15 -> TIMBER
                    16 -> SAWDUST_WOOD_CHIPS
                    17 -> SCRAP_METAL
                    18 -> LIQUIFIED_NATURAL_GAS
                    19 -> LIQUIFIED_PETROLEUM_GAS
                    20 -> WINE
                    21 -> CEMENT
                    22 -> GRAIN
                    else -> null
                }
            }
        }
    }
}

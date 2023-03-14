package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.intValue

/**
 * https://openenc.com/control/symbols/SILTNK/CATSIL
 * Enum
 * 1	silo in general
 * 2	tank in general
 * 3	grain elevator
 * 4	water tower
 */
enum class Catsil {
    SILO_IN_GENERAL,
    TANK_IN_GENERAL,
    GRAIN_ELEVATOR,
    WATER_TOWER;

    companion object {
        fun fromId(id: Int): Catsil? = when (id) {
            1 -> SILO_IN_GENERAL
            2 -> TANK_IN_GENERAL
            3 -> GRAIN_ELEVATOR
            4 -> WATER_TOWER
            else -> null
        }
    }
}

fun S57Prop.catsil() : Catsil? {
    return intValue("CATSIL")?.let { Catsil.fromId(it) }
}

package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/BCNLAT/BCNSHP
 * Enum
 */
enum class Bcnshp {
    STAKE_POLE_PERCH_POST,
    WHITY,
    BEACON_TOWER,
    LATTICE_BEACON,
    PILE_BEACON,
    CAIRN,
    BUOYANT_BEACON;

    companion object {

        fun ChartFeature.bcnshp(): Bcnshp? {
            return when (props.intValue("BCNSHP")) {
                1 -> STAKE_POLE_PERCH_POST
                2 -> WHITY
                3 -> BEACON_TOWER
                4 -> LATTICE_BEACON
                5 -> PILE_BEACON
                6 -> CAIRN
                7 -> BUOYANT_BEACON
                else -> null
            }
        }
    }
}

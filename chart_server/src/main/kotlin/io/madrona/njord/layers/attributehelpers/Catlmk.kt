package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/LNDMRK/CATLMK
 * Attribute: Category of landmark
 *
 * Acronym: CATLMK
 *
 * Code: 35
 *
 * ID	Meaning
 * 1	cairn
 * 2	cemetery
 * 3	chimney
 * 4	dish aerial
 * 5	flagstaff (flagpole)
 * 6	flare stack
 * 7	mast
 * 8	windsock
 * 9	monument
 * 10	column (pillar)
 * 11	memorial plaque
 * 12	obelisk
 * 13	statue
 * 14	cross
 * 15	dome
 * 16	radar scanner
 * 17	tower
 * 18	windmill
 * 19	windmotor
 * 20	spire/minaret
 *
 * Attribute type: L
 */
enum class Catlmk {
    CAIRN,
    CEMETERY,
    CHIMNEY,
    DISH_AERIAL,
    FLAGSTAFF_FLAGPOLE,
    FLARE_STACK,
    MAST,
    WINDSOCK,
    MONUMENT,
    COLUMN_PILLAR,
    MEMORIAL_PLAQUE,
    OBELISK,
    STATUE,
    CROSS,
    DOME,
    RADAR_SCANNER,
    TOWER,
    WINDMILL,
    WINDMOTOR,
    SPIRE_MINARET;

    companion object {

        fun ChartFeature.catlmk(): List<Catlmk> {
            return props.intValues("CATLMK").mapNotNull {
                when (it) {
                    1 -> CAIRN
                    2 -> CEMETERY
                    3 -> CHIMNEY
                    4 -> DISH_AERIAL
                    5 -> FLAGSTAFF_FLAGPOLE
                    6 -> FLARE_STACK
                    7 -> MAST
                    8 -> WINDSOCK
                    9 -> MONUMENT
                    10 -> COLUMN_PILLAR
                    11 -> MEMORIAL_PLAQUE
                    12 -> OBELISK
                    13 -> STATUE
                    14 -> CROSS
                    15 -> DOME
                    16 -> RADAR_SCANNER
                    17 -> TOWER
                    18 -> WINDMILL
                    19 -> WINDMOTOR
                    20 -> SPIRE_MINARET
                    else -> null
                }
            }
        }
    }
}

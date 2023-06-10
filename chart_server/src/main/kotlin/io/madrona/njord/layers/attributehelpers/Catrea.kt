package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/RESARE/CATREA
 * List
 */
enum class Catrea {
    OFFSHORE_SAFETY_ZONE,
    NATURE_RESERVE,
    BIRD_SANCTUARY,
    GAME_PRESERVE,
    SEAL_SANCTUARY,
    DEGAUSSING_RANGE,
    MILITARY_AREA,
    HISTORIC_WRECK_AREA,
    NAVIGATIONAL_AID_SAFETY_ZONE,
    MINEFIELD,
    SWIMMING_AREA,
    WAITING_AREA,
    RESEARCH_AREA,
    DREDGING_AREA,
    FISH_SANCTUARY,
    ECOLOGICAL_RESERVE,
    NO_WAKE_AREA,
    SWINGING_AREA;

    companion object {
        fun ChartFeature.catrea(): List<Catrea> {
            return props.intValues("CATREA").mapNotNull {
                when (it) {
                    1 -> OFFSHORE_SAFETY_ZONE
                    4 -> NATURE_RESERVE
                    5 -> BIRD_SANCTUARY
                    6 -> GAME_PRESERVE
                    7 -> SEAL_SANCTUARY
                    8 -> DEGAUSSING_RANGE
                    9 -> MILITARY_AREA
                    10 -> HISTORIC_WRECK_AREA
                    12 -> NAVIGATIONAL_AID_SAFETY_ZONE
                    14 -> MINEFIELD
                    18 -> SWIMMING_AREA
                    19 -> WAITING_AREA
                    20 -> RESEARCH_AREA
                    21 -> DREDGING_AREA
                    22 -> FISH_SANCTUARY
                    23 -> ECOLOGICAL_RESERVE
                    24 -> NO_WAKE_AREA
                    25 -> SWINGING_AREA
                    else -> null
                }
            }
        }
    }
}

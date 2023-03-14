package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.intValues

/**
 * https://openenc.com/control/symbols/RESARE/RESTRN
 * List
 */
enum class Restrn {
    ANCHORING_PROHIBITED,
    ANCHORING_RESTRICTED,
    FISHING_PROHIBITED,
    FISHING_RESTRICTED,
    TRAWLING_PROHIBITED,
    TRAWLING_RESTRICTED,
    ENTRY_PROHIBITED,
    ENTRY_RESTRICTED,
    DREDGING_PROHIBITED,
    DREDGING_RESTRICTED,
    DIVING_PROHIBITED,
    DIVING_RESTRICTED,
    NO_WAKE,
    AREA_TO_BE_AVOIDED,
    CONSTRUCTION_PROHIBITED;

    companion object {
        fun fromId(id: Int): Restrn? = when (id) {
            1 -> ANCHORING_PROHIBITED
            2 -> ANCHORING_RESTRICTED
            3 -> FISHING_PROHIBITED
            4 -> FISHING_RESTRICTED
            5 -> TRAWLING_PROHIBITED
            6 -> TRAWLING_RESTRICTED
            7 -> ENTRY_PROHIBITED
            8 -> ENTRY_RESTRICTED
            9 -> DREDGING_PROHIBITED
            10 -> DREDGING_RESTRICTED
            11 -> DIVING_PROHIBITED
            12 -> DIVING_RESTRICTED
            13 -> NO_WAKE
            14 -> AREA_TO_BE_AVOIDED
            15 -> CONSTRUCTION_PROHIBITED
            else -> null
        }
    }
}

fun S57Prop.restrn(): List<Restrn> {
    return intValues("RESTRN").mapNotNull { Restrn.fromId(it) }
}

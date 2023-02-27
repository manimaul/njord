package io.madrona.njord.layers.attributehelpers

/**
 * https://s57dev.mxmariner.com/control/symbols/OBSTRN/CATOBS
 * Enum
 */
enum class Catobs {
    SNAG_STUMP,
    WELLHEAD,
    DIFFUSER,
    CRIB,
    FISH_HAVEN,
    FOUL_AREA,
    FOUL_GROUND,
    ICE_BOOM,
    GROUND_TACKLE,
    BOOM;

    companion object {
        fun fromId(id: Int): Catobs? = when (id) {
            1 -> SNAG_STUMP
            2 -> WELLHEAD
            3 -> DIFFUSER
            4 -> CRIB
            5 -> FISH_HAVEN
            6 -> FOUL_AREA
            7 -> FOUL_GROUND
            8 -> ICE_BOOM
            9 -> GROUND_TACKLE
            10 -> BOOM
            else -> null
        }
    }
}
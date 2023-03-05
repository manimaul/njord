package io.madrona.njord.layers.attributehelpers

/**
 * https://s57dev.mxmariner.com/control/symbols/BRIDGE/CATBRG
 * List
 */
enum class Catbrg {
    FIXED_BRIDGE,
    OPENING_BRIDGE,
    SWING_BRIDGE,
    LIFTING_BRIDGE,
    BASCULE_BRIDGE,
    PONTOON_BRIDGE,
    DRAW_BRIDGE,
    TRANSPORTER_BRIDGE,
    FOOTBRIDGE,
    VIADUCT,
    AQUEDUCT,
    SUSPENSION_BRIDGE;

    companion object {
        fun fromId(id: Int): Catbrg? = when (id) {
            1 -> FIXED_BRIDGE
            2 -> OPENING_BRIDGE
            3 -> SWING_BRIDGE
            4 -> LIFTING_BRIDGE
            5 -> BASCULE_BRIDGE
            6 -> PONTOON_BRIDGE
            7 -> DRAW_BRIDGE
            8 -> TRANSPORTER_BRIDGE
            9 -> FOOTBRIDGE
            10 -> VIADUCT
            11 -> AQUEDUCT
            12 -> SUSPENSION_BRIDGE
            else -> null
        }
    }
}
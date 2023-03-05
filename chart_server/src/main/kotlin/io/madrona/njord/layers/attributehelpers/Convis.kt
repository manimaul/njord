package io.madrona.njord.layers.attributehelpers

/**
 * https://s57dev.mxmariner.com/control/symbols/SILTNK/CONVIS
 * Enum
 */
enum class Convis {
    VISUAL_CONSPICUOUS,
    NOT_VISUAL_CONSPICUOUS;

    companion object {
        fun fromId(id: Int): Convis? = when (id) {
            1 -> VISUAL_CONSPICUOUS
            2 -> NOT_VISUAL_CONSPICUOUS
            else -> null
        }
    }
}

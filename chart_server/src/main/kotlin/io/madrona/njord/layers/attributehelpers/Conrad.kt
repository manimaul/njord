package io.madrona.njord.layers.attributehelpers

/**
 * https://openenc.com/control/symbols/CONVYR/CONRAD
 * Enum
 */
enum class Conrad {
    RADAR_CONSPICUOUS,
    NOT_RADAR_CONSPICUOUS,
    RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR;

    companion object {
        fun fromId(id: Int): Conrad? = when (id) {
            1 -> RADAR_CONSPICUOUS
            2 -> NOT_RADAR_CONSPICUOUS
            3 -> RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR
            else -> null
        }
    }
}
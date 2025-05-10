package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/CONVYR/CONRAD
 * Enum
 */
enum class Conrad {
    RADAR_CONSPICUOUS,
    NOT_RADAR_CONSPICUOUS,
    RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR;

    companion object {
        fun ChartFeature.conrad(): Conrad? {
            return when (props.intValue("CONRAD")) {
                1 -> RADAR_CONSPICUOUS
                2 -> NOT_RADAR_CONSPICUOUS
                3 -> RADAR_CONSPICUOUS_HAS_RADAR_REFLECTOR
                else -> null
            }
        }
    }
}
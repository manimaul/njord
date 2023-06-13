package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/BOYLAT/CATLAM
 *
 * Attribute: Category of lateral mark
 *
 * Acronym: CATLAM
 *
 * Code: 36
 *
 * ID	Meaning
 * 1	port-hand lateral mark
 * 2	starboard-hand lateral mark
 * 3	preferred channel to starboard lateral mark
 * 4	preferred channel to port lateral mark
 *
 * Attribute type: E
 */
enum class Catlam {
PORT_HAND_LATERAL_MARK,
STARBOARD_HAND_LATERAL_MARK,
PREFERRED_CHANNEL_TO_STARBOARD_LATERAL_MARK,
PREFERRED_CHANNEL_TO_PORT_LATERAL_MARK;

    companion object {

        fun ChartFeature.catlam(): Catlam? {
            return props.intValue("CATLAM").let {
                when (it) {
                    1 -> PORT_HAND_LATERAL_MARK
                    2 -> STARBOARD_HAND_LATERAL_MARK
                    3 -> PREFERRED_CHANNEL_TO_STARBOARD_LATERAL_MARK
                    4 -> PREFERRED_CHANNEL_TO_PORT_LATERAL_MARK
                    else -> null
                }
            }
        }
    }
}

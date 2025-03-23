package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/DWRTPT/TRAFIC
 *
 * Attribute: Traffic flow
 *
 * Acronym: TRAFIC
 *
 * Code: 172
 *
 * ID	Meaning
 * 1	inbound
 * 2	outbound
 * 3	one-way
 * 4	two-way
 *
 */
enum class Trafic {
   INBOUND,
   OUTBOUND,
   ONE_WAY,
   TWO_WAY;

    companion object {

        fun ChartFeature.trafic(): Trafic? {
            return props.intValue("TRAFIC").let {
                when (it) {
                    1 -> INBOUND
                    2 -> OUTBOUND
                    3 -> ONE_WAY
                    4 -> TWO_WAY
                    else -> null
                }
            }
        }
    }
}

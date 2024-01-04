package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/FSHFAC/CATFIF
 *
 * Attribute: Category of fishing facility
 *
 * Acronym: CATFIF
 *
 * Code: 26
 * Enum
 */
enum class Catfif {
    	FISHING_STAKE,
    	FISH_TRAP,
    	FISH_WEIR,
    	TUNNY_NET;

    companion object {
        fun ChartFeature.catfif(): Catfif? {
            return when (props.intValue("CATFIF")) {
                1 -> FISHING_STAKE
                2 -> FISH_TRAP
                3 -> FISH_WEIR
                4 -> TUNNY_NET
                else -> null
            }
        }
    }
}

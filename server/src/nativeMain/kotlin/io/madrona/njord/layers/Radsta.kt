package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point
 *
 * Object: Radar station
 *
 * Acronym: RADSTA
 *
 * Code: 104
 *
 * S-52 lookups: `SY(POSGEN01)`, or for a radar surveillance station (CATRAS 2)
 * `SY(RDOSTA02);TE('ch %s','COMCHA',3,1,2,'15110',0,0,CHBLK,11)`.
 */
class Radsta : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.props.intValue("CATRAS")) {
            2 -> {
                feature.pointSymbol(Sprite.RDOSTA02)
                feature.props[SURVEILLANCE] = true.json
            }
            else -> feature.pointSymbol(Sprite.POSGEN01)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        textLayer(
            textField = listOf("concat", "ch ", listOf("to-string", listOf("get", "COMCHA"))).json,
            theme = options.theme,
            textAnchor = Anchor.BOTTOM_LEFT,
            textJustify = TextJustify.LEFT,
            filter = listOf(Filters.all, listOf("has", SURVEILLANCE), listOf("has", "COMCHA")).json,
        ),
    )

    companion object {
        private const val SURVEILLANCE = "_SURV"
    }
}

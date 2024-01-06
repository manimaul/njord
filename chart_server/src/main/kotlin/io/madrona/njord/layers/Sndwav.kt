package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Sand waves
 *
 * Acronym: SNDWAV
 *
 * Code: 118
 */
class Sndwav : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(Color.CHGRD)
        feature.areaPattern(Sprite.SNDWAV01P)
        feature.pointSymbol(Sprite.SNDWAV02)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRD,
            style = LineStyle.DashLine
        ),
        areaLayerWithPointSymbol(),
//        areaLayerWithFillPattern(),
    )
}

package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catfif
import io.madrona.njord.layers.attributehelpers.Catfif.Companion.catfif
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Fishing facility
 *
 * Acronym: FSHFAC
 *
 * Code: 55
 */
class Fshfac : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.lineColor(Color.CHGRD)
        when (feature.catfif()) {
            Catfif.FISHING_STAKE -> {
                feature.pointSymbol(Sprite.FSHFAC03)
                feature.areaPattern(Sprite.FSHFAC03)
            }
            Catfif.FISH_TRAP,
            Catfif.FISH_WEIR,
            Catfif.TUNNY_NET -> {
                feature.pointSymbol(Sprite.FSHFAC02)
                feature.areaPattern(Sprite.FSHFAC04P)
            }
            null -> {
                feature.pointSymbol(Sprite.FSHFAC02)
                feature.areaPattern(Sprite.FSHHAV02P)

            }
        }

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            color = Color.CHGRD,
            theme = options.theme,
            style = LineStyle.DashLine,
            width = 1f,
        ),
        pointLayerFromSymbol(),
    )
}

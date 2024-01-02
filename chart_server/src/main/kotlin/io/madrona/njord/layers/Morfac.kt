package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catmor
import io.madrona.njord.layers.attributehelpers.Catmor.Companion.catmor
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Mooring/warping facility
 *
 * Acronym: MORFAC
 *
 * Code: 84
 */
class Morfac : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        val sprite = when (feature.catmor()) {
            Catmor.DOLPHIN -> Sprite.MORFAC03
            Catmor.DEVIATION_DOLPHIN -> Sprite.MORFAC04
            Catmor.BOLLARD -> Sprite.PILPNT02
            Catmor.POST_OR_PILE -> Sprite.PILPNT02
            Catmor.MOORING_BUOY -> Sprite.BOYMOR11
            Catmor.TIE_UP_WALL,
            Catmor.CHAIN_WIRE_CABLE,
            null -> Sprite.MORFAC03
        }
        feature.pointSymbol(sprite)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(theme = options.theme, color = Color.CHBRN),
        lineLayerWithColor(theme = options.theme, color = Color.CHBRN, width = 1f),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CSTLN, width = 2f,
            filter = listOf(
                Filters.all,
                Filters.eqTypeLineString,
                Catmor.TIE_UP_WALL.filterEq()
            ),
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHMGF,
            width = 1f,
            style = LineStyle.CustomDash(5f, 5f),
            filter = listOf(
                Filters.all,
                Filters.eqTypeLineString,
                Catmor.CHAIN_WIRE_CABLE.filterEq()
            ),
        ),
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CSTLN,
            width = 2f,
            filter = listOf(
                Filters.all,
                Filters.eqTypeLineString,
                Catmor.CHAIN_WIRE_CABLE.filterNotEq(),
                Catmor.TIE_UP_WALL.filterNotEq()
            ),
        ),
        pointLayerFromSymbol(iconKeepUpright = true),
    )
}

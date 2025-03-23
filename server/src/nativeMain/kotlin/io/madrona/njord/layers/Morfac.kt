package io.madrona.njord.layers

import io.madrona.njord.ext.json
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
        when (feature.catmor()) {
            Catmor.DOLPHIN -> feature.pointSymbol(Sprite.MORFAC03)
            Catmor.DEVIATION_DOLPHIN -> feature.pointSymbol(Sprite.MORFAC04)
            Catmor.BOLLARD -> feature.pointSymbol(Sprite.PILPNT02)
            Catmor.POST_OR_PILE -> feature.pointSymbol(Sprite.PILPNT02)
            Catmor.MOORING_BUOY -> feature.pointSymbol(Sprite.BOYMOR11, 2)
            Catmor.TIE_UP_WALL,
            Catmor.CHAIN_WIRE_CABLE,
            null -> feature.pointSymbol(Sprite.MORFAC03)
        }
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
            ).json,
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
            ).json,
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
            ).json,
        ),
        pointLayerFromSymbol(
            symbol = Symbol.Property(),
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
        pointLayerFromSymbol(
            symbol = Symbol.Property(2),
            iconRotationAlignment = IconRotationAlignment.VIEWPORT,
        ),
    )
}

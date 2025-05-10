package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catpra
import io.madrona.njord.layers.attributehelpers.Catpra.Companion.catpra
import io.madrona.njord.layers.attributehelpers.Convis
import io.madrona.njord.layers.attributehelpers.Convis.Companion.convis
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Production / storage area
 *
 * Acronym: PRDARE
 *
 * Code: 97
 */
class Prdare : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        val lineColor = when(feature.convis()) {
            Convis.VISUAL_CONSPICUOUS -> Color.CHBLK
            Convis.NOT_VISUAL_CONSPICUOUS,
            null -> Color.LANDF
        }

        feature.lineColor(lineColor)

        val symbol = when (feature.catpra()) {
            Catpra.QUARRY -> Sprite.QUARRY01
            Catpra.MINE,
            Catpra.STOCKPILE,
            Catpra.POWER_STATION_AREA -> Sprite.PRDINS02
            Catpra.REFINERY_AREA -> Sprite.RFNERY11
            Catpra.TIMBER_YARD -> Sprite.TMBYRD01
            Catpra.FACTORY_AREA -> Sprite.PRDINS02
            Catpra.TANK_FARM -> Sprite.TNKFRM11
            Catpra.WIND_FARM -> Sprite.WNDFRM61
            null -> null
        }

        symbol?.let {
            feature.pointSymbol(it)
            feature.linePattern(it)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
        lineLayerWithColor(
            theme = options.theme,
            options = setOf(Color.LANDF, Color.CHBLK),
            style = LineStyle.DashLine
        ),
        lineLayerWithPattern(),
    )
}

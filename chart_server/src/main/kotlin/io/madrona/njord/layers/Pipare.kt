package io.madrona.njord.layers

import io.madrona.njord.ext.whenAny
import io.madrona.njord.layers.attributehelpers.Catpip
import io.madrona.njord.layers.attributehelpers.Catpip.Companion.catpip
import io.madrona.njord.layers.attributehelpers.Prodct
import io.madrona.njord.layers.attributehelpers.Prodct.Companion.prodct
import io.madrona.njord.model.*


/**
 * Geometry Primitives: Point, Area
 *
 * Object: Pipeline area
 *
 * Acronym: PIPARE
 *
 * Code: 92
 *
 */
class Pipare : Layerable() {
    private val lineColor = Color.CHMGD
    private val symbol = Sprite.CHINFO07

    override fun preTileEncode(feature: ChartFeature) {
        val catpip = feature.catpip()
        val color = catpip.whenAny(
            { it == Catpip.OUTFALL_PIPE || it == Catpip.INTAKE_PIPE },
            { Color.CHGRD }
        ) ?: when (feature.prodct().firstOrNull()) {
            Prodct.WATER -> Color.CHGRD
            Prodct.OIL,
            Prodct.GAS,
            Prodct.STONE,
            Prodct.COAL,
            Prodct.ORE,
            Prodct.CHEMICALS,
            Prodct.DRINKING_WATER,
            Prodct.MILK,
            Prodct.BAUXITE,
            Prodct.COKE,
            Prodct.IRON_INGOTS,
            Prodct.SALT,
            Prodct.SAND,
            Prodct.TIMBER,
            Prodct.SAWDUST_WOOD_CHIPS,
            Prodct.SCRAP_METAL,
            Prodct.LIQUIFIED_NATURAL_GAS,
            Prodct.LIQUIFIED_PETROLEUM_GAS,
            Prodct.WINE,
            Prodct.CEMENT,
            Prodct.GRAIN,
            null -> Color.CHMGD
        }
        feature.lineColor(color = color)
        feature.pointSymbol(symbol = symbol)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(color = lineColor, style = LineStyle.DashLine),
        pointLayerFromSymbol(symbol = symbol),
    )
}

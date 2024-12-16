package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Restrn
import io.madrona.njord.layers.attributehelpers.Restrn.Companion.restrn
import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.IconRotationAlignment

/**
 * Geometry Primitives: Area
 *
 * Object: Restricted area
 *
 * Acronym: RESARE
 *
 * Code: 112
 */
class Resare : Layerable() {
    private val lineColor = Color.CHMGD

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.restrn().also { restrictions ->
            restrictions.firstOrNull {
                it == Restrn.ENTRY_RESTRICTED
                        || it == Restrn.ENTRY_PROHIBITED
            }?.let {
                feature.pointSymbol(Sprite.ENTRES51)
            }
            restrictions.firstOrNull {
                it == Restrn.ANCHORING_PROHIBITED
                        || it == Restrn.ANCHORING_RESTRICTED
            }?.let {
                feature.pointSymbol(Sprite.ACHRES51)
            }
        }
        feature.lineColor(lineColor)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
       lineLayerWithColor(theme = options.theme, color = lineColor, style = LineStyle.CustomDash(3f, 2f)) ,
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP,
        ),
    )
}

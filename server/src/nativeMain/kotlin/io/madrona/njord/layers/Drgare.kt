package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Dredged area
 *
 * Acronym: DRGARE
 *
 * Code: 46
 */
class Drgare(
    private val config: ChartsConfig = Singletons.config
) : Layerable() {

    private val areaFillColors = setOf(
        Color.DEPIT,
        Color.DEPVS,
        Color.DEPMS,
        Color.DEPMD,
        Color.DEPDW,
    )

    override suspend fun preTileEncode(feature: ChartFeature) {
        var ac = Color.DEPVS
        feature.props.floatValue("DRVAL1")?.let { drval1 ->
            ac = when {
                drval1 <= 0.0f -> Color.DEPIT
                drval1 <= config.shallowDepth -> Color.DEPVS
                drval1 <= config.safetyDepth -> Color.DEPMS
                drval1 <= config.deepDepth -> Color.DEPMD
                else -> Color.DEPDW
            }
        }
        feature.areaColor(ac)
        feature.areaPattern(Sprite.DRGARE01P)
        feature.lineColor(Color.CHGRD)
    }

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            areaLayerWithFillColor(theme = options.theme, options = areaFillColors),
            areaLayerWithFillPattern(),
            lineLayerWithColor(theme = options.theme, width = 0.5f, options = setOf(Color.CHGRD), style = LineStyle.DashLine),
        )
    }
}

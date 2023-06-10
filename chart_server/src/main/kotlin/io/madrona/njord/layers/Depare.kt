package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Depth area
 *
 * Acronym: DEPARE
 *
 * Code: 42
 */
open class Depare(
    private val config: ChartsConfig = Singletons.config
) : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            //DRVAL1 is lower (sometimes negative) (shallower) end of range
            //DRVAL2 is higher (deeper) end of range
            areaLayerWithFillColor(),
            lineLayerWithColor(width = 0.5f),
        )
    }

    override fun preTileEncode(feature: ChartFeature) {
        var ac = Color.CHBLK
        feature.props.floatValue("DRVAL1")?.let { shallowRange ->
            feature.props.floatValue("DRVAL2")?.let { deepRange ->
                ac = when {
                    shallowRange < 0.0f && deepRange <= 0.0f -> Color.DEPIT
                    shallowRange <= config.shallowDepth -> Color.DEPVS
                    shallowRange <= config.safetyDepth -> Color.DEPMS
                    shallowRange <= config.deepDepth -> Color.DEPMD
                    shallowRange > config.deepDepth -> Color.DEPDW
                    else -> throw IllegalStateException("unexpected DRVAL1 $shallowRange")
                }
                log.debug("finding area fill color for $key $ac DRVAL1=$shallowRange DRVAL2=$deepRange")
            }
        }
        feature.areaColor(ac)
        feature.lineColor(Color.CHGRD)
    }
}

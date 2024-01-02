package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*
import java.util.Collections

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

    private val areaFillColors = setOf(
        Color.DEPIT,
        Color.DEPVS,
        Color.DEPMS,
        Color.DEPMD,
        Color.DEPDW,
    )

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            //DRVAL1 is lower (sometimes negative) (shallower) end of range
            //DRVAL2 is higher (deeper) end of range
            areaLayerWithFillColor(theme = options.theme, options = areaFillColors),
            lineLayerWithColor(theme = options.theme, width = 0.5f, options = Collections.singleton(Color.CHGRD)),
        )
    }

    override suspend fun preTileEncode(feature: ChartFeature) {
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
            }
        }
        feature.areaColor(ac)
        feature.lineColor(Color.CHGRD)
    }
}

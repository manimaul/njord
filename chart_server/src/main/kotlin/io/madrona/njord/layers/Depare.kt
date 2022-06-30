package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*

class Depare : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            //DRVAL1 is lower (sometimes negative) (shallower) end of range
            //DRVAL2 is higher (deeper) end of range
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillColor = Filters.areaFillColor
                ),
            ),
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineStringOrPolygon,
                paint = Paint(
                    lineColor = colorFrom("CHGRD"),
                    lineWidth = 0.5f
                ),
            )
        )
    }

    override fun tileEncode(feature: ChartFeature) {
        var ac = "CHBLK"
        feature.props.floatValue("DRVAL1")?.let { shallowRange ->
            feature.props.floatValue("DRVAL2")?.let { deepRange ->
                ac = when {
                    shallowRange < 0.0f && deepRange <= 0.0f -> "DEPIT"
                    shallowRange <= Singletons.config.shallowDepth -> "DEPVS"
                    shallowRange <= Singletons.config.safetyDepth -> "DEPMS"
                    shallowRange <= Singletons.config.deepDepth -> "DEPMD"
                    shallowRange > Singletons.config.deepDepth -> "DEPDW"
                    else -> throw IllegalStateException("unexpected DRVAL1 $shallowRange")
                }
                log.debug("finding area fill color for $key $ac DRVAL1=$shallowRange DRVAL2=$deepRange")
            }
        }
        feature.props["AC"] = ac
    }
}

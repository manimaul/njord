package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.*
import io.madrona.njord.util.logger

class Obstrn : Layerable(autoSymbol = true) {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineStringOrPolygon,
                paint = Paint(
                    lineColor = colorFrom("CHGRD"),
                    lineWidth = 1f,
                    lineDashArray = listOf(2f, 5f),
                )
            ),
            Layer(
                id = "${key}_fill_color",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon,
                    listOf(Filters.ltEq, "VALSOU", 3.0)
                ),
                paint = Paint(
                    fillColor = colorFrom("DEPIT")
                )
            ),
            Layer(
                id = "${key}_fill_pattern",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillPattern = listOf("get", "SY")
                )
            ),
            Layer(
                id = "${key}_point",
                type = LayerType.SYMBOL,
                sourceLayer = key,
                filter = Filters.eqTypePoint,
                layout = Layout(
                    symbolPlacement = Placement.POINT,
                    iconImage = listOf("get", "SY"),
                    iconAnchor = Anchor.CENTER,
                    iconAllowOverlap = true,
                    iconKeepUpright = false,
                ),
            )
        )
    }

    override fun tileEncode(feature: ChartFeature) {
        val meters: Float = feature.props.floatValue("VALSOU") ?: 0.0f
        val quality = feature.props.intValues("QUASOU")
        val ac = when  {
            quality.contains(1) || //depth known
                    quality.contains(5) || //no bottom found at value shown
                    quality.contains(6) || //least depth known
                    quality.contains(10) -> { //maintained depth
                when {
                    meters <= 0.0 -> "DEPIT"
                    meters <= Singletons.config.shallowDepth -> "DEPVS"
                    meters <= Singletons.config.safetyDepth -> "DEPMS"
                    meters <= Singletons.config.deepDepth -> "DEPMD"
                    meters > Singletons.config.deepDepth -> "DEPDW"
                    else -> throw IllegalStateException("unexpected VALSOU $meters")
                }
            }
            else -> null
            //2 depth unknown
            //3 doubtful sounding
            //4 unreliable sounding
            //7 least depth unknown, safe clearance at value shown
            //8 value reported (not surveyed)
            //9 value reported (not confirmed)
            //11 not regularly maintained

        }
        log.debug("finding area fill color for $key $ac VALSOU=$meters QUASOU=$quality")
        feature.props["AC"] = ac
    }
}

package io.madrona.njord.layers

import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.model.*
import io.madrona.njord.util.logger

class Depare(
    private val config: ChartsConfig = Singletons.config
) : Layerable {
    override val key = KEY

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            //DRVAL1 is lower (sometimes negative) (shallower) end of range
            //DRVAL2 is higher (deeper) end of range
            Layer(
                id = "${key}_fill",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon,
                ),
                paint = Paint(
                    fillColor = listOf("case",
                        listOf("==", listOf("get", "AC"), "DEPIT"), colorFrom("DEPIT"),
                        listOf("==", listOf("get", "AC"), "DEPVS"), colorFrom("DEPVS"),
                        listOf("==", listOf("get", "AC"), "DEPMS"), colorFrom("DEPMS"),
                        listOf("==", listOf("get", "AC"), "DEPMD"), colorFrom("DEPMD"),
                        listOf("==", listOf("get", "AC"), "DEPDW"), colorFrom("DEPDW"),
                        listOf("==", listOf("get", "AC"), "CHBLK"), colorFrom("CHBLK"),
                        colorFrom("CHRED")
                    )
                ),
            ),
            Layer(
                id = "${key}_line",
                paint = Paint(
                    lineColor = colorFrom("CHGRD"),
                    lineWidth = 0.5f
                ),
                type = LayerType.LINE,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePolyGon,
                    Filters.eqTypeLineString,
                )
            )
        )
    }

    companion object {
        const val KEY = "DEPARE"
        private val  log = logger()
        fun tileEncodeFillColor(feature: ChartFeature) {
            if (feature.layer == KEY) {
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
                        log.debug("finding area fill color for $KEY $ac DRVAL1=$shallowRange DRVAL2=$deepRange")
                    }
                }
                feature.props["AC"] = ac
            }
        }
    }
}

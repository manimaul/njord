package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.layers.attributehelpers.Catobs
import io.madrona.njord.layers.attributehelpers.DepthColor
import io.madrona.njord.layers.attributehelpers.Quasou
import io.madrona.njord.layers.attributehelpers.Watlev
import io.madrona.njord.model.*
import io.madrona.njord.util.logger


class Obstrn : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineStringOrPolygon,
                paint = Paint(
                    lineColor = colorFrom("CHGRD"),
                    lineWidth = 2f,
                    lineDashArray = listOf(1f, 2f),
                )
            ),
            Layer(
                id = "${key}_fill_color",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon,
                ),
                paint = Paint(
                    fillColor = Filters.areaFillColor
                )
            ),
            Layer(
                id = "${key}_fill_pattern",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillPattern = listOf("get", "AP")
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

    override fun preTileEncode(feature: ChartFeature) {
        val state = ObstrnState(feature)
        feature.props["AC"] = state.depthColor.code

        var sySet = false

        when (state.category) {
            Catobs.SNAG_STUMP,
            Catobs.WELLHEAD,
            Catobs.DIFFUSER,
            Catobs.CRIB -> {}

            Catobs.FISH_HAVEN -> {
                feature.props["SY"] = "FSHHAV01"
                sySet = true
            }

            Catobs.FOUL_AREA,
            Catobs.FOUL_GROUND -> feature.props["AP"] = "FOULAR01"

            Catobs.GROUND_TACKLE -> {
                feature.props["AP"] = "ACHARE02"
                feature.props["SY"] = "ACHARE02"
                sySet = true
            }

            Catobs.ICE_BOOM,
            Catobs.BOOM -> feature.props["AP"] = "FLTHAZ02"

            null -> {}
        }

        if (!sySet) {
            when (state.waterLevelEffect) {
                Watlev.COVERS_AND_UNCOVERS -> feature.props["SY"] = "OBSTRN03"
                Watlev.ALWAYS_DRY -> feature.props["SY"] = "OBSTRN11"
                Watlev.ALWAYS_UNDER_WATER_SUBMERGED -> {
                    when (state.depthColor) {
                        DepthColor.DEEP_WATER,
                        DepthColor.MEDIUM_DEPTH-> feature.props["SY"] = "OBSTRN02"
                        DepthColor.SAFETY_DEPTH,
                        DepthColor.VERY_SHALLOW -> feature.props["SY"] = "OBSTRN01"
                        DepthColor.COVERS_UNCOVERS -> feature.props["SY"] = "OBSTRN03"
                    }
                }
                Watlev.FLOATING -> feature.props["SY"] = "FLTHAZ02"
                Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
                Watlev.AWASH,
                Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING,
                null -> feature.props["SY"] = "ISODGR51"
            }
        }
    }

}

class ObstrnState(feature: ChartFeature) {
    val log = logger()
    val meters: Float = feature.props.floatValue("VALSOU") ?: 0.0f
    val category = feature.props.intValue("CATOBS")?.let { Catobs.fromId(it) }
    val waterLevelEffect = feature.props.intValue("WATLEV")?.let { Watlev.fromId(it) }
    val qualityOfSounding = feature.props.intValues("QUASOU").mapNotNull { Quasou.fromId(it) }
    val depthColor: DepthColor
        get() {
            val ac = when (waterLevelEffect) {
                Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
                Watlev.ALWAYS_DRY,
                Watlev.COVERS_AND_UNCOVERS,
                Watlev.AWASH,
                Watlev.FLOATING -> DepthColor.COVERS_UNCOVERS

                Watlev.ALWAYS_UNDER_WATER_SUBMERGED,
                Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING -> {
                    if (qualityOfSounding.contains(Quasou.DEPTH_KNOWN) ||
                        qualityOfSounding.contains(Quasou.NO_BOTTOM_FOUND_AT_VALUE_SHOWN) ||
                        qualityOfSounding.contains(Quasou.LEAST_DEPTH_KNOWN) ||
                        qualityOfSounding.contains(Quasou.LEAST_DEPTH_UNKNOWN_SAFE_CLEARANCE_AT_VALUE_SHOWN) ||
                        qualityOfSounding.contains(Quasou.MAINTAINED_DEPTH)
                    ) {
                        when {
                            meters <= 0.0 -> DepthColor.COVERS_UNCOVERS
                            meters <= Singletons.config.shallowDepth -> DepthColor.VERY_SHALLOW
                            meters <= Singletons.config.safetyDepth -> DepthColor.SAFETY_DEPTH
                            meters <= Singletons.config.deepDepth -> DepthColor.MEDIUM_DEPTH
                            meters > Singletons.config.deepDepth -> DepthColor.DEEP_WATER
                            else -> throw IllegalStateException("unexpected VALSOU $meters")
                        }
                    } else {
                        null
                    }
                }

                null -> null
            } ?: DepthColor.COVERS_UNCOVERS

            return ac
        }
}

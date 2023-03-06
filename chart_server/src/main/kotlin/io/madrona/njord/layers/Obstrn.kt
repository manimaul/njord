package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.addSoundingConversions
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.layers.attributehelpers.*
import io.madrona.njord.model.*
import io.madrona.njord.util.logger


class Obstrn : Soundg() {
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
        ) + super.layers(options)
    }
    override fun preTileEncode(feature: ChartFeature) {
        val state = ObstrnState(feature)
        feature.props["AC"] = state.depthColor.code

        var sySet = false
        var showDepth = state.usableDepthValue

        when (state.category) {
            Catobs.SNAG_STUMP,
            Catobs.WELLHEAD,
            Catobs.DIFFUSER,
            Catobs.CRIB -> {
                if (!state.usableDepthValue) {
                    feature.props["SY"] = "ISODGR51"
                    sySet = true
                    showDepth = false
                }
            }

            Catobs.FISH_HAVEN -> {
                feature.props["SY"] = "FSHHAV01"
                sySet = true
                showDepth = false
            }

            Catobs.FOUL_AREA,
            Catobs.FOUL_GROUND -> feature.props["AP"] = "FOULAR01"

            Catobs.GROUND_TACKLE -> {
                feature.props["AP"] = "ACHARE02"
                feature.props["SY"] = "ACHARE02"
                sySet = true
                showDepth = false
            }

            Catobs.ICE_BOOM,
            Catobs.BOOM -> feature.props["AP"] = "FLTHAZ02"

            null -> {}
        }

        var checkAccuracy = true
        if (!sySet) {
            when (state.waterLevelEffect) {
                Watlev.COVERS_AND_UNCOVERS -> {
                    checkAccuracy = false
                    feature.props["SY"] = "OBSTRN03"
                }

                Watlev.ALWAYS_DRY -> {
                    checkAccuracy = false
                    feature.props["SY"] = "OBSTRN11"
                }

                Watlev.ALWAYS_UNDER_WATER_SUBMERGED -> {
                    when (state.depthColor) {
                        DepthColor.DEEP_WATER,
                        DepthColor.MEDIUM_DEPTH -> {
                            feature.props["SY"] = if (showDepth) "DANGER02" else "OBSTRN02"
                        }

                        DepthColor.SAFETY_DEPTH,
                        DepthColor.VERY_SHALLOW -> {
                            feature.props["SY"] = if (showDepth) "DANGER01" else "OBSTRN01"
                        }

                        DepthColor.COVERS_UNCOVERS -> {
                            feature.props["SY"] = if (showDepth) "DANGER03" else "OBSTRN03"
                        }
                    }
                }

                Watlev.FLOATING -> feature.props["SY"] = "FLTHAZ02"
                Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
                Watlev.AWASH,
                Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING,
                null -> {
                    checkAccuracy = false
                    feature.props["SY"] = "ISODGR51"
                }
            }
        }

        if (checkAccuracy && state.qualityOfSounding.find {
                it == Quasou.DOUBTFUL_SOUNDING
                        || it == Quasou.UNRELIABLE_SOUNDING
            } != null) {
            showDepth = false
            feature.props["SY"] = "LOWACC01"
        }

        state.meters?.takeIf { showDepth }?.let { meters ->
            feature.props.addSoundingConversions(meters.toDouble())
        }
    }
}

class ObstrnState(feature: ChartFeature) {
    val log = logger()
    val meters: Float? = feature.props.floatValue("VALSOU")
    val category = feature.props.catobs()
    val waterLevelEffect = feature.props.watlev()
    val qualityOfSounding = feature.props.quasou()
    val usableDepthValue: Boolean = qualityOfSounding.firstOrNull {
        when (it) {
            Quasou.DEPTH_KNOWN,
            Quasou.NO_BOTTOM_FOUND_AT_VALUE_SHOWN,
            Quasou.LEAST_DEPTH_KNOWN,
            Quasou.LEAST_DEPTH_UNKNOWN_SAFE_CLEARANCE_AT_VALUE_SHOWN,
            Quasou.MAINTAINED_DEPTH -> true

            Quasou.DEPTH_UNKNOWN,
            Quasou.DOUBTFUL_SOUNDING,
            Quasou.UNRELIABLE_SOUNDING,
            Quasou.VALUE_REPORTED_NOT_SURVEYED,
            Quasou.VALUE_REPORTED_NOT_CONFIRMED,
            Quasou.NOT_REGULARLY_MAINTAINED -> false
        }
    } != null

    val depthColor: DepthColor
        get() {
            val ac = when (waterLevelEffect) {
                Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
                Watlev.ALWAYS_DRY,
                Watlev.COVERS_AND_UNCOVERS,
                Watlev.AWASH,
                Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING,
                Watlev.FLOATING -> DepthColor.COVERS_UNCOVERS

                Watlev.ALWAYS_UNDER_WATER_SUBMERGED -> {
                    meters?.takeIf { usableDepthValue }?.let {
                        when {
                            it <= Singletons.config.shallowDepth -> DepthColor.VERY_SHALLOW
                            it <= Singletons.config.safetyDepth -> DepthColor.SAFETY_DEPTH
                            it <= Singletons.config.deepDepth -> DepthColor.MEDIUM_DEPTH
                            it > Singletons.config.deepDepth -> DepthColor.DEEP_WATER
                            else -> throw IllegalStateException("unexpected VALSOU $it")
                        }
                    } ?: DepthColor.VERY_SHALLOW
                }

                null -> null
            } ?: DepthColor.COVERS_UNCOVERS

            return ac
        }
}

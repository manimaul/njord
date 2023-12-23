package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.addSoundingConversions
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.layers.attributehelpers.Catobs
import io.madrona.njord.layers.attributehelpers.Catobs.Companion.catobs
import io.madrona.njord.layers.attributehelpers.DepthColor
import io.madrona.njord.layers.attributehelpers.Quasou
import io.madrona.njord.layers.attributehelpers.Quasou.Companion.quasou
import io.madrona.njord.layers.attributehelpers.Watlev
import io.madrona.njord.layers.attributehelpers.Watlev.Companion.watlev
import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Layer
import io.madrona.njord.util.logger


class Obstrn : Soundg() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        val textLayers = super.layers(options)
        return sequenceOf(
            areaLayerWithFillColor(),
            areaLayerWithFillPattern(),
            lineLayerWithColor(color = Color.CHGRD, style = LineStyle.DashLine),
            pointLayerFromSymbol(anchor = Anchor.CENTER),
            areaLayerWithPointSymbol()
        ) + textLayers
    }

    override fun preTileEncode(feature: ChartFeature) {
        val state = ObstrnState(feature)

        var sySet = false
        var showDepth = state.usableDepthValue
        var fillDepthColor = true

        when (state.category) {
            Catobs.SNAG_STUMP,
            Catobs.WELLHEAD,
            Catobs.DIFFUSER,
            Catobs.CRIB -> {
                if (!state.usableDepthValue) {
                    feature.pointSymbol(Sprite.ISODGR01)
                    sySet = true
                    showDepth = false
                }
            }

            Catobs.FISH_HAVEN -> {
                feature.pointSymbol(Sprite.FSHHAV01)
                sySet = true
                showDepth = false
            }

            Catobs.FOUL_AREA,
            Catobs.FOUL_GROUND -> feature.areaPattern(Sprite.FOULAR01P)

            Catobs.GROUND_TACKLE -> {
                feature.pointSymbol(Sprite.ACHARE02)
                sySet = true
                showDepth = false
            }

            Catobs.ICE_BOOM,
            Catobs.BOOM -> feature.pointSymbol(Sprite.FLTHAZ02)

            null -> {}
        }

        var checkAccuracy = true
        if (!sySet) {
            when (state.waterLevelEffect) {
                Watlev.COVERS_AND_UNCOVERS -> {
                    checkAccuracy = false
                    feature.excludeAreaPointSymbol() // exclude symbol from area geometry
                    feature.pointSymbol(Sprite.OBSTRN03)
                }

                Watlev.ALWAYS_DRY -> {
                    checkAccuracy = false
                    feature.excludeAreaPointSymbol()
                    feature.pointSymbol(Sprite.OBSTRN11)
                }

                Watlev.ALWAYS_UNDER_WATER_SUBMERGED -> {
                    when (state.depthColor) {
                        DepthColor.DEEP_WATER,
                        DepthColor.MEDIUM_DEPTH -> {
                            feature.excludeAreaPointSymbol()
                            feature.pointSymbol(if (showDepth) Sprite.DANGER02 else Sprite.OBSTRN02)
                        }

                        DepthColor.SAFETY_DEPTH,
                        DepthColor.VERY_SHALLOW -> {
                            feature.excludeAreaPointSymbol()
                            feature.pointSymbol(if (showDepth) Sprite.DANGER01 else Sprite.OBSTRN01)
                        }

                        DepthColor.COVERS_UNCOVERS -> {
                            feature.excludeAreaPointSymbol()
                            feature.pointSymbol(if (showDepth) Sprite.DANGER03 else Sprite.OBSTRN03)
                        }
                    }
                }

                Watlev.FLOATING -> {
                    showDepth = false
                    fillDepthColor = false
                    feature.pointSymbol(Sprite.FLTHAZ02)
                }

                Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
                Watlev.AWASH,
                Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING,
                null -> {
                    checkAccuracy = false
                    feature.pointSymbol(Sprite.ISODGR01)
                }
            }
        }

        if (checkAccuracy && state.qualityOfSounding.find {
                it == Quasou.DOUBTFUL_SOUNDING
                        || it == Quasou.UNRELIABLE_SOUNDING
            } != null) {
            showDepth = false
            feature.pointSymbol(Sprite.LOWACC01)
        }

        if (fillDepthColor) {
            feature.areaColor(state.depthColor.color)
        }
        state.meters?.takeIf { showDepth }?.let { meters ->
            feature.props.addSoundingConversions(meters.toDouble())
        }
    }
}

class ObstrnState(feature: ChartFeature) {
    val log = logger()
    val meters: Float? = feature.props.floatValue("VALSOU")
    val category = feature.catobs()
    val waterLevelEffect = feature.watlev()
    val qualityOfSounding = feature.quasou()
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

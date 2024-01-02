package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Cattrk
import io.madrona.njord.layers.attributehelpers.Cattrk.Companion.cattrk
import io.madrona.njord.layers.attributehelpers.Trafic
import io.madrona.njord.layers.attributehelpers.Trafic.Companion.trafic
import io.madrona.njord.model.ChartFeature

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Recommended track
 *
 * Acronym: RECTRC
 *
 * Code: 109
 */
class Rectrc : Layerable() {
    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.cattrk()) {
            Cattrk.BASED_ON_A_SYSTEM_OF_MARKS -> {
                when (feature.trafic()) {
                    Trafic.INBOUND,
                    Trafic.OUTBOUND,
                    Trafic.ONE_WAY -> feature.linePattern(Sprite.RECTRC58)
                    Trafic.TWO_WAY -> feature.linePattern(Sprite.RECTRC56)
                    null -> feature.linePattern(Sprite.RECDEF51)
                }
            }

            Cattrk.NOT_BASED_ON_A_SYSTEM_OF_MARKS,
            null -> {
                when (feature.trafic()) {
                    Trafic.INBOUND,
                    Trafic.OUTBOUND,
                    Trafic.ONE_WAY -> feature.linePattern(Sprite.RECTRC57)
                    Trafic.TWO_WAY -> feature.linePattern(Sprite.RECTRC55)
                    null -> feature.linePattern(Sprite.RECDEF51)
                }
            }
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithPattern(
            // https://maplibre.org/maplibre-style-spec/layers/#layout-symbol-icon-rotation-alignment
            // map orientation aligns x-axis with line
            iconRotate = 90f, //listOf("get", "ORIENT"),
            spacing = 15f,
            allowOverlap = false
        ),
    )
}

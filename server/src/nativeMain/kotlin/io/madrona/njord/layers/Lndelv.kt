package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*
import kotlinx.serialization.json.JsonElement

/**
 * Geometry Primitives: Point, Line
 *
 * Object: Land elevation
 *
 * Acronym: LNDELV
 *
 * Code: 75
 *
 * S-52 lookups: a point is `SY(POSGEN04);TE('%3.0lf m','ELEVAT',3,2,2,'15110',1,0,CHBLK,28)`, a
 * line (a land contour) is `LS(SOLD,1,LANDF)` with no text.
 */
class Lndelv : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.POSGEN04)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.LANDF,
            width = 1f,
            filter = Filters.eqTypeLineString,
        ),
        pointLayerFromSymbol(anchor = Anchor.CENTER),
        textLayer(
            textField = elevation(options.depth),
            theme = options.theme,
            textAnchor = Anchor.LEFT,
            textJustify = TextJustify.LEFT,
            textOffset = Offset.Coord(x = 1.2f, y = 0f),
            filter = listOf(Filters.all, Filters.eqTypePoint, listOf("has", "ELEVAT")).json,
        ),
    )

    /**
     * Whole units, per `%3.0lf`. Like [ClearanceLabel] the tile stays unit-agnostic and the style
     * picks metres or feet from the depth preference.
     */
    private fun elevation(depth: Depth): JsonElement = when (depth) {
        Depth.METERS -> listOf(
            "concat",
            listOf("to-string", listOf("round", listOf("get", "ELEVAT"))),
            "m",
        )

        Depth.FEET, Depth.FATHOMS -> listOf(
            "concat",
            listOf("to-string", listOf("round", listOf("*", listOf("get", "ELEVAT"), FEET_PER_METER))),
            "ft",
        )
    }.json

    companion object {
        private const val FEET_PER_METER = 3.28084
    }
}

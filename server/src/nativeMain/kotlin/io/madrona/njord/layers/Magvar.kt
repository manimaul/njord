package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Magnetic variation
 *
 * Acronym: MAGVAR
 *
 * Code: 77
 *
 * S-52 lookups:
 * * point - `SY(MAGVAR01);TX(VALMAG,3,1,2,'15110',1,-1,CHBLK,27)`
 * * line - `LS(SOLD,2,CHMGF);SY(MAGVAR51);TE('varn %s','VALMAG',3,1,2,'15110',1,-1,CHBLK,27)`
 * * area - `SY(MAGVAR51)`
 *
 * MAGVAR51 (a cursor pick site) isn't in the sprite sheets, so in its place every geometry gets
 * the line's `varn` text - which also reads better on a point than the bare number S-52 shows.
 */
class Magvar : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        feature.pointSymbol(Sprite.MAGVAR01)
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHMGF,
            width = 2f,
            filter = Filters.eqTypeLineString,
        ),
        pointLayerFromSymbol(),
        textLayer(
            textField = listOf("concat", "varn ", listOf("to-string", listOf("get", "VALMAG"))).json,
            theme = options.theme,
            textAnchor = Anchor.BOTTOM_LEFT,
            textJustify = TextJustify.LEFT,
            textOffset = Offset.Coord(x = 1.2f, y = -1.2f),
            filter = listOf("has", "VALMAG").json,
        ),
    )
}

package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Line, Area
 *
 * Object: Tideway
 *
 * Acronym: TIDEWY
 *
 * Code: 147
 *
 * S-52 lookup (area): `LS(DASH,1,CHGRF);TX(OBJNAM,1,2,3,'15110',0,0,CHBLK,25)` - a dashed outline
 * and a centred name, no fill. The presentation library has no line lookup, so a line gets the
 * same treatment.
 */
class Tidewy : Layerable() {

    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(
            theme = options.theme,
            color = Color.CHGRF,
            width = 1f,
            style = LineStyle.DashLine,
        ),
        textLayer(
            textField = listOf("get", "OBJNAM").json,
            theme = options.theme,
            filter = listOf("has", "OBJNAM").json,
        ),
    )
}

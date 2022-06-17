package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.model.*

/**
 * Geometric primitives: P, A
 * Set Attribute_A: 	CATACH; DATEND; DATSTA; NOBJNM; OBJNAM; PEREND; PERSTA; RESTRN; STATUS;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * An area in which vessels or seaplanes anchor or may anchor. (Adapted from the IHO Dictionary, S-32, 5th Edition, 130)
 * (changed according to MD 7.Co.15)
 * References
 * INT 1:	IN 12.1-9;
 * S-4:	431.3;
 * Remarks:
 * Distinction:
 * anchor berth; mooring/warping facility;
 */
class Achare : SymbolLayerable() {
    override val key = "ACHARE"

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_point",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = listOf(Filters.any, Filters.eqTypePoint),
            layout = Layout(
                symbolPlacement = Placement.POINT,
                iconImage = listOf("get", "SY"),
                iconAnchor = Anchor.BOTTOM,
                iconAllowOverlap = true,
                iconKeepUpright = true,
            )
        ),
        Layer(
            id = "${key}_line",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = listOf(
                Filters.any,
                Filters.eqTypePolyGon
            ),
            paint = Paint(
                lineColor = colorFrom("CHMGF"),
                lineWidth = 2.0f,
                lineDashArray = listOf(7f, 4f)
            )
        )
    )
}

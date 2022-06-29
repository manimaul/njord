package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Sea Area, SEAARE
 * Geometric primitives: Point, Area
 *
 * Set Attribute_A: 	(!?)CATSEA; NOBJNM; (!?)OBJNAM;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * A geographically defined part of the sea or other navigable waters. It may be specified within its limits by its proper name.
 * References
 * INT 1:	not specified;
 * S-4:	not specified;
 * Remarks:
 * Each sea area is defined independent of any other. Smaller sea areas may be located within larger sea areas.
 * Distinction:
 * depth area; seabed area;
 */
class Seaare : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill",
                        paint = Paint(
                                fillColor = colorFrom("CHWHT")
                        ),
                        filter = listOf(Filters.any, Filters.eqTypePolyGon, Filters.eqTypeLineString),
                        sourceLayer = "SEAARE",
                        type = LayerType.FILL
                ),
//                Layer(
//                        id = "${key}_point",
//                        type = LayerType.SYMBOL,
//                        sourceLayer = "SEAARE",
//                        filter = listOf("any", listOf(">", "CATSEA", 0)),
//                        layout = Layout(
//                                textFont = listOf(Font.ROBOTO_BOLD),
//                                textAnchor = Anchor.CENTER,
//                                textJustify = Anchor.CENTER,
//                                textField = listOf("get", "OBJNAM"),
//                                textAllowOverlap = false,
//                                textIgnorePlacement = false,
//                                textMaxWidthEms = 9F,
//                                textSize = 12F,
//                                textPadding = 6F,
//                                symbolPlacement = Placement.POINT
//                        ),
//                        paint = Paint(
//                                textColor = ColorLibrary.black,
//                                textHaloColor = ColorLibrary.white,
//                                textHaloWidth = 1.5F
//
//                        )
//                )
        )
    }
}
package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * DEPCNT, Depth Contour
 * Geometric primitives: LineString
 *
 * Set Attribute_A: 	(!)VALDCO; VERDAT;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * A line connecting points of equal water depth which is sometimes significantly displaced outside of soundings, symbols and other chart detail for clarity as well as generalization. Depth contours, therefore, often represent an approximate location of the line of equal depth as related to the surveyed line delineated on the source. Also referred to as depth curve. (IHO Dictionary, S-32, 5th Edition, 1314, 1315)
 * References
 * INT 1:	II 15, 30, 31;
 * S-4:	404.2; 410; 411, 411.2; 413-413.2;
 * Remarks:
 * Drying contours are encoded with negative values.
 * Distinction:
 * sounding; depth area; coastline;
 */
class Depcnt : Layerable {
    override val key = "DEPCNT"
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "depth_contour",
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypeLineString
                        ),
                        paint = Paint(
                                lineColor = colorFrom("CSTLN"),
                                lineWidth = 0.5f
                        )
                )
        )
    }
}
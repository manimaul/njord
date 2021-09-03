package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * DEPARE, Depth Area
 * Geometric primitives: L,A
 *
 * Set Attribute_A: 	(!)DRVAL1; (!)DRVAL2; QUASOU; SOUACC; VERDAT;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * A depth area is a water area whose depth is within a defined range of values.
 * References
 * INT 1:	not specified;
 * S-4:	not specified;
 * Remarks:
 * Intertidal areas are encoded as depth areas. These do not have to include soundings.
 * The depth range within a depth area is defined by the attributes 'DRVAL1' and 'DRVAL2'.
 * Distinction:
 * depth contour; dredged area; sounding; obstruction; sea area/named water area; unsurveyed area; wreck;
 *
 * The geometric primitive line is removed for the object class, S-57 Supplement No. 3 (Edition 3.1.3), 3.3
 */
class Depare : Layerable {
    override val key = "DEPARE"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
                Layer(
                        id = "${key}_fill_2",
                        paint = Paint(
                                fillColor = options.color.from("DEPMD")
                        ),
                        sourceLayer = key,
                        type = LayerType.FILL,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf(Filters.gtEq, "DRVAL1", 9)
                        )
                ),
                Layer(
                        id = "${key}_fill_1",
                        paint = Paint(
                                fillColor = options.color.from("DEPVS")
                        ),
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf(Filters.gtEq, "DRVAL1", 3.0)
                        )
                ),
                Layer(
                        id = "${key}_fill_0",
                        paint = Paint(
                                fillColor = options.color.from("DEPIT")
                        ),
                        type = LayerType.FILL,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                listOf(Filters.gt, "DRVAL1", 0.0),
                                listOf(Filters.gtEq, "DRVAL2", 0.0),
                        )
                ),
                Layer(
                        id = "${key}_line",
                        paint = Paint(
                                lineColor = options.color.from("CSTLN"),
                                lineWidth = 0.5f
                        ),
                        type = LayerType.LINE,
                        sourceLayer = key,
                        filter = listOf(
                                Filters.all,
                                Filters.eqTypePolyGon,
                                Filters.eqTypeLineString,
                        )
                )
        )
    }
}
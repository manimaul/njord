package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometric primitives: A
 * Set Attribute_A:	DATEND; DATSTA; DRVAL1; NOBJNM; OBJNAM; ORIENT; QUASOU; RESTRN; SOUACC; STATUS; TRAFIC; VERDAT;
 * Set Attribute_B:	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C:	RECDAT; RECIND; SORDAT; SORIND;
 * Definition:
 * That part of a river, harbour and so on, where the main navigable channel for vessels of larger size lies. It is also the usual course followed by vessels entering or leaving harbours, called 'ship channel'. (International Maritime Dictionary, 2nd Ed.)
 * References
 * INT 1:	not specified;
 * S-4:	not specified;
 * Remarks:
 * Distinction:
 * deep water route centerline; deep water route part; traffic separation scheme lane part;
 */
class Fairwy : Layerable {
    override val key = "FAIRWY"
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon
                ),
                paint = Paint(
                    lineColor = options.color.from("CHBLK"),
                    lineWidth = 0.5f,
                    lineDashArray = listOf(10f, 5f)
                )
            )
        )
    }
}
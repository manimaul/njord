package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometric primitives: P
 * Set Attribute_A:	CATPLE; COLOUR; (!?)COLPAT; CONDTN; CONVIS; DATEND; DATSTA; HEIGHT; NOBJNM; OBJNAM; VERACC; VERDAT; VERLEN;
 * Set Attribute_B:	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C:	RECDAT; RECIND; SORDAT; SORIND;
 * Definition:
 * A long heavy timber or section of steel, wood, concrete, etc.. forced into the earth which may serve as a support, as for a pier, or a free standing pole within a marine environment. (Adapted from IHO Dictionary, S-32, 5th Edition, 3840)
 * References
 * INT 1:	IF 22;
 * S-4:	327.3;
 * Remarks:
 * Distinction:
 * beacon, cardinal; beacon, isolated danger; beacon, lateral; beacon, safe water; beacon special purpose/general; mooring/warping facility;
 */
class Pilpnt : Layerable {
    override val key = "PILPNT"

    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_point",
                type = LayerType.CIRCLE,
                sourceLayer = key,
                filter = listOf(
                    Filters.any,
                    Filters.eqTypePoint,
                ),
                paint = Paint(
                    circleColor = colorFrom("CHBLK"),
                    circleRadius = 2.5f
                )
            )
        )
    }
}
package io.madrona.njord.layers

import io.madrona.njord.model.*


/**
 * Geometric primitives: P
 * Set Attribute_A:	(!)BOYSHP; (!)CATLAM; (!)COLOUR; (!?)COLPAT; CONRAD; DATEND; DATSTA; (?)MARSYS; NATCON; NOBJNM; OBJNAM; PEREND; PERSTA; STATUS; VERACC; VERLEN;
 * Set Attribute_B:	INFORM; NINFOM; NTXTDS; PICREP; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C:	RECDAT; RECIND; SORDAT; SORIND;
 * Definition:
 * A buoy is a floating object moored to the bottom in a particular place, as an aid to navigation or for other specific purposes. (IHO Dictionary, S-32, 5th Edition, 565).
 * A lateral buoy is used to indicate the port or starboard hand side of the route to be followed. They are generally used for well defined channels and are used in conjunction with a conventional direction of buoyage. (UKHO NP 735, 5th Edition)
 * References
 * INT 1:	IQ 130.1;
 * S-4:	461;
 * Remarks:
 * Topmark, light, fog signal, radar reflector and retro-reflector are separate objects.
 * Distinction:
 * buoy cardinal; buoy safe water; buoy isolated danger; buoy special purpose/general; mooring/warping facility;
 */
class Boylat : SymbolLayerable() {
    override val key = "BOYLAT"

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
            )
    )
}

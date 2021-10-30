package io.madrona.njord.layers

import io.madrona.njord.model.*


/**
 * Beacon, Lateral
 *
 * Geometric primitives: P
 * Set Attribute_A:	(!)BCNSHP; (!)CATLAM; (!)COLOUR; (!?)COLPAT; CONDTN; CONRAD; CONVIS; DATEND; DATSTA; ELEVAT; HEIGHT; (?)MARSYS; NATCON; NOBJNM; OBJNAM; PEREND; PERSTA; STATUS; VERACC; VERDAT; VERLEN;
 * Set Attribute_B:	INFORM; NINFOM; NTXTDS; PICREP; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C:	RECDAT; RECIND; SORDAT; SORIND;
 * Definition:
 * A beacon is a prominent specially constructed object forming a conspicuous mark as a fixed aid to navigation or for use in hydrographic survey (IHO Dictionary, S-32, 5th Edition, 420).
 * A lateral beacon is used to indicate the port or starboard hand side of the route to be followed. They are generally used for well defined channels and are used in conjunction with a conventional direction of buoyage. (UKHO NP 735, 5th Edition)
 * References
 * INT 1:	IQ 91-92, 130.1;
 * S-4:	not specified;
 * Remarks:
 * Topmark, light, fog signal, radar reflector and retro-reflector are separate objects.
 * Distinction:
 * daymark; beacon cardinal; beacon safe water; beacon isolated danger; beacon special purpose/general;

 */
class Bcnlat : SymbolLayerable() {
    override val key = "BCNLAT"

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

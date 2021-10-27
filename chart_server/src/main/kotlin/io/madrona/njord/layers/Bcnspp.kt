package io.madrona.njord.layers

import io.madrona.njord.model.*


/**
 * Geometric primitives: P
 * Set Attribute_A:	(!)BCNSHP; (!)CATSPM; (!)COLOUR; (!?)COLPAT; CONDTN; CONRAD; CONVIS; DATEND; DATSTA; ELEVAT; HEIGHT;
 * (?)MARSYS; NATCON; NOBJNM; OBJNAM; PEREND; PERSTA; STATUS; VERACC; VERDAT; VERLEN;
 * Set Attribute_B:	INFORM; NINFOM; NTXTDS; PICREP; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C:	RECDAT; RECIND; SORDAT; SORIND;
 * Definition:
 * A beacon is a prominent specially constructed object forming a conspicuous mark as a fixed aid to navigation or for
 * use in hydrographic survey (IHO Dictionary, S-32, 5th Edition, 420).
 * A special purpose beacon is primarily used to indicate an area or feature, the nature of which is apparent from
 * reference to a chart, Sailing Directions or Notices to Mariners. (UKHO NP 735, 5th Edition)
 * Beacon in general: A beacon whose appearance or purpose is not adequately known.
 * References
 * INT 1:	IQ 130.6;
 * S-4:	456.4;
 * Remarks:
 * Topmark, light, fog signal, radar reflector and retro-reflector are separate objects.
 * Distinction:
 * daymark; beacon lateral; beacon safe water; beacon isolated danger; beacon cardinal; distance mark;
 */
class Bcnspp : Layerable {
    override val key = "BCNSPP"

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
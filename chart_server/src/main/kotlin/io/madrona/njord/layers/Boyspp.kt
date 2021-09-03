package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * BOYSPP, Buoy Special Purpose / General
 * Geometric primitives: Point
 *
 * Set Attribute_A: 	(!)BOYSHP; (!)CATSPM; (!)COLOUR; (!?)COLPAT; CONRAD; DATEND; DATSTA; (?)MARSYS; NATCON; NOBJNM; OBJNAM; PEREND; PERSTA; STATUS; VERACC; VERLEN;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; PICREP; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 *     A buoy is a floating object moored to the bottom in a particular place, as an aid to navigation or for other specific purposes. (IHO Dictionary, S-32, 5th Edition, 565).
 *     A special purpose buoy is primarily used to indicate an area or feature, the nature of which is apparent from reference to a chart, Sailing Directions or Notices to Mariners. (UKHO NP 735, 5th Edition)
 *     Buoy in general: A buoy whose appearance or purpose is not adequately known.
 * References
 *     INT 1:	IQ 130.6;
 *     S-4:	461;
 * Remarks:
 *     Topmark, light, fog signal, radar reflector and retro-reflector are separate objects.
 * Distinction:
 *     buoy lateral; buoy safe water; buoy isolated danger; buoy cardinal; buoy installation; mooring/warping facility;
 */
class Boyspp : Layerable {
    override val key = "BOYSPP"

    override fun layers(options: LayerableOptions) = sequenceOf<Layer>(

    )
}
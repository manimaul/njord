package io.madrona.njord.layers

import com.fasterxml.jackson.annotation.JsonValue
import io.madrona.njord.model.*

/**
 * BOYSHP
 * ID	Meaning	INT 1	S-4
 * 1	conical (nun, ogival)	IQ 20;	462.2
 * 2	can (cylindrical)	IQ 21;	462.3
 * 3	spherical	IQ 22;	462.4
 * 4	pillar	IQ 23;	462.5
 * 5	spar (spindle)	IQ 24;	462.6
 * 6	barrel (tun)	IQ 25;	462.7
 * 7	super-buoy	IQ 26;	462.9
 * 8	ice buoy
 */
enum class Boyshp(@get:JsonValue val code: Int) {
    Conical(1),
    Can(2),
    Spherical(3),
    Pillar(4),
    Spar(5),
    Barrel(6),
    SuperBuoy(7),
    IceBuoy(8),
}

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

    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "${key}_point",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(Filters.any, Filters.eqTypePoint),
                    layout = Layout(
                            symbolPlacement = Placement.POINT,
                            iconImage = listOf("GET", "SY"),
                            iconAnchor = Anchor.BOTTOM,
                            iconAllowOverlap = true,
                            iconKeepUpright = true,
                    )
            )
    )
}
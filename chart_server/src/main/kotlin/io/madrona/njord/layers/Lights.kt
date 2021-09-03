package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * LIGHTS, Light
 * Geometric primitives: P
 *
 * Set Attribute_A: 	(!?)CATLIT; (!?)COLOUR; DATEND; DATSTA; EXCLIT; (?)HEIGHT; (!?)LITCHR; LITVIS; MARSYS; MLTYLT; NOBJNM; OBJNAM; (!?)ORIENT; PEREND; PERSTA; (!?)SECTR1; (!?)SECTR2; (!?)SIGGRP; (!?)SIGPER; (?)SIGSEQ; STATUS; VALNMR; VERACC; (?)VERDAT;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 *     A luminous or lighted aid to navigation. (adapted from IHO Dictionary, S-32, 5th Edition, 2766)
 * References
 *     INT 1:	IP 1-30.3, 40-65;
 *     S-4:	470-473.5; 475-475.7; 476-478,5;
 * Remarks:
 *     A light may be fixed on a buoy, beacon, tower etc. These are separate objects.
 * Distinction:
 *     beacon, cardinal; beacon, isolated danger; beacon, lateral; beacon, safe water; beacon special purpose/general; buoy, cardinal; buoy, installation; buoy, isolated danger; buoy, lateral; buoy, safe water; buoy, special purpose/general; light vessel; light float;
 */
class Lights : Layerable {
    override val key = "LIGHTS"

    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "lights",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(Filters.any, Filters.eqTypePoint),
                    layout = Layout(
                            iconImage = listOf("get", "SY"),
                            iconKeepUpright = true,
                            iconAnchor = Anchor.TOP_LEFT,
                            iconAllowOverlap = true,
                            symbolPlacement = Placement.POINT
                    )
            )
    )
}
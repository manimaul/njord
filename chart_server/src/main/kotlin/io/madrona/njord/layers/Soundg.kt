package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Sounding SOUNDG
 * Geometric primitives: Point
 *
 * Set Attribute_A: 	EXPSOU; NOBJNM; OBJNAM; QUASOU; (?)SOUACC; STATUS; (?)TECSOU; VERDAT;
 * Set Attribute_B: 	INFORM; NINFOM; NTXTDS; SCAMAX; SCAMIN; TXTDSC;
 * Set Attribute_C: 	RECDAT; RECIND; SORDAT; SORIND;
 *
 * Definition:
 * A measured water depth or spot which has been reduced to a vertical datum (may be a drying height).
 * References
 * INT 1:	II 10-15;
 * S-4:	403.1; 410; 412-412.4; 413.1; 417.3;
 * Remarks:
 * The value of the sounding is encoded in the 3-D Coordinate field of the Spatial Record Structure (see S-57 Part 3).
 * Drying heights (drying soundings) are indicated by a negative value.
 * Distinction:
 * depth area; wreck; underwater/awash rock; obstruction;
 */
class Soundg : Layerable {
    override val key = "SOUNDG"

    override fun layers(options: LayerableOptions) = when(options.depth) {
        Depth.FATHOMS -> fathoms(options.color)
        Depth.METERS -> meters(options.color)
        Depth.FEET -> feet(options.color)
    }

    private fun fathoms(color: StyleColor) = sequenceOf(
            Layer(
                    id = "soundg_fathoms",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(
                            Filters.any, Filters.eqTypePoint
                    ),
                    layout = Layout(
                            textFont = listOf(Font.ROBOTO_BOLD),
                            textAnchor = Anchor.BOTTOM_RIGHT,
                            textJustify = Anchor.CENTER,
                            textField = listOf("get", "FATHOMS"),
                            textAllowOverlap = true,
                            textIgnorePlacement = true,
                            textSize = 11f,
                            symbolPlacement = Placement.POINT,
                    ),
                    paint = Paint(
                            textColor = listOf(
                                    "case", listOf(Filters.gtEq, listOf("get", "METERS"), 9.0f),
                                    color.from("SNDG2"),
                                    color.from("SNDG1")
                            ),
                            textHaloColor = color.from("CHWHT"),
                            textHaloWidth = 1.5f
                    )
            ),
            Layer(
                    id = "soundg_fathoms_feet",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(
                            Filters.any,
                            Filters.eqTypePoint,
                            listOf(Filters.notEq, "FATHOMS_FT", 0)
                    ),
                    layout = Layout(
                            textFont = listOf(Font.ROBOTO_BOLD),
                            textAnchor = Anchor.TOP_LEFT,
                            textOffset = listOf(0.1f,-0.7f),
                            textJustify = Anchor.CENTER,
                            textField = listOf("get", "FATHOMS_FT"),
                            textAllowOverlap = true,
                            textIgnorePlacement = true,
                            textSize = 9f,
                            symbolPlacement = Placement.POINT,
                    ),
                    paint = Paint(
                            textColor = listOf(
                                    "case", listOf(Filters.gtEq, listOf("get", "METERS"), 9.0f),
                                    color.from("SNDG2"),
                                    color.from("SNDG1")
                            )
                    )
            )
    )

    private fun feet(color: StyleColor) = sequenceOf(
            Layer(
                    id = "soundg_feet",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(
                            "any", Filters.eqTypePoint
                    ),
                    layout = Layout(
                            textFont = listOf(Font.ROBOTO_BOLD),
                            textAnchor = Anchor.CENTER,
                            textJustify = Anchor.CENTER,
                            textField = listOf("get", "FEET"),
                            textAllowOverlap = true,
                            textIgnorePlacement = true,
                            textSize = 11f,
                            symbolPlacement = Placement.POINT,
                    ),
                    paint = Paint(
                            textColor = listOf(
                                    "case", listOf(Filters.gtEq, listOf("get", "METERS"), 9.0f),
                                    color.from("SNDG2"),
                                    color.from("SNDG1")
                            ),
                            textHaloColor = color.from("CHWHT"),
                            textHaloWidth = 1.5f
                    )
            )
    )

    private fun meters(color: StyleColor) = sequenceOf(
            Layer(
                    id = "soundg_meters",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(
                            "any", Filters.eqTypePoint
                    ),
                    layout = Layout(
                            textFont = listOf(Font.ROBOTO_BOLD),
                            textAnchor = Anchor.CENTER,
                            textJustify = Anchor.CENTER,
                            textField = listOf("get", "METERS"),
                            textAllowOverlap = true,
                            textIgnorePlacement = true,
                            textSize = 11f,
                            symbolPlacement = Placement.POINT,
                    ),
                    paint = Paint(
                            textColor = listOf(
                                    "case", listOf("<=", listOf("get", "METERS"), 9.0f),
                                    color.from("SNDG2"),
                                    color.from("SNDG1")
                            ),
                            textHaloColor = color.from("CHWHT"),
                            textHaloWidth = 1.5f
                    )
            )
    )
        /*
        json!({
              "id": "soundg_meters",
              "type": "symbol",
              "source": "src_senc",
              "source-layer": "SOUNDG",
              "filter": [ "any", [ "==", "$type", "Point" ], ],
              "layout": {
                "text-font": [ "Roboto Bold" ],
                "text-anchor": "center",
                "text-justify": "center",
                "text-field": ["get", "METERS"],
                "text-allow-overlap": true,
                "text-ignore-placement": true,
                "text-size": 11,
                "symbol-placement": "point"
              },
              "paint": {
                "text-color": ["case", ["<=", ["get", "METERS"], 9.0], colors["SNDG2"], colors["SNDG1"] ],
                "text-halo-color": colors["CHWHT"],
                "text-halo-width": 1.5
              }
            })
         */

}
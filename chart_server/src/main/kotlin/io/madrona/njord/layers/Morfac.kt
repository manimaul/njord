package io.madrona.njord.layers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.*

class Morfac : Layerable() {

    override fun preTileEncode(feature: ChartFeature) {
        /* https://openenc.com/control/symbols/MORFAC/CATMOR
        Enum
        1	dolphin
        2	deviation dolphin
        3	bollard
        4	tie-up wall
        5	post or pile
        6	chain/wire/cable
        7	mooring buoy
         */
        val category = feature.props.intValue("CATMOR") ?: 0
        val sy =  when (category) {
            1 -> "MORFAC03"
            2 -> "MORFAC04"
            3 -> "PILPNT02"
            5 -> "PILPNT02"
            7 -> "BOYMOR11"
            else -> "MORFAC03"
        }
        feature.props["SY"] = sy
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        Layer(
            id = "${key}_area_fill",
            type = LayerType.FILL,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                fillColor = colorFrom("CHBRN")
            )
        ),
        Layer(
            id = "${key}_area_line",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = Filters.eqTypePolyGon,
            paint = Paint(
                lineColor = colorFrom("CHBLK"),
                lineWidth = 1f
            )
        ),
        Layer(
            id = "${key}_line_tie_up_wall",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = listOf(
                Filters.all,
                Filters.eqTypeLineString,
                listOf(Filters.eq, "CATMOR", 4)
            ),
            paint = Paint(
                lineColor = colorFrom("CSTLN"),
                lineWidth = 2f
            )
        ),
        Layer(
            id = "${key}_line_chain_wire_cable",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = listOf(
                Filters.all,
                Filters.eqTypeLineString,
                listOf(Filters.eq, "CATMOR", 6)
            ),
            paint = Paint(
                lineColor = colorFrom("CHMGF"),
                lineWidth = 1f,
                lineDashArray = listOf(5f, 5f)
            )
        ),
        Layer(
            id = "${key}_line_default",
            type = LayerType.LINE,
            sourceLayer = key,
            filter = listOf(
                Filters.all,
                Filters.eqTypeLineString,
                listOf(Filters.notEq, "CATMOR", 6),
                listOf(Filters.notEq, "CATMOR", 4),
            ),
            paint = Paint(
                lineColor = colorFrom("CSTLN"),
                lineWidth = 2f,
            )
        ),
        Layer(
            id = "${key}_point",
            type = LayerType.SYMBOL,
            sourceLayer = key,
            filter = Filters.eqTypePoint,
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

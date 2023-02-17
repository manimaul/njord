package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.floatValue
import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.geo.symbols.intValues
import io.madrona.njord.model.*

class Obstrn : Layerable() {
    override fun layers(options: LayerableOptions): Sequence<Layer> {
        return sequenceOf(
            Layer(
                id = "${key}_line",
                type = LayerType.LINE,
                sourceLayer = key,
                filter = Filters.eqTypeLineStringOrPolygon,
                paint = Paint(
                    lineColor = colorFrom("CHGRD"),
                    lineWidth = 2f,
                    lineDashArray = listOf(1f, 2f),
                )
            ),
            Layer(
                id = "${key}_fill_color",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = listOf(
                    Filters.all,
                    Filters.eqTypePolyGon,
                ),
                paint = Paint(
                    fillColor = Filters.areaFillColor
                )
            ),
            Layer(
                id = "${key}_fill_pattern",
                type = LayerType.FILL,
                sourceLayer = key,
                filter = Filters.eqTypePolyGon,
                paint = Paint(
                    fillPattern = listOf("get", "AP")
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
                    iconAnchor = Anchor.CENTER,
                    iconAllowOverlap = true,
                    iconKeepUpright = false,
                ),
            )
        )
    }

    private fun encodeAreaPattern(feature: ChartFeature) {
        val category = feature.props.intValue("CATOBS")
        /**
        1	snag / stump
        2	wellhead
        3	diffuser
        4	crib
        5	fish haven
        6	foul area
        7	foul ground
        8	ice boom
        9	ground tackle
        10	boom
         */
        if (category == 6) {
            feature.props["AP"] = "FOULAR01"
        }
    }

    private fun encodeAreaColor(feature: ChartFeature) {
        val meters: Float = feature.props.floatValue("VALSOU") ?: 0.0f
        val quality = feature.props.intValues("QUASOU")
        val waterLevelEffect = feature.props.intValue("WATLEV")
        val ac = when {
            /**
            1	partly submerged at high water
            2	always dry
            3	always under water/submerged
            4	covers and uncovers
            5	awash
            6	subject to inundation or flooding
            7	floating
             */
            waterLevelEffect == 1 ||
                    waterLevelEffect == 2 ||
                    waterLevelEffect == 4 ||
                    waterLevelEffect == 5 ||
                    waterLevelEffect == 7 -> "DEPIT"

            /**
            1	depth known
            2	depth unknown
            3	doubtful sounding
            4	unreliable sounding
            5	no bottom found at value shown
            6	least depth known
            7	least depth unknown, safe clearance at value shown
            8	value reported (not surveyed)
            9	value reported (not confirmed)
            10	maintained depth
            11	not regularly maintained
             */
            quality.contains(1) ||
                    quality.contains(5) ||
                    quality.contains(6) ||
                    quality.contains(10) -> {
                when {
                    meters <= 0.0 -> "DEPIT"
                    meters <= Singletons.config.shallowDepth -> "DEPVS"
                    meters <= Singletons.config.safetyDepth -> "DEPMS"
                    meters <= Singletons.config.deepDepth -> "DEPMD"
                    meters > Singletons.config.deepDepth -> "DEPDW"
                    else -> throw IllegalStateException("unexpected VALSOU $meters")
                }
            }

            else -> null
        } ?: "DEPVS"

        log.debug("found area fill color for $key $ac VALSOU=$meters QUASOU=$quality WATLEV=$waterLevelEffect")
        feature.props["AC"] = ac
    }

    override fun preTileEncode(feature: ChartFeature) {
        encodeAreaColor(feature)
        encodeAreaPattern(feature)
        encodePointSymbol(feature)
    }

    private fun encodePointSymbol(feature: ChartFeature) {
        /* /control/symbols/OBSTRN/CATOBS
        Enum
        1	snag / stump
        2	wellhead
        3	diffuser
        4	crib
        5	fish haven
        6	foul area
        7	foul ground
        8	ice boom
        9	ground tackle
        10	boom
         */
        val category = feature.props.intValue("CATOBS") ?: 0

        /* /control/symbols/OBSTRN/WATLEV
        Enum
        1	partly submerged at high water
        2	always dry
        3	always under water/submerged
        4	covers and uncovers
        5	awash
        6	subject to inundation or flooding
        7	floating
         */
        val waterLevelEffect = feature.props.intValue("WATLEV") ?: 0


        /* /control/symbols/OBSTRN/QUASOU
        List
        1	depth known
        2	depth unknown
        3	doubtful sounding
        4	unreliable sounding
        5	no bottom found at value shown
        6	least depth known
        7	least depth unknown, safe clearance at value shown
        8	value reported (not surveyed)
        9	value reported (not confirmed)
        10	maintained depth
        11	not regularly maintained
         */
        val qualityOfSounding = feature.props.intValues("QUASOU")

        val sy = when {
            category == 8 || category == 10  -> "FLTHAZ02"
            category == 6 -> "FOULAR01"
            category == 9 -> "ACHARE02"
//            category == 1 || category == 2 || category == 3 || category == 4 || category == 5 -> null
            category == 7 -> "FOULGND1"
            qualityOfSounding.contains(1) -> ""
            waterLevelEffect == 4 -> "OBSTRN03"
            waterLevelEffect == 3 -> "OBSTRN02"
//            waterLevelEffect == 4 -> "OBSTRN01"
            waterLevelEffect == 7 -> "FLTHAZ02"
//            qualityOfSounding.contains(1) ->
            else -> null
        } ?: "ISODGR51"
        feature.props["SY"] = sy
        log.debug("found symbol for layer $key = $sy CATOBS=$category WATLEV=$waterLevelEffect")
    }
}

package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.geo.symbols.stringValue
import io.madrona.njord.layers.attributehelpers.Topshp
import io.madrona.njord.layers.attributehelpers.Topshp.Companion.topshp
import io.madrona.njord.model.*

/**
 * S-52_PresLib_v4.0
 * Topmark objects are to be symbolized through
 * consideration of their platforms e.g. a buoy. Therefore this conditional
 * symbology procedure searches for platforms by looking for other objects
 * that are located at the same position. Based on the finding whether the
 * platform is rigid or floating, the respective upright or sloping symbol is
 * selected and presented at the objects location. Buoy symbols and
 * topmark symbols have been carefully designed to fit to each other when
 * combined at the same position. The result is a composed symbol that
 * looks like the traditional symbols the mariner is used to.
 */
enum class TopmarPlatform {
    FLOATING,
    RIGID
}

data class TopmarData(
    val platform: TopmarPlatform,
    val assoc: List<String>
) {

    fun addTo(props: MutableMap<String, Any?>) {
        props["_PTFM"] = platform.name
        props["_ASOC"] = assoc
    }

    companion object {
        fun fromAssoc(assoc: List<String>): TopmarData {
            val platform = if (assoc.isEmpty()) TopmarPlatform.RIGID else TopmarPlatform.FLOATING
            return TopmarData(platform, assoc)
        }
    }
}

/**
 * Geometry Primitives: Point
 *
 * Object: Top mark
 *
 * Acronym: TOPMAR
 *
 * Code: 144
 */
class Topmar(
    private val featureDao: FeatureDao = Singletons.featureDao
) : Layerable() {

    private suspend fun topmarData(feature: ChartFeature): TopmarData {
        return feature.props.stringValue("LNAM")?.let { lnam ->
            TopmarData.fromAssoc(featureDao.findAssociatedLayerNames(lnam))
        } ?: TopmarData(TopmarPlatform.RIGID, emptyList())
    }

    override suspend fun preTileEncode(feature: ChartFeature) {
        val data = topmarData(feature)
        data.addTo(feature.props)
        when (data.platform) {
            TopmarPlatform.FLOATING -> {
                when (feature.topshp()) {
                    Topshp.CONE_POINT_UP -> feature.pointSymbol(Sprite.TOPMAR02) //1
                    Topshp.CONE_POINT_DOWN -> feature.pointSymbol(Sprite.TOPMAR04) //2
                    Topshp.SPHERE -> feature.pointSymbol(Sprite.TOPMAR10) //3
                    Topshp.TWO_SPHERE -> feature.pointSymbol(Sprite.TOPMAR12) //4
                    Topshp.CYLINDER_CAN -> feature.pointSymbol(Sprite.TOPMAR13) //5
                    Topshp.BOARD -> feature.pointSymbol(Sprite.TOPMAR14) //6
                    Topshp.X_SHAPE -> feature.pointSymbol(Sprite.TOPMAR65) //7
                    Topshp.UPRIGHT_CROSS -> feature.pointSymbol(Sprite.TOPMAR17) //8
                    Topshp.CUBE_POINT_UP -> feature.pointSymbol(Sprite.TOPMAR16) //9
                    Topshp.TWO_CONES_POINT_TO_POINT -> feature.pointSymbol(Sprite.TOPMAR08) //10
                    Topshp.TWO_CONES_BASE_TO_BASE -> feature.pointSymbol(Sprite.TOPMAR07) //11
                    Topshp.RHOMBUS_DIAMOND -> feature.pointSymbol(Sprite.TOPMAR14) //12
                    Topshp.TWO_CONES_POINTS_UPWARD -> feature.pointSymbol(Sprite.TOPMAR05) //13
                    Topshp.TWO_CONES_POINTS_DOWNWARD -> feature.pointSymbol(Sprite.TOPMAR06) //14
                    Topshp.BESOM_POINT_UP_BROOM_OR_PERCH -> feature.pointSymbol(Sprite.TMARDEF2) //15
                    Topshp.BESOM_POINT_DOWN_BROOM_OR_PERCH -> feature.pointSymbol(Sprite.TMARDEF2) //16
                    Topshp.FLAG -> feature.pointSymbol(Sprite.TMARDEF2) //17
                    Topshp.SPHERE_OVER_RHOMBUS -> feature.pointSymbol(Sprite.TOPMAR10) //18
                    Topshp.SQUARE -> feature.pointSymbol(Sprite.TOPMAR13) //19
                    Topshp.RECTANGLE_HORIZONTAL -> feature.pointSymbol(Sprite.TOPMAR14) //20
                    Topshp.RECTANGLE_VERTICAL -> feature.pointSymbol(Sprite.TOPMAR13) //21
                    Topshp.TRAPEZIUM_UP -> feature.pointSymbol(Sprite.TOPMAR14) //22
                    Topshp.TRAPEZIUM_DOWN -> feature.pointSymbol(Sprite.TOPMAR14) //23
                    Topshp.TRIANGLE_POINT_UP -> feature.pointSymbol(Sprite.TOPMAR02) //24
                    Topshp.TRIANGLE_POINT_DOWN -> feature.pointSymbol(Sprite.TOPMAR04) //25
                    Topshp.CIRCLE -> feature.pointSymbol(Sprite.TOPMAR10) //26
                    Topshp.TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER -> feature.pointSymbol(Sprite.TOPMAR17) //27
                    Topshp.T_SHAPE -> feature.pointSymbol(Sprite.TOPMAR18) //28
                    Topshp.TRIANGLE_POINTING_UP_OVER_A_CIRCLE -> feature.pointSymbol(Sprite.TOPMAR02) //29
                    Topshp.UPRIGHT_CROSS_OVER_A_CIRCLE -> feature.pointSymbol(Sprite.TOPMAR17) //30
                    Topshp.RHOMBUS_OVER_A_CIRCLE -> feature.pointSymbol(Sprite.TOPMAR14) //31
                    Topshp.CIRCLE_OVER_A_TRIANGLE_POINTING_UP -> feature.pointSymbol(Sprite.TOPMAR10) //32
                    Topshp.OTHER_SHAPE, //33
                    null -> feature.pointSymbol(Sprite.TMARDEF2)
                }
            }

            TopmarPlatform.RIGID -> {
                when (feature.topshp()) {
                    Topshp.CONE_POINT_UP -> feature.pointSymbol(Sprite.TOPMAR22)
                    Topshp.CONE_POINT_DOWN -> feature.pointSymbol(Sprite.TOPMAR24)
                    Topshp.SPHERE -> feature.pointSymbol(Sprite.TOPMAR30)
                    Topshp.TWO_SPHERE -> feature.pointSymbol(Sprite.TOPMAR32)
                    Topshp.CYLINDER_CAN -> feature.pointSymbol(Sprite.TOPMAR33)
                    Topshp.BOARD -> feature.pointSymbol(Sprite.TOPMAR34)
                    Topshp.X_SHAPE -> feature.pointSymbol(Sprite.TOPMAR85)
                    Topshp.UPRIGHT_CROSS -> feature.pointSymbol(Sprite.TOPMAR86)
                    Topshp.CUBE_POINT_UP -> feature.pointSymbol(Sprite.TOPMAR36)
                    Topshp.TWO_CONES_POINT_TO_POINT -> feature.pointSymbol(Sprite.TOPMAR28)
                    Topshp.TWO_CONES_BASE_TO_BASE -> feature.pointSymbol(Sprite.TOPMAR27)
                    Topshp.RHOMBUS_DIAMOND -> feature.pointSymbol(Sprite.TOPMAR14)
                    Topshp.TWO_CONES_POINTS_UPWARD -> feature.pointSymbol(Sprite.TOPMAR25)
                    Topshp.TWO_CONES_POINTS_DOWNWARD -> feature.pointSymbol(Sprite.TOPMAR26)
                    Topshp.BESOM_POINT_UP_BROOM_OR_PERCH -> feature.pointSymbol(Sprite.TOPMAR88)
                    Topshp.BESOM_POINT_DOWN_BROOM_OR_PERCH -> feature.pointSymbol(Sprite.TOPMAR87)
                    Topshp.FLAG -> feature.pointSymbol(Sprite.TMARDEF1)
                    Topshp.SPHERE_OVER_RHOMBUS -> feature.pointSymbol(Sprite.TOPMAR30)
                    Topshp.SQUARE -> feature.pointSymbol(Sprite.TOPMAR33)
                    Topshp.RECTANGLE_HORIZONTAL -> feature.pointSymbol(Sprite.TOPMAR34)
                    Topshp.RECTANGLE_VERTICAL -> feature.pointSymbol(Sprite.TOPMAR33)
                    Topshp.TRAPEZIUM_UP -> feature.pointSymbol(Sprite.TOPMAR34)
                    Topshp.TRAPEZIUM_DOWN -> feature.pointSymbol(Sprite.TOPMAR34)
                    Topshp.TRIANGLE_POINT_UP -> feature.pointSymbol(Sprite.TOPMAR22)
                    Topshp.TRIANGLE_POINT_DOWN -> feature.pointSymbol(Sprite.TOPMAR24)
                    Topshp.CIRCLE -> feature.pointSymbol(Sprite.TOPMAR30)
                    Topshp.TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER -> feature.pointSymbol(Sprite.TOPMAR86)
                    Topshp.T_SHAPE -> feature.pointSymbol(Sprite.TOPMAR89)
                    Topshp.TRIANGLE_POINTING_UP_OVER_A_CIRCLE -> feature.pointSymbol(Sprite.TOPMAR22)
                    Topshp.UPRIGHT_CROSS_OVER_A_CIRCLE -> feature.pointSymbol(Sprite.TOPMAR86)
                    Topshp.RHOMBUS_OVER_A_CIRCLE -> feature.pointSymbol(Sprite.TOPMAR14)
                    Topshp.CIRCLE_OVER_A_TRIANGLE_POINTING_UP -> feature.pointSymbol(Sprite.TOPMAR30)
                    Topshp.OTHER_SHAPE,
                    null -> feature.pointSymbol(Sprite.TMARDEF1)
                }
            }
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            iconOffset = listOf(
                "case",
                listOf("==", listOf("get", "_PTFM"), TopmarPlatform.RIGID.name),
                listOf("literal", listOf(0f, 0f)), // RIGID
                listOf("literal", listOf(0f, -8f)) // FLOATING
            ),
        ),
    )
}

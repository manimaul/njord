package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.geo.symbols.intValue
import io.madrona.njord.model.ChartFeature

/**
 * https://openenc.com/control/symbols/DAYMAR/TOPSHP
 *
 * Attribute: Topmark/daymark shape
 *
 * Acronym: TOPSHP
 *
 * Code: 171
 *
 * ID	Meaning
 * 1	cone, point up
 * 2	cone, point down
 * 3	sphere
 * 4	2 sphere
 * 5	cylinder (can)
 * 6	board
 * 7	x-shape (St. Andrew's cross)
 * 8	upright cross (St. George cross)
 * 9	cube, point up
 * 10	2 cones, point to point
 * 11	2 cones, base to base
 * 12	rhombus (diamond)
 * 13	2 cones (points upward)
 * 14	2 cones (points downward)
 * 15	besom, point up (broom or perch)
 * 16	besom, point down (broom or perch)
 * 17	flag
 * 18	sphere over rhombus
 * 19	square
 * 20	rectangle, horizontal
 * 21	rectangle, vertical
 * 22	trapezium, up
 * 23	trapezium, down
 * 24	triangle, point up
 * 25	triangle, point down
 * 26	circle
 * 27	two upright crosses (one over the other)
 * 28	T-shape
 * 29	triangle pointing up over a circle
 * 30	upright cross over a circle
 * 31	rhombus over a circle
 * 32	circle over a triangle pointing up
 * 33	other shape (see INFORM)
 *
 * Attribute type: E
 */
enum class Topshp {
    CONE_POINT_UP,
    CONE_POINT_DOWN,
    SPHERE,
    TWO_SPHERE,
    CYLINDER_CAN,
    BOARD,
    X_SHAPE, ///(ST.ANDREW'SCROSS)
    UPRIGHT_CROSS, //(ST.GEORGECROSS)
    CUBE_POINT_UP,
    TWO_CONES_POINT_TO_POINT,
    TWO_CONES_BASE_TO_BASE,
    RHOMBUS_DIAMOND,
    TWO_CONES_POINTS_UPWARD,
    TWO_CONES_POINTS_DOWNWARD,
    BESOM_POINT_UP_BROOM_OR_PERCH,
    BESOM_POINT_DOWN_BROOM_OR_PERCH,
    FLAG,
    SPHERE_OVER_RHOMBUS,
    SQUARE,
    RECTANGLE_HORIZONTAL,
    RECTANGLE_VERTICAL,
    TRAPEZIUM_UP,
    TRAPEZIUM_DOWN,
    TRIANGLE_POINT_UP,
    TRIANGLE_POINT_DOWN,
    CIRCLE,
    TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER,
    T_SHAPE,
    TRIANGLE_POINTING_UP_OVER_A_CIRCLE,
    UPRIGHT_CROSS_OVER_A_CIRCLE,
    RHOMBUS_OVER_A_CIRCLE,
    CIRCLE_OVER_A_TRIANGLE_POINTING_UP,
    OTHER_SHAPE;


    companion object {
        fun ChartFeature.topshp(): Topshp? {
            return when (props.intValue("TOPSHP")) {
                1 -> CONE_POINT_UP
                2 -> CONE_POINT_DOWN
                3 -> SPHERE
                4 -> TWO_SPHERE
                5 -> CYLINDER_CAN
                6 -> BOARD
                7 -> X_SHAPE
                8 -> UPRIGHT_CROSS
                9 -> CUBE_POINT_UP
                10 -> TWO_CONES_POINT_TO_POINT
                11 -> TWO_CONES_BASE_TO_BASE
                12 -> RHOMBUS_DIAMOND
                13 -> TWO_CONES_POINTS_UPWARD
                14 -> TWO_CONES_POINTS_DOWNWARD
                15 -> BESOM_POINT_UP_BROOM_OR_PERCH
                16 -> BESOM_POINT_DOWN_BROOM_OR_PERCH
                17 -> FLAG
                18 -> SPHERE_OVER_RHOMBUS
                19 -> SQUARE
                20 -> RECTANGLE_HORIZONTAL
                21 -> RECTANGLE_VERTICAL
                22 -> TRAPEZIUM_UP
                23 -> TRAPEZIUM_DOWN
                24 -> TRIANGLE_POINT_UP
                25 -> TRIANGLE_POINT_DOWN
                26 -> CIRCLE
                27 -> TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER
                28 -> T_SHAPE
                29 -> TRIANGLE_POINTING_UP_OVER_A_CIRCLE
                30 -> UPRIGHT_CROSS_OVER_A_CIRCLE
                31 -> RHOMBUS_OVER_A_CIRCLE
                32 -> CIRCLE_OVER_A_TRIANGLE_POINTING_UP
                33 -> OTHER_SHAPE
                else -> null
            }
        }
    }
}
//! `attributehelpers/Topshp.kt`.

crate::layers::attributehelpers::single_attribute! {
    /// <https://openenc.com/control/symbols/DAYMAR/TOPSHP>
    /// Attribute: Topmark/daymark shape
    /// Acronym: TOPSHP
    /// Code: 171
    /// ID	Meaning
    /// 1	cone, point up
    /// 2	cone, point down
    /// 3	sphere
    /// 4	2 sphere
    /// 5	cylinder (can)
    /// 6	board
    /// 7	x-shape (St. Andrew's cross)
    /// 8	upright cross (St. George cross)
    /// 9	cube, point up
    /// 10	2 cones, point to point
    /// 11	2 cones, base to base
    /// 12	rhombus (diamond)
    /// 13	2 cones (points upward)
    /// 14	2 cones (points downward)
    /// 15	besom, point up (broom or perch)
    /// 16	besom, point down (broom or perch)
    /// 17	flag
    /// 18	sphere over rhombus
    /// 19	square
    /// 20	rectangle, horizontal
    /// 21	rectangle, vertical
    /// 22	trapezium, up
    /// 23	trapezium, down
    /// 24	triangle, point up
    /// 25	triangle, point down
    /// 26	circle
    /// 27	two upright crosses (one over the other)
    /// 28	T-shape
    /// 29	triangle pointing up over a circle
    /// 30	upright cross over a circle
    /// 31	rhombus over a circle
    /// 32	circle over a triangle pointing up
    /// 33	other shape (see INFORM)
    /// Attribute type: E
    pub enum Topshp = "TOPSHP" {
        CONE_POINT_UP = 1,
        CONE_POINT_DOWN = 2,
        SPHERE = 3,
        TWO_SPHERE = 4,
        CYLINDER_CAN = 5,
        BOARD = 6,
        X_SHAPE = 7,
        UPRIGHT_CROSS = 8,
        CUBE_POINT_UP = 9,
        TWO_CONES_POINT_TO_POINT = 10,
        TWO_CONES_BASE_TO_BASE = 11,
        RHOMBUS_DIAMOND = 12,
        TWO_CONES_POINTS_UPWARD = 13,
        TWO_CONES_POINTS_DOWNWARD = 14,
        BESOM_POINT_UP_BROOM_OR_PERCH = 15,
        BESOM_POINT_DOWN_BROOM_OR_PERCH = 16,
        FLAG = 17,
        SPHERE_OVER_RHOMBUS = 18,
        SQUARE = 19,
        RECTANGLE_HORIZONTAL = 20,
        RECTANGLE_VERTICAL = 21,
        TRAPEZIUM_UP = 22,
        TRAPEZIUM_DOWN = 23,
        TRIANGLE_POINT_UP = 24,
        TRIANGLE_POINT_DOWN = 25,
        CIRCLE = 26,
        TWO_UPRIGHT_CROSSES_ONE_OVER_THE_OTHER = 27,
        T_SHAPE = 28,
        TRIANGLE_POINTING_UP_OVER_A_CIRCLE = 29,
        UPRIGHT_CROSS_OVER_A_CIRCLE = 30,
        RHOMBUS_OVER_A_CIRCLE = 31,
        CIRCLE_OVER_A_TRIANGLE_POINTING_UP = 32,
        OTHER_SHAPE = 33,
    }
}

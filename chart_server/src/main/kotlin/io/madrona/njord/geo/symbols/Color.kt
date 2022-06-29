package io.madrona.njord.geo.symbols

/**
 * COLOUR Attribute
 * ID	Meaning	INT 1	S-4
 * 1	white	IP 11.1;	450.2-3;
 * 2	black
 * 3	red	IP 11.2;	450.2-3;
 * 4	green	IP 11.3;	450.2-3;
 * 5	blue	IP 11.4;	450.2-3;
 * 6	yellow	IP 11.6;	450.2-3;
 * 7	grey
 * 8	brown
 * 9	amber	IP 11.8;	450.2-3;
 * 10	violet	IP 11.5;	450.2-3;
 * 11	orange	IP 11.7;	450.2-3;
 * 12	magenta
 * 13	pink
 */
enum class Color(val code: Int) {
    White(1),
    Black(2),
    Red(3),
    Green(4),
    Blue(5),
    Yellow(6),
    Grey(7),
    Brown(8),
    Amber(9),
    Violet(10),
    Orange(11),
    Magenta(12),
    Pink(13);

    companion object {
        fun fromProp(prop: S57Prop?): List<Color> {
            return prop.listFrom("COLOUR") { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}

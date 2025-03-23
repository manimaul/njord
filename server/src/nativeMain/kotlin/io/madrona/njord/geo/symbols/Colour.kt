package io.madrona.njord.geo.symbols

import io.madrona.njord.model.ChartFeature

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
enum class Colour {
    White,
    Black,
    Red,
    Green,
    Blue,
    Yellow,
    Grey,
    Brown,
    Amber,
    Violet,
    Orange,
    Magenta,
    Pink;

    companion object {
        fun ChartFeature.colors(): List<Colour> {
            return props.intValues("COLOUR").mapNotNull {
                when (it) {
                    1 -> White
                    2 -> Black
                    3 -> Red
                    4 -> Green
                    5 -> Blue
                    6 -> Yellow
                    7 -> Grey
                    8 -> Brown
                    9 -> Amber
                    10 -> Violet
                    11 -> Orange
                    12 -> Magenta
                    13 -> Pink
                    else -> null
                }
            }
        }
    }
}

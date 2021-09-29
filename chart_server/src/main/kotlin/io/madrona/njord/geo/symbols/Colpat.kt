package io.madrona.njord.geo.symbols

import mil.nga.sf.geojson.Feature

/**
 * COLPAT Attribute
 * ID	Meaning	INT 1	S-4
 * 1	horizontal stripes
 * 2	vertical stripes
 * 3	diagonal stripes
 * 4	squared
 * 5	stripes (direction unknown)
 * 6	border stripe
 */
enum class Colpat(val code: Int) {
    HorizontalStripes(1),
    VerticalStripes(2),
    DiagonalStripes(3),
    Squared(4),
    Stripes(5),
    BorderStripe(6);

    companion object {
        fun fromProp(prop: S57Prop?): List<Colpat> {
            return prop.listFrom("COLPAT") { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}




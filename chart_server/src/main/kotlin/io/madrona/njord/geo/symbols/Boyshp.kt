package io.madrona.njord.geo.symbols

import mil.nga.sf.geojson.Feature

/**
 * BOYSHP
 * ID	Meaning	INT 1	S-4
 *  1	conical (nun, ogival)	IQ 20;	462.2
 *  2	can (cylindrical)	IQ 21;	462.3
 *  3	spherical	IQ 22;	462.4
 *  4	pillar	IQ 23;	462.5
 *  5	spar (spindle)	IQ 24;	462.6
 *  6	barrel (tun)	IQ 25;	462.7
 *  7	super-buoy	IQ 26;	462.9
 *  8	ice buoy
 */
enum class Boyshp(val code: Int) {
    Conical(1),
    Can(2),
    Spherical(3),
    Pillar(4),
    Spar(5),
    Barrel(6),
    SuperBuoy(7),
    IceBuoy(8);

    companion object {
        fun fromProp(prop: S57Prop?): Boyshp? {
            return prop?.let {
                it["BOYSHP"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}

fun Feature.addBoyShp() {
    val colpat = Colpat.fromProp(properties)
    val color = Color.fromProp(properties)
    val sy: String  = when (Boyshp.fromProp(properties)) {
        Boyshp.Conical -> when (color) {
            listOf(Color.White) -> "BOYCON01"
            listOf(Color.Red) -> "BOYCON60"
            listOf(Color.Green) -> "BOYCON61"
            listOf(Color.Yellow) -> "BOYCON62"
            listOf(Color.Black, Color.Red, Color.Black) -> "BOYCON63"
            listOf(Color.Black) -> "BOYCON64"
            listOf(Color.Green, Color.White, Color.Green, Color.White, Color.Green) -> "BOYCON65"
            listOf(Color.Red, Color.Green, Color.Red) -> "BOYCON66"
            listOf(Color.Green, Color.Red, Color.Green) -> "BOYCON67"
            listOf(Color.Green, Color.Red) -> "BOYCON68"
            listOf(Color.Black, Color.Yellow) -> "BOYCON69"
            listOf(Color.Yellow, Color.Black) -> "BOYCON70"
            listOf(Color.Black, Color.Yellow, Color.Black) -> "BOYCON71"
            listOf(Color.Yellow, Color.Black, Color.Yellow) -> "BOYCON72"
            listOf(Color.Green, Color.White) -> "BOYCON73"
            listOf(Color.White, Color.Orange) -> "BOYCON77"
            listOf(Color.Red, Color.White) -> when (colpat) {
                listOf(Colpat.VerticalStripes) -> "BOYCON78"
                else -> "BOYCON01"
            }
            listOf(Color.Red, Color.Green) -> "BOYCON79"
            listOf(Color.White, Color.Orange, Color.White) -> "BOYCON80"
            listOf(Color.Blue, Color.Red, Color.White, Color.Blue) -> when (colpat) {
                listOf(Colpat.HorizontalStripes, Colpat.VerticalStripes) -> "BOYCON81"
                else -> "BOYCON01"
            }
            else -> "BOYCON01"
        }
        Boyshp.Can -> when (color) {
            listOf(Color.Red) -> "BOYCAN60"
            listOf(Color.Green) -> "BOYCAN61"
            listOf(Color.White) -> "BOYCAN62"
            listOf(Color.Yellow) -> "BOYCAN63"
            listOf(Color.Black) -> "BOYCAN64"
            listOf(Color.Black, Color.Yellow) -> "BOYCAN68"
            listOf(Color.Yellow, Color.Black) -> "BOYCAN69"
            listOf(Color.Black, Color.Yellow, Color.Black) -> "BOYCAN70"
            listOf(Color.Yellow, Color.Black, Color.Yellow) -> "BOYCAN71"
            listOf(Color.Red, Color.Green, Color.Red) -> "BOYCAN73"
            listOf(Color.White, Color.Red) -> when (colpat) {
                listOf(Colpat.VerticalStripes) -> "BOYCAN74"
                else -> "BOYCAN65"
            }
            listOf(Color.Red, Color.Green) -> "BOYCAN75"
            listOf(Color.Black, Color.Red, Color.Black) -> "BOYCAN76"
            listOf(Color.White, Color.Orange) -> "BOYCAN77"
            listOf(Color.White, Color.Orange, Color.White) -> "BOYCAN78"
            listOf(Color.Orange) -> "BOYCAN79"
            listOf(Color.Red, Color.White) -> "BOYCAN80"
            listOf(Color.Orange, Color.White) -> "BOYCAN81"
            listOf(Color.Red, Color.White, Color.Red, Color.White, Color.Red) -> "BOYCAN82"
            listOf(Color.Red, Color.White, Color.Red, Color.White) -> "BOYCAN83"
            else -> "BOYCAN65"

        }
        Boyshp.Spherical -> when (color) {
            listOf(Color.White) -> "BOYSPH05"
            listOf(Color.Red) -> "BOYSPH60"
            listOf(Color.Green) -> "BOYSPH61"
            listOf(Color.Yellow) -> "BOYSPH62"
            listOf(Color.White, Color.Red, Color.White, Color.Red, Color.White) -> when (colpat) {
                listOf(Colpat.VerticalStripes) -> "BOYSPH65"
                else -> "BOYSPH01"
            }
            listOf(Color.Red, Color.Green, Color.Red) -> "BOYSPH66"
            listOf(Color.Green, Color.Red, Color.Green) -> "BOYSPH67"
            listOf(Color.Black, Color.Yellow) -> "BOYSPH68"
            listOf(Color.Yellow, Color.Black) -> "BOYSPH69"
            listOf(Color.Black, Color.Yellow, Color.Black) -> "BOYSPH70"
            listOf(Color.Yellow, Color.Black, Color.Yellow) -> "BOYSPH71"
            listOf(Color.Red, Color.Green) -> "BOYSPH74"
            listOf(Color.Green, Color.Red) -> "BOYSPH75"
            listOf(Color.White, Color.Orange) -> "BOYSPH76"
            listOf(Color.Red, Color.White) -> when (colpat) {
                listOf(Colpat.VerticalStripes) -> "BOYSPH78"
                else -> "BOYSPH01"
            }
            else -> "BOYSPH01"
        }
        Boyshp.Pillar -> when (color) {
            else -> ""
        }
        Boyshp.Spar -> when (color) {
            else -> ""
        }
        Boyshp.Barrel -> when (color) {
            else -> ""
        }
        Boyshp.SuperBuoy -> when (color) {
            else -> ""
        }
        Boyshp.IceBuoy -> when (color) {
            else -> ""
        }
        else -> ""
    }
    properties?.put("SY", sy)
}


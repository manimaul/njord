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

fun S57Prop.addBoyShp() {
    val colpat = Colpat.fromProp(this)
    val color = Color.fromProp(this)
    val sy: String? = when (Boyshp.fromProp(this)) {
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
            listOf(Color.Red) -> "BOYPIL60"
            listOf(Color.Green) -> "BOYPIL61"
            listOf(Color.Yellow) -> "BOYPIL62"
            listOf(Color.Black) -> "BOYPIL63"
            listOf(Color.Orange) -> "BOYPIL64"
            listOf(Color.Grey) -> "BOYPIL65"
            listOf(Color.Red, Color.Green, Color.Red) -> "BOYPIL66"
            listOf(Color.Green, Color.Red, Color.Green) -> "BOYPIL67"
            listOf(Color.Black, Color.Yellow) -> "BOYPIL68"
            listOf(Color.Yellow, Color.Black) -> "BOYPIL69"
            listOf(Color.Yellow, Color.Black, Color.Yellow) -> "BOYPIL70"
            listOf(Color.Black, Color.Red, Color.Black) -> "BOYPIL72"
            listOf(Color.Red, Color.White) -> when (colpat) {
                listOf(Colpat.VerticalStripes) -> "BOYPIL73"
                else -> "BOYPIL76"
            }
            listOf(Color.Red, Color.Green) -> "BOYPIL74"
            listOf(Color.Green, Color.Red) -> "BOYPIL75"
            listOf(Color.Green, Color.White) -> "BOYPIL77"
            listOf(Color.Red, Color.White, Color.Red, Color.White) -> "BOYPIL78"
            listOf(Color.Green, Color.White, Color.Green, Color.White) -> "BOYPIL79"
            listOf(Color.Red, Color.Yellow) -> "BOYPIL80"
            listOf(Color.White, Color.Orange) -> "BOYPIL81"
            else -> "BOYPIL01"
        }
        Boyshp.Spar -> when (color) {
            listOf(Color.Orange, Color.White, Color.Orange, Color.White) -> "BOYSPR04"
            listOf(Color.White) -> "BOYSPR05"
            listOf(Color.Red) -> "BOYSPR60"
            listOf(Color.Green) -> "BOYSPR61"
            listOf(Color.Yellow) -> "BOYSPR62"
            listOf(Color.Red, Color.White, Color.Red) -> "BOYSPR65"
            listOf(Color.Black, Color.Yellow) -> "BOYSPR68"
            listOf(Color.Yellow, Color.Black) -> "BOYSPR69"
            listOf(Color.Black, Color.Yellow, Color.Black) -> "BOYSPR70"
            listOf(Color.Yellow, Color.Black, Color.Yellow) -> "BOYSPR71"
            listOf(Color.Black, Color.Red, Color.Black) -> "BOYSPR72"
            else -> "BOYSPR01"
        }
        Boyshp.Barrel -> when (color) {
            listOf(Color.Red) -> "BOYBAR60"
            listOf(Color.Green) -> "BOYBAR61"
            listOf(Color.Yellow) -> "BOYBAR62"
            else -> "BOYBAR01"
        }
        Boyshp.SuperBuoy -> "BOYSUP01" //todo:()
        Boyshp.IceBuoy -> "BOYSPR01" //todo:()
        else -> null
    }
    sy?.let { put("SY", sy) }
}


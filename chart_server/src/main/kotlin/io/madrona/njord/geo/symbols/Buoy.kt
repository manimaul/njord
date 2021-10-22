package io.madrona.njord.geo.symbols

import io.madrona.njord.geo.symbols.attributes.Boyshp
import io.madrona.njord.geo.symbols.attributes.Catspm

private fun conical(color: List<Color>, colpat: List<Colpat>) : String {
    return when (color) {
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
}

private fun pillar(color: List<Color>, colpat: List<Colpat>) : String {
    return when (color) {
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
}

private fun can(color: List<Color>, colpat: List<Colpat>) : String {
    return when (color) {
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
}

private fun spherical(color: List<Color>, colpat: List<Colpat>) : String {
    return when (color) {
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
}

private fun spar(color: List<Color>) : String {
    return when (color) {
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
}

private fun barrel(color: List<Color>) : String {
    return when (color) {
        listOf(Color.Red) -> "BOYBAR60"
        listOf(Color.Green) -> "BOYBAR61"
        listOf(Color.Yellow) -> "BOYBAR62"
        else -> "BOYBAR01"
    }
}

private fun buoy(shape: Boyshp?, color: List<Color>, colpat: List<Colpat>) : String? {
    return when (shape) {
        Boyshp.Conical -> conical(color, colpat)
        Boyshp.Can -> can(color, colpat)
        Boyshp.Spherical -> spherical(color, colpat)
        Boyshp.Pillar -> pillar(color, colpat)
        Boyshp.Spar -> spar(color)
        Boyshp.Barrel -> barrel(color)
        Boyshp.SuperBuoy -> "BOYSUP01" //todo:()
        Boyshp.IceBuoy -> "BOYSPR01" //todo:()
        else -> null
    }
}

fun S57Prop.addBcnLat() {
    //todo: (bcnshp)
}


//completed all "Paper" permutations in chartsymbols.xml
fun S57Prop.addBoySpp() {
    val catspm = Catspm.fromProp(this)
    val shape = Boyshp.fromProp(this)
    val color = Color.fromProp(this)
    val colpat = Colpat.fromProp(this)
    val sy = when (catspm) {
        Catspm.OutfallMark -> when (shape) {
            Boyshp.Can -> when (color) {
                listOf(Color.Red) -> "BOYCAN60"
                else -> null
            }
            else -> null
        }
        Catspm.OceanDataAcquisitionSystem -> "BOYSUP01"
        Catspm.MooringMark -> when (shape) {
            Boyshp.Can -> "BOYMOR31"
            else -> null
        }
        Catspm.LargeAutomaticNavigationalBuoy -> "BOYSUP03"
        Catspm.NoticeMark -> when (color) {
            listOf(Color.Yellow) -> "NOTBRD12"
            else -> "NOTBRD11"
        }
        Catspm.RefugeBeacon -> "BCNGEN01"
        else -> null
    } ?: buoy(shape, color, colpat)
    put("SY", sy)
}

//completed all "Paper" permutations in chartsymbols.xml
fun S57Prop.addBoyLat() {
    val shape = Boyshp.fromProp(this)
    val color = Color.fromProp(this)
    val colpat = Colpat.fromProp(this)
    val sy: String? = buoy(shape, color, colpat)
    sy?.let { put("SY", sy) }
}

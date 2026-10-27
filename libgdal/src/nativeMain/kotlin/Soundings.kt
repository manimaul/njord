import kotlinx.serialization.json.JsonElement
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

data class DisplayDepths(
    val meters: Double,
    val metersWhole: Int,
    val metersTenths: Int,
    val feet: Int,
    val fathoms: Int,
    val fathomsFeet: Int
)

/**
 * Every part is computed on the magnitude and then given the sounding's sign, so a drying height
 * (negative, S-57 convention) carries the same digits as the equivalent depth with every part
 * negative. S-52 SNDFRM04 draws those digits positive with the SOUNDSA1 underline; the style
 * applies `abs` and reads the sign from `METERS` (see Soundg.kt).
 */
fun displayDepths(meters: Double) : DisplayDepths {
    val magnitude = abs(meters)
    val sgn = sign(meters).toInt()

    val feet: Double = magnitude * 3.28084
    var feetWhole: Int = feet.toInt()
    if (feet - feetWhole >= 0.9) {
        feetWhole += 1
    }
    val fathoms: Double = (magnitude * 0.546807)
    val fathomsWhole: Int = fathoms.toInt()
    val fathomsFeet: Int = ((fathoms - fathomsWhole) * 6.0).toInt()
    // S-57 encodes depths to one decimal, so split on the decimal tenths rather than the binary
    // double: 2.3 is 2.29999… and truncating (2.3 - 2) * 10 would read 2.2. The epsilon absorbs
    // that representation error; anything beyond the first decimal still truncates, so a label
    // never reads deeper than the sounding.
    val tenthsTotal: Int = floor(magnitude * 10.0 + 1e-6).toInt()
    return DisplayDepths(
        meters = meters,
        metersWhole = sgn * (tenthsTotal / 10),
        metersTenths = sgn * (tenthsTotal % 10),
        feet = sgn * feetWhole,
        fathoms = sgn * fathomsWhole,
        fathomsFeet = sgn * fathomsFeet,
    )
}

fun MutableMap<String, JsonElement>.addSoundingConversions(meters: Double) {
    val depths = displayDepths(meters)
    this["METERS"] = meters.json // need to add to /control/symbols/OBSTRN/VALSOU
    this["METERS_W"] = depths.metersWhole.json
    this["METERS_T"] = depths.metersTenths.json
    this["FEET"] = depths.feet.json
    this["FATHOMS"] = depths.fathoms.json
    this["FATHOMS_FT"] = depths.fathomsFeet.json
}

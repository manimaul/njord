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

fun displayDepths(meters: Double) : DisplayDepths {
    val feet: Double = meters * 3.28084
    var feetWhole: Int = feet.toInt()
    if (feet - feetWhole >= 0.9) {
        feetWhole += 1
    }
    val fathoms: Double = (meters * 0.546807)
    val fathomsWhole: Int = fathoms.toInt()
    val fathomsFeet: Int = ((fathoms - fathomsWhole) * 6.0).toInt()
    // S-57 encodes depths to one decimal, so split on the decimal tenths rather than the binary
    // double: 2.3 is 2.29999… and truncating (2.3 - 2) * 10 would read 2.2. The epsilon absorbs
    // that representation error; anything beyond the first decimal still truncates (toward zero,
    // so a label never reads deeper than the sounding and drying heights keep their sign).
    val tenthsTotal: Int = (floor(abs(meters) * 10.0 + 1e-6) * sign(meters)).toInt()
    val metersWhole: Int = tenthsTotal / 10
    val metersTenths: Int = tenthsTotal % 10
    return DisplayDepths(
        meters = meters,
        metersWhole = metersWhole,
        metersTenths = metersTenths,
        feet = feetWhole,
        fathoms = fathomsWhole,
        fathomsFeet = fathomsFeet
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

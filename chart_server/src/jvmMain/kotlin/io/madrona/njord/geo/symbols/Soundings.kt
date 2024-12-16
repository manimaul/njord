package io.madrona.njord.geo.symbols

import io.madrona.njord.util.logger
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position

//private val FORMAT = DecimalFormat("#.#").apply {
//    roundingMode = RoundingMode.DOWN
//}

val log by lazy {
    logger<Feature>()
}

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
    val metersWhole: Int = meters.toInt()
    val metersTenths = ((meters - metersWhole) * 10.0).toInt()
    return DisplayDepths(
        meters = meters,
        metersWhole = metersWhole,
        metersTenths = metersTenths,
        feet = feetWhole,
        fathoms = fathomsWhole,
        fathomsFeet = fathomsFeet
    )
}

fun MutableMap<String, Any?>.addSoundingConversions(meters: Double) {
    val depths = displayDepths(meters)
    this["METERS"] = meters // need to add to /control/symbols/OBSTRN/VALSOU
    this["METERS_W"] = depths.metersWhole
    this["METERS_T"] = depths.metersTenths
    this["FEET"] = depths.feet
    this["FATHOMS"] = depths.fathoms
    this["FATHOMS_FT"] = depths.fathomsFeet
}

fun Feature.addSounding() {
    (geometry as? Point)?.let {
        val meters = it.coordinates.z ?: 0.0
        properties["METERS"] = meters
        it.coordinates = Position(it.coordinates.x, it.coordinates.y)
        geometry = it
    } ?: run {
        log.error("unexpected geometry point $geometryType")
    }
}

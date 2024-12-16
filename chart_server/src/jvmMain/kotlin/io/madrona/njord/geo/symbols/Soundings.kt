package io.madrona.njord.geo.symbols

import io.madrona.njord.ext.json
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.FeatureBuilder
import io.madrona.njord.geojson.Point
import io.madrona.njord.geojson.Position
import io.madrona.njord.util.logger
import kotlinx.serialization.json.JsonElement

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

fun MutableMap<String, JsonElement>.addSoundingConversions(meters: Double) {
    val depths = displayDepths(meters)
    this["METERS"] = meters.json // need to add to /control/symbols/OBSTRN/VALSOU
    this["METERS_W"] = depths.metersWhole.json
    this["METERS_T"] = depths.metersTenths.json
    this["FEET"] = depths.feet.json
    this["FATHOMS"] = depths.fathoms.json
    this["FATHOMS_FT"] = depths.fathomsFeet.json
}

fun FeatureBuilder.addSounding() {
    (geo as? Point)?.let {
        val meters = it.position.z ?: 0.0
        addProperty("METERS", meters)
        geo = Point(Position(it.position.longitude, it.position.latitude))
    } ?: run {
        log.error("unexpected geometry point ${geo?.type}")
    }
}

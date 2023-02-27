package io.madrona.njord.geo.symbols

import io.madrona.njord.util.logger
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import java.math.RoundingMode
import java.text.DecimalFormat

private val FORMAT_M = DecimalFormat("#.##").apply {
    roundingMode = RoundingMode.DOWN
}

private val FORMAT_FT = DecimalFormat("#.#").apply {
    roundingMode = RoundingMode.DOWN
}

val log by lazy {
    logger<Feature>()
}

fun MutableMap<String, Any?>.addSounding(meters: Double) {
    val feet = meters * 3.28084
    val fathoms = (meters * 0.546807)
    val fathomsWhole = fathoms.toInt()
    val fathomsFeet = ((fathoms - fathomsWhole) * 6.0).toInt()
    this["METERS"] = FORMAT_M.format(meters)
    this["FEET"] = FORMAT_FT.format(feet)
    this["FATHOMS"] = fathomsWhole
    this["FATHOMS_FT"] = fathomsFeet
}

fun Feature.addSounding() {
    (geometry as? Point)?.let {
        val meters = it.coordinates.z ?: 0.0
        properties.addSounding(meters)
        it.coordinates = Position(it.coordinates.x, it.coordinates.y)
        geometry = it
    } ?: run {
        log.error("unexpected geometry point $geometryType")
    }
}

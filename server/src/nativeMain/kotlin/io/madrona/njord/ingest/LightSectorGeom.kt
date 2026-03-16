package io.madrona.njord.ingest

import io.madrona.njord.Singletons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun Double.toRadians() = this * PI / 180.0

/**
 * Returns WKT for the arc LineString and two radial LineStrings for a light sector.
 * Bearings are degrees from true north, clockwise (S-57 convention).
 */
fun lightSectorWkt(
    lon: Double, lat: Double,
    sector1: Double?,
    sector2: Double?,
    scale: Double?,
    majorLight: Boolean,
): LightSectorGeom {
    var radiusDeg = 1.0 / 60.0
    scale?.let {
        val zoom = Singletons.tileSystem.scaleToZoom(it, lat).roundToInt()
        if (zoom < 10) {
            val factor = 10 - zoom
            radiusDeg *= factor
        } else if (zoom > 10) {
            val factor = zoom - 10
            radiusDeg /= factor
        }
    }

    val latRad = lat.toRadians()
    val arcWkt: String
    val rad1Wkt: String?
    val rad2Wkt: String?

    if (majorLight && (sector1 == null || sector2 == null)) {
        val arcDeltas = buildArcDeltas(latRad, radiusDeg, 0.0, 360.0, steps = 70)
        arcWkt = "LINESTRING(${arcDeltas.joinToString { "${lon + it.first} ${lat + it.second}" }})"
        rad1Wkt = null
        rad2Wkt = null
    } else if (sector1 != null && sector2 != null) {
        val arcDeltas = buildArcDeltas(latRad, radiusDeg, sector2, sector1, steps = 70) // reversed: CCW arc fix
        arcWkt = "LINESTRING(${arcDeltas.joinToString { "${lon + it.first} ${lat + it.second}" }})"
        val p1 = arcDeltas.first()
        rad1Wkt = "LINESTRING($lon $lat, ${lon + p1.first} ${lat + p1.second})"
        val p2 = arcDeltas.last()
        rad2Wkt = "LINESTRING($lon $lat, ${lon + p2.first} ${lat + p2.second})"
    } else {
        throw IllegalStateException("missing sectors for minor light")
    }

    return LightSectorGeom(arcWkt, listOfNotNull(rad1Wkt, rad2Wkt))
}

/** Returns list of (dLon, dLat) deltas from center for each arc sample point. */
private fun buildArcDeltas(
    latRad: Double,
    radiusDeg: Double,
    sectr1: Double,
    sectr2: Double,
    steps: Int
): List<Pair<Double, Double>> {
    // Handle wrap-around through 360°: if sectr2 < sectr1, add 360 to sectr2
    val end = if (sectr2 < sectr1) sectr2 + 360.0 else sectr2
    val step = (end - sectr1) / steps
    val cosLat = cos(latRad)
    return (0..steps).map { i ->
        val bearingRad = (sectr1 + i * step).toRadians()
        val dLat = radiusDeg * cos(bearingRad)
        val dLon = radiusDeg * sin(bearingRad) / cosLat
        Pair(dLon, dLat)
    }
}

data class LightSectorGeom(
    val arkWkt: String,
    val radWkt: List<String>
)

package io.madrona.njord.util

import org.locationtech.jts.geom.Coordinate
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val longitude: Double,
    val latitude: Double
) : Point {
    override val x: Double
        get() = longitude
    override val y: Double
        get() = latitude
}

interface Point {
    val x: Number
    val y: Number
}

private fun haversineDistance(origin: GeoPoint, destination: GeoPoint) : Double {
    val radius = 6371.0
    val dLat = Math.toRadians(destination.latitude - origin.latitude)
    val dLon = Math.toRadians(destination.longitude - origin.longitude)
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) + cos(Math.toRadians(origin.latitude)) *
            cos(Math.toRadians(destination.latitude)) * sin(dLon / 2.0) * sin(dLon / 2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    val d = radius * c
    return d * 1000 // meters
}

private fun cartesianDistance(origin: GeoPoint, destination: GeoPoint) : Double {
    val proj = CRSFactory().createFromName("EPSG:3785") // spherical mercator, should work anywhere
    val point1 = proj.projection.project(ProjCoordinate(origin.x, origin.y), ProjCoordinate())
    val point2 = proj.projection.project(ProjCoordinate(destination.x, destination.y), ProjCoordinate())
    val coord1 = Coordinate(point1.x, point1.y)
    val coord2 = Coordinate(point2.x, point2.y)
    return coord1.distance(coord2)
}

private fun latitudeDistortion(latitude: Double) : Double {
    val origin = GeoPoint(0.0, latitude)
    val destination = GeoPoint(1.0, latitude)
    val hDist = haversineDistance(origin, destination)
    val cDist = cartesianDistance(origin, destination)
    return cDist / hDist
}

private fun getZoomForTrueScale(trueScale: Double) : Int {
    var t = 30
    val tp = 0.70
    var ts = trueScale * tp
    while (ts > 1.0) {
        ts /= 2.0
        t -= 1
    }
    return t
}

fun getZoom(scale: Int, latitude: Double) : Int {
    val trueScale = scale * latitudeDistortion(latitude)
    return getZoomForTrueScale(trueScale)
}

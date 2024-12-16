package io.madrona.njord.geojson

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A position is the fundamental geometry construct. The "coordinates" member of a geometry object is composed of one
 * position (in the case of a Point geometry), an array of positions (LineString or MultiPoint geometries), an array of
 * arrays of positions (Polygons, MultiLineStrings), or a multidimensional array of positions (MultiPolygon).
 *
 * A position is represented by an array of numbers. There must be at least two elements, and may be more. The order of
 * elements must follow x, y, z order (easting, northing, altitude for coordinates in a projected coordinate reference
 * system, or longitude, latitude, altitude for coordinates in a geographic coordinate reference system). Any number of
 * additional elements are allowed – interpretation and meaning of additional elements is beyond the scope of this specification.
 *
 * [https://geojson.org/geojson-spec.html#appendix-a-geometry-examples](Examples of positions and geometries are provided in Appendix A. Geometry Examples.)
 *
 * [https://geojson.org/geojson-spec.html#positions](Positions)
 */
@Serializable(with = PositionSerializer::class)
data class Position constructor(
    val coordinates: List<Double>
) {

    init {
        require(coordinates.size >= 2) { "at least 2 coordinates" }
    }

    constructor(longitude: Double, latitude: Double) : this(listOf(longitude, latitude))

    @Transient
    val x: Double = coordinates[0]
    @Transient
    val longitude = coordinates[0]
    @Transient
    val y: Double = coordinates[1]
    @Transient
    val latitude = coordinates[1]
    @Transient
    val z: Double? = if (coordinates.size > 2) coordinates[2] else null
    @Transient
    val altitude: Double? = if (coordinates.size > 2) coordinates[2] else null
    @Transient
    val m: Double? = if (coordinates.size > 3) coordinates[3] else null
}


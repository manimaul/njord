package io.madrona.njord.geojson

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * To include information on the coordinate range for geometries, features, or feature collections, a GeoJSON object may
 * have a member named "bbox". The value of the bbox member must be a 2*n array where n is the number of dimensions
 * represented in the contained geometries, with the lowest values for all axes followed by the highest values. The axes
 * order of a bbox follows the axes order of geometries. In addition, the coordinate reference system for the bbox is
 * assumed to match the coordinate reference system of the GeoJSON object of which it is a member.
 *
 * [https://geojson.org/geojson-spec.html#bounding-boxes](BoundingBox)
 */
@Serializable(with = BoundingBoxSerializer::class)
class BoundingBox constructor(
    val coordinates: List<Double>
) {

    constructor(west: Double, south: Double, east: Double, north: Double) : this(
        listOf(west, south, east, north)
    )

    constructor(west: Double, south: Double, east: Double, north: Double, z: Double?, m: Double?) : this(
        listOf(west, south, east, north, z, m).filterNotNull()
    )

    init {
        require(coordinates.size == 4 || coordinates.size == 6) {
            "Bounding Box coordinates must either have 4 or 6 values"
        }
    }

    @Transient
    val west: Double by lazy { coordinates[0] }

    @Transient
    val south: Double by lazy { coordinates[1] }

    @Transient
    val east: Double by lazy { coordinates[2] }

    @Transient
    val north: Double by lazy { coordinates[3] }

    @Transient
    val z: Double? = if (coordinates.size == 6) coordinates[4] else null

    @Transient
    val m: Double? = if (coordinates.size == 6) coordinates[5] else null
}


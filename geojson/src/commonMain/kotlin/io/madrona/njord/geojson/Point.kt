package io.madrona.njord.geojson

import kotlinx.serialization.*

/**
 * For type "Point", the "coordinates" member must be a single position.
 *
 * [https://geojson.org/geojson-spec.html#point](Point)
 */
@Serializable
data class Point(
    override val coordinates: List<Double>,
    override val bbox: BoundingBox? = null,
    override val crs: CoordinateReferenceSystem? = null,
) : Geometry {

    constructor(other: Point) : this(other.coordinates)
    constructor(position: Position) : this(position.coordinates)
    constructor(longitude: Double, latitude: Double) : this(listOf(longitude, latitude))
    constructor(longitude: Double, latitude: Double, zoom: Double, m: Double) : this(
        listOf(longitude, latitude, zoom, m)
    )

    init {
        require(coordinates.size >= 2) { "coordinates must be a single position" }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type = GeometryType.Point

    val position by lazy { Position(coordinates) }
}

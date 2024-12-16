package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable


/**
 * For type "LineString", the "coordinates" member must be an array of two or more positions.
 * A LinearRing is closed LineString with 4 or more positions. The first and last positions are equivalent(they represent equivalent points).
 * Though a LinearRing is not explicitly represented as a GeoJSON geometry type, it is referred to in the Polygon geometry type definition.
 *
 * [https://geojson.org/geojson-spec.html#linestring](LineString)
 */
@Serializable
data class LineString constructor(
    override val coordinates: List<Position>,
    override val bbox: BoundingBox? = null,
    override val crs: CoordinateReferenceSystem? = null
) : Geometry {

    constructor(vararg positions: Position) : this(positions.toList())

    init {
        require(coordinates.size >= 2) { "LineString points must have at least two points" }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type: GeometryType = GeometryType.LineString
}

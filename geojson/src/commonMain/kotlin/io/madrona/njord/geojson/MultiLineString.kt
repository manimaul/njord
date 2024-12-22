package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * For type "MultiLineString", the "coordinates" member must be an array of LineString coordinate arrays.
 *
 * [https://geojson.org/geojson-spec.html#multilinestring](MultiLineString)
 */
@Serializable
data class MultiLineString(
    override val coordinates: List<List<Position>>,
    override val bbox: BoundingBox? = null,
) : Geometry {

    constructor(vararg lineString: LineString) : this(lineString.toList().map { it.coordinates })

    override val crs: CoordinateReferenceSystem? = null

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type = GeometryType.MultiLineString
}
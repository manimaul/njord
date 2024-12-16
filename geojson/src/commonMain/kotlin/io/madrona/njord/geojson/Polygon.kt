package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * For type "Polygon", the "coordinates" member must be an array of LinearRing coordinate arrays.
 * For Polygons with multiple rings, the first must be the exterior ring and any others must be interior rings or holes.
 *
 * [https://geojson.org/geojson-spec.html#polygon](Polygon)
 */
@Serializable
data class Polygon(
    override val coordinates: List<List<Position>> = emptyList(),
    override val bbox: BoundingBox? = null,
    override val crs: CoordinateReferenceSystem? = null,
) : Geometry {

    constructor(vararg rings: LineString) : this(rings.toList().map { it.coordinates })

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type: GeometryType = GeometryType.Polygon
}

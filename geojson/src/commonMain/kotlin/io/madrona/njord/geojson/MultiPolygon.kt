package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * For type "MultiPolygon", the "coordinates" member must be an array of Polygon coordinate arrays.
 *
 * [https://geojson.org/geojson-spec.html#multipolygon](MultiPolygon)
 */
@Serializable
data class MultiPolygon(
    override val coordinates: List<List<List<Position>>> = emptyList(),
    override val bbox: BoundingBox? = null,
) : Geometry {

    constructor(polygons: List<Polygon>) : this(polygons.map { it.coordinates })

    override val crs: CoordinateReferenceSystem? = null

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type = GeometryType.MultiPolygon
}
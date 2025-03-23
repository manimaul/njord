@file:OptIn(ExperimentalJsExport::class)

package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * For type "MultiPolygon", the "coordinates" member must be an array of Polygon coordinate arrays.
 *
 * [https://geojson.org/geojson-spec.html#multipolygon](MultiPolygon)
 */
@Serializable
@JsExport
data class MultiPolygon(
    override val coordinates: List<List<List<Position>>> = emptyList(),
    override val bbox: BoundingBox? = null,
) : Geometry {

    @JsName("create")
    constructor(polygons: List<Polygon>) : this(polygons.map { it.coordinates })

    override val crs: CoordinateReferenceSystem? = null

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type = GeometryType.MultiPolygon
}
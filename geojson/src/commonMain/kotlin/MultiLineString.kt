@file:OptIn(ExperimentalJsExport::class)

package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * For type "MultiLineString", the "coordinates" member must be an array of LineString coordinate arrays.
 *
 * [https://geojson.org/geojson-spec.html#multilinestring](MultiLineString)
 */
@Serializable
@JsExport
data class MultiLineString(
    override val coordinates: List<List<Position>>,
    override val bbox: BoundingBox? = null,
) : Geometry {

    @JsName("create")
    constructor(vararg lineString: LineString) : this(lineString.toList().map { it.coordinates })

    override val crs: CoordinateReferenceSystem? = null

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type = GeometryType.MultiLineString
}
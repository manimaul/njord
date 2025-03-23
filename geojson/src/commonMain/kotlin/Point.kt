@file:OptIn(ExperimentalJsExport::class)
package io.madrona.njord.geojson

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlinx.serialization.*
import kotlin.js.ExperimentalJsExport

/**
 * For type "Point", the "coordinates" member must be a single position.
 *
 * [https://geojson.org/geojson-spec.html#point](Point)
 */
@Serializable
@JsExport
data class Point(
    override val coordinates: List<Double>,
    override val bbox: BoundingBox? = null,
    override val crs: CoordinateReferenceSystem? = null,
) : Geometry {

    @JsName("createWithPoint")
    constructor(other: Point) : this(other.coordinates)
    @JsName("createWithPosition")
    constructor(position: Position) : this(position.coordinates)
    @JsName("createWithLngLat")
    constructor(longitude: Double, latitude: Double) : this(listOf(longitude, latitude))
    @JsName("createWithLngLatZoomM")
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

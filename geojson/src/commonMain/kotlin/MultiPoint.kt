@file:OptIn(ExperimentalJsExport::class, ExperimentalJsExport::class)

package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName


/**
 * For type "MultiPoint", the "coordinates" member must be an array of positions.
 *
 * [https://geojson.org/geojson-spec.html#multipoint](MultiPoint)
 */
@Serializable
@JsExport
data class MultiPoint(
    override val coordinates: List<Position>,
    override val bbox: BoundingBox? = null,
) : Geometry {

    @JsName("createWithPositions")
    constructor(vararg positions: Position) : this(positions.toList())
    @JsName("createWithPoints")
    constructor(vararg points: Point) : this(points.toList().map { Position(it.coordinates) })

    override val crs: CoordinateReferenceSystem? = null

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type = GeometryType.MultiPoint
}

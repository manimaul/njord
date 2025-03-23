@file:OptIn(ExperimentalJsExport::class, ExperimentalJsExport::class)

package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A GeoJSON object with type "GeometryCollection" is a geometry object which represents a collection of geometry objects.
 * A geometry collection must have a member with the name "geometries". The value corresponding to "geometries" is an array.
 * Each element in this array is a GeoJSON geometry object.
 *
 * [https://geojson.org/geojson-spec.html#geometry-collection](GeometryCollection)
 */
@Serializable
@JsExport
data class GeometryCollection(
    @Transient
    override val coordinates: List<Geometry> = emptyList(),
    override val bbox: BoundingBox? = null,
) : Geometry {

    @JsName("create")
    constructor(vararg geometry: Geometry) : this(geometry.toList())

    override val crs: CoordinateReferenceSystem? = null

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val geometries = coordinates

    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val type: GeometryType = GeometryType.GeometryCollection

}

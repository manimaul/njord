@file:OptIn(ExperimentalJsExport::class, ExperimentalJsExport::class)

package io.madrona.njord.geojson

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json.Default.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A geometry is a GeoJSON object where the type member’s value is one of the following strings:
 * "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon", or "GeometryCollection".
 *
 * A GeoJSON geometry object of any type other than "GeometryCollection" must have a member with the name "coordinates".
 * The value of the coordinates member is always an array. The structure for the elements in this array is determined
 * by the type of geometry.
 */
@Serializable(with = GeometrySerializer::class)
@JsExport
interface Geometry : GeoJsonObject {
    val bbox: BoundingBox?
    val type: GeometryType
    val coordinates: List<Any>
    val crs: CoordinateReferenceSystem?
}


@Serializable
@JsExport
sealed interface GeoJsonObject

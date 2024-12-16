package io.madrona.njord.geojson

import kotlinx.serialization.Serializable

/**
 * "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon", or "GeometryCollection".
 *
 * [https://geojson.org/geojson-spec.html#geometry-objects]
 */
@Serializable
enum class GeometryType {
    Point,
    MultiPoint,
    LineString,
    MultiLineString,
    Polygon,
    MultiPolygon,
    GeometryCollection
}

package io.madrona.njord.geojson

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The coordinate reference system (CRS) of a GeoJSON object is determined by its "crs" member (referred to as the CRS object below).
 * If an object has no crs member, then its parent or grandparent object’s crs member may be acquired. If no crs member
 * can be so acquired, the default CRS shall apply to the GeoJSON object.
 *
 * The default CRS is a geographic coordinate reference system, using the WGS84 datum, and with longitude and latitude
 * units of decimal degrees. The value of a member named "crs" must be a JSON object (referred to as the CRS object below)
 * or JSON null. If the value of CRS is null, no CRS can be assumed. The crs member should be on the top-level GeoJSON
 * object in a hierarchy (in feature collection, feature, geometry order) and should not be repeated or overridden on
 * children or grandchildren of the object.
 *
 * A non-null CRS object has two mandatory members: "type" and "properties".
 *
 * The value of the type member must be a string, indicating the type of CRS object.
 *
 * The value of the properties member must be an object.
 *
 * CRS shall not change coordinate ordering [https://geojson.org/geojson-spec.html#positions](see 2.1.1. Positions).
 */
@Serializable
data class CoordinateReferenceSystem(
    val type: String,
    val properties: JsonObject
)

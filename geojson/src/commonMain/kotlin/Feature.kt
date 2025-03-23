@file:OptIn(ExperimentalJsExport::class, ExperimentalJsExport::class)

package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A GeoJSON object with the type "Feature" is a feature object. A feature object must have a member with the name "geometry".
 * The value of the geometry member is a geometry object as defined above or a JSON null value. A feature object must
 * have a member with the name "properties". The value of the properties member is an object (any JSON object or a JSON null value).
 *
 * If a feature has a commonly used identifier, that identifier should be included as a member of the feature object with the name "id".
 *
 * [https://geojson.org/geojson-spec.html#feature-objects](Feature)
 */
@Serializable
@JsExport
data class Feature(
    val id: String? = null,
    val geometry: Geometry?,
    val properties: JsonObject = JsonObject(emptyMap()),
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type: String = "Feature",
) : GeoJsonObject

fun JsonObject.objValue(key: String) = get(key)?.jsonObject
fun JsonObject.stringValue(key: String) = get(key)?.jsonPrimitive?.contentOrNull
fun JsonObject.intValue(key: String) = get(key)?.jsonPrimitive?.intOrNull
fun JsonObject.floatValue(key: String) = get(key)?.jsonPrimitive?.floatOrNull
fun JsonObject.doubleValue(key: String) = get(key)?.jsonPrimitive?.doubleOrNull

class FeatureBuilder(
    val geometryJson: String?
) {

    var geo: Geometry? = geometryJson?.let { Json.decodeFromString<Geometry>(geometryJson) }
    private val properties: MutableMap<String, JsonElement> = mutableMapOf()

    fun addProperty(key: String, value: String) {
        properties[key] = JsonPrimitive(value)
    }

    fun addProperty(key: String, value: Number) {
        properties[key] = JsonPrimitive(value)
    }

    fun addProperty(key: String, value: Boolean) {
        properties[key] = JsonPrimitive(value)
    }

    fun addAll(props: MutableMap<String, JsonElement>) {
        properties.putAll(props)
    }

    fun build(): Feature {
        return Feature(
            geometry = geo,
            properties = buildJsonObject {
                properties.forEach { (key, value) ->
                    put(key, value)
                }
            }
        )
    }


}
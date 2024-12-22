package io.madrona.njord.model

import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.Geometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Data class similar to a geojson [Feature] with additional properties
 */
@Serializable
data class MapGeoJsonFeature (
    val id: String? = null,
    val geometry: Geometry? = null,
    val properties: JsonObject = JsonObject(emptyMap()),
    val type: String = "Feature",

    /**
     * Data class similar to a [Layer] with more dynamic properties
     */
    val layer: JsonObject? = null,
    val source: String? = null,
    val sourceLayer: String? = null,
    val state: JsonObject? = null
)

package io.madrona.njord.model

import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.Geometry
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Data class similar to a geojson [Feature] with additional properties
 */
@Serializable
data class MapGeoJsonFeature (
    val id: String? = null,
    val geometry: Geometry?,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val properties: JsonObject = JsonObject(emptyMap()),
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type: String = "Feature",

    /**
     * Data class similar to a [Layer] with more dynamic properties
     */
    val layer: JsonObject?,
    val source: String?,
    val sourceLayer: String?,
    val state: JsonObject?
)

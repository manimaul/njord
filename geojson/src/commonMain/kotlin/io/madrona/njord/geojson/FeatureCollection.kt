package io.madrona.njord.geojson

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class FeatureCollection(
    val features: List<Feature>,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type: String = "FeatureCollection"
) : GeoJsonObject

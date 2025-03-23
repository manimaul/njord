package io.madrona.njord.model

import io.madrona.njord.geojson.FeatureCollection

data class LayerGeoJson(
    val layer: String,
    val featureCollection: FeatureCollection,
)

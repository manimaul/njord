package io.madrona.njord.model

import mil.nga.sf.geojson.FeatureCollection

data class LayerGeoJson(
    val layer: String,
    val featureCollection: FeatureCollection,
)

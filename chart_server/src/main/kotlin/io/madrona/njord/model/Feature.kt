package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import mil.nga.sf.geojson.GeoJsonObject

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FeatureInsert(
    @JsonProperty("layer_name") val layerName: String,
    val chart: Chart,
    val geo: GeoJsonObject,
)

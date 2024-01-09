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

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FeatureRecord(
    val id: Long,
    val layer: String,
    val props: Map<String, Any?>,
    val geom: GeoJsonObject,
    @JsonProperty("chart_id") val chartId: Long,
    val zoomMin: Int,
    val zoomMax: Int,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LayerQueryResult(
    val lat: Double,
    val lng: Double,
    val zoom: Float,
    val props: Map<String, Any?>,
    val chartName: String,
    val geomType: String,
)

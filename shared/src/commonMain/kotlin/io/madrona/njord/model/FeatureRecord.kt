package io.madrona.njord.model

import io.madrona.njord.geojson.GeoJsonObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FeatureInsert(
    @SerialName("layer_name") val layerName: String,
    val chart: Chart,
    val geo: GeoJsonObject,
)

@Serializable
data class FeatureRecord(
    val id: Long,
    val layer: String,
    val props: Map<String, JsonElement>,
    val geom: GeoJsonObject,
    @SerialName("chart_id") val chartId: Long,
    val zoomMin: Int,
    val zoomMax: Int,
)

@Serializable
data class LayerQueryResultPage(
    val lastId: Long,
    val items: List<LayerQueryResult>,
)

@Serializable
data class LayerQueryResult(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val zoom: Float,
    val props: Map<String, JsonElement>,
    val chartName: String,
    val geomType: String,
)

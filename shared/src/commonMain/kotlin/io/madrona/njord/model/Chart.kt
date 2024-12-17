package io.madrona.njord.model

import io.madrona.njord.geojson.Feature
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class ChartInfo(
    val id: Long,
    val scale: Int,
    val zoom: Int,
    val covrWKB: ByteArray
)

@Serializable
class ChartFeatureInfo(
    val layer: String,
    val props: Map<String, JsonElement>,
    val geom: String?,
)

@Serializable
class ChartFeature(
    val layer: String,
    val geomWKB: ByteArray?,
    val props: MutableMap<String, JsonElement>
)

@Serializable
data class Chart(
    val id: Long,
    val name: String,
    val scale: Int,
    @SerialName("file_name") val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val covr: Feature,
    val bounds: Bounds,
    val layers: List<String>,
    @SerialName("dsid_props") val dsidProps: Map<String, JsonElement>,
    @SerialName("chart_txt") val chartTxt: Map<String, String>,
    val featureCount: Int,
)

@Serializable
data class Bounds(
    val leftLng: Double,
    val topLat: Double,
    val rightLng: Double,
    val bottomLat: Double,
)

@Serializable
data class ChartInsert(
    val name: String,
    val scale: Int,
    @SerialName("file_name") val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val covr: Feature,
    @SerialName("dsid_props") val dsidProps: Map<String, JsonElement>,
    @SerialName("chart_txt") val chartTxt: Map<String, String>,
)

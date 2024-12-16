package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import mil.nga.sf.geojson.Feature

class ChartInfo(
    val id: Long,
    val scale: Int,
    val zoom: Int,
    val covrWKB: ByteArray
)

@JsonInclude(JsonInclude.Include.NON_NULL)
class ChartFeatureInfo(
    val layer: String,
    val props: MutableMap<String, Any?>,
    val geom: String?,
)

class ChartFeature(
    val layer: String,
    val geomWKB: ByteArray?,
    val props: MutableMap<String, Any?>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Chart(
    val id: Long,
    val name: String,
    val scale: Int,
    @JsonProperty("file_name") val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val covr: Feature,
    val bounds: Bounds,
    val layers: List<String>,
    @JsonProperty("dsid_props") val dsidProps: Map<String, Any>,
    @JsonProperty("chart_txt") val chartTxt: Map<String, String>,
    val featureCount: Int,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Bounds(
    val leftLng: Double,
    val topLat: Double,
    val rightLng: Double,
    val bottomLat: Double,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChartInsert(
    val name: String,
    val scale: Int,
    @JsonProperty("file_name") val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val covr: Feature,
    @JsonProperty("dsid_props") val dsidProps: Map<String, Any?>,
    @JsonProperty("chart_txt") val chartTxt: Map<String, String>,
)

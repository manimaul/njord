package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.GeoJsonObject
import mil.nga.sf.geojson.Geometry

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
    val layers: List<String>,
    @JsonProperty("dsid_props") val dsidProps: Map<String, Any>,
    @JsonProperty("chart_txt") val chartTxt: Map<String, String>,
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

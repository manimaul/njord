package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.json.simple.JSONObject

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Chart(
    val id: Long,
    val name: String,
    val scale: Int,
    @JsonProperty("file_name") val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    val layers: List<String>,
    @JsonProperty("dsid_props") val dsidProps: Map<String, Any>,
    @JsonProperty("chart_txt") val chartTxt: JSONObject,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChartInsert(
    val name: String,
    val scale: Int,
    @JsonProperty("file_name") val fileName: String,
    val updated: String,
    val issued: String,
    val zoom: Int,
    @JsonProperty("dsid_props") val dsidProps: Map<String, Any>,
    @JsonProperty("chart_txt") val chartTxt: Map<String, String>,
)

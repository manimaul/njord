package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

typealias S57ObjAcronyms =  Map<String, List<S57Symbol>>

data class S57Symbol(
    @JsonProperty("SY") val SY: String?,
    @JsonProperty("ATT") val ATT: List<S57Attribute>
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class S57Attribute(
    @JsonProperty("BOYSHP") val BOYSHP: List<Any>?,
    @JsonProperty("BCNSHP") val BCNSHP: List<Any>?,
    @JsonProperty("COLPAT") val COLPAT: List<Any>?,
    @JsonProperty("CATLAM") val CATLAM: List<Any>?,
    @JsonProperty("CATCAM") val CATCAM: List<Any>?,
    @JsonProperty("CATSPM") val CATSPM: List<Any>?,
    @JsonProperty("CONVIS") val CONVIS: List<Any>?,
    @JsonProperty("COLOUR") val COLORS: List<Any>?,
    @JsonProperty("FUNCTN") val FUNCTN: List<Any>?,
    @JsonProperty("OBJNAM") val OBJNAM: List<Any>?,
    @JsonProperty("CATCHP") val CATCHP: List<Any>?,
    @JsonProperty("ORIENT") val ORIENT: List<Any>?,
    @JsonProperty("CURVEL") val CURVEL: List<Any>?,
    @JsonProperty("CATDAM") val CATDAM: List<Any>?,
    @JsonProperty("CATDIS") val CATDIS: List<Any>?,
    @JsonProperty("TOPSHP") val TOPSHP: List<Any>?,
    @JsonProperty("HUNITS") val HUNITS: List<Any>?,
    @JsonProperty("WTWDIS") val WTWDIS: List<Any>?,
    @JsonProperty("CATFIF") val CATFIF: List<Any>?,
    @JsonProperty("CATGAT") val CATGAT: List<Any>?,
)

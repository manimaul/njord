package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonProperty

typealias S57ObjAcronyms =  Map<String, MutableList<S57Symbol>>

data class S57Symbol(
    @JsonProperty("SY") val symbol: String?,
    @JsonProperty("ATT") val attributes: List<Map<String, List<String>>>
)

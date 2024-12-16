package io.madrona.njord.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias S57ObjAcronyms =  Map<String, MutableList<S57Symbol>>

@Serializable
data class S57Symbol(
    @SerialName("SY") val symbol: String?,
    @SerialName("ATT") val attributes: List<Map<String, List<String>>>
)

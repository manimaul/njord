package io.madrona.njord.layers.attributehelpers

enum class DepthColor(val code: String) {
    DEEP_WATER("DEPDW"), // deep water
    MEDIUM_DEPTH("DEPMD"), // medium depth
    SAFETY_DEPTH("DEPMS"), // safety depth
    VERY_SHALLOW("DEPVS"), // very shallow
    COVERS_UNCOVERS("DEPIT"), // covers, uncovers
}

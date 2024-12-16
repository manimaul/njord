package io.madrona.njord.layers.attributehelpers

import io.madrona.njord.model.Color


enum class DepthColor(val color: Color) {
    DEEP_WATER(Color.DEPDW), // deep water
    MEDIUM_DEPTH(Color.DEPMD), // medium depth
    SAFETY_DEPTH(Color.DEPMS), // safety depth
    VERY_SHALLOW(Color.DEPVS), // very shallow
    COVERS_UNCOVERS(Color.DEPIT), // covers, uncovers
}

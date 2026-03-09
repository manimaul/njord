package io.madrona.njord.layers.base

import io.madrona.njord.layers.Canals

/**
 * Natural Earth base layer: rivers and lake centerlines
 */
class RiversLakeCenterlines : Canals() {
    override val sourceLayer: String = "rivers_lake_centerlines"

}
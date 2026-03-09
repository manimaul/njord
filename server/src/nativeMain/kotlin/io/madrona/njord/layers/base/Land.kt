package io.madrona.njord.layers.base

import io.madrona.njord.layers.Lndare

/**
 * Natural Earth base layer: land polygons
 */
class Land : Lndare() {
    override val sourceLayer: String = "land"
}
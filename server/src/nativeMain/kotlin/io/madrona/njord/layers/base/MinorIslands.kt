package io.madrona.njord.layers.base

import io.madrona.njord.layers.Lndare

/**
 * Natural Earth base layer: minor islands
 */
class MinorIslands : Lndare() {
    override val sourceLayer: String = "minor_islands"
}
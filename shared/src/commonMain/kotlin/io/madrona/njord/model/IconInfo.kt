package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class IconInfo(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val pixelRatio: Int,
    // MapLibre stretchable-sprite metadata (sprites used with icon-text-fit), in this sheet's pixels.
    val stretchX: List<List<Int>>? = null,
    val stretchY: List<List<Int>>? = null,
    val content: List<Int>? = null,
)
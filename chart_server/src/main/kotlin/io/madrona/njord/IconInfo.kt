package io.madrona.njord

import kotlinx.serialization.*

@Serializable
data class IconInfo(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val pixelRatio: Int,
)
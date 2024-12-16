package io.madrona.njord.model

import kotlinx.serialization.Serializable

@Serializable
data class IconInfo(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val pixelRatio: Int,
)